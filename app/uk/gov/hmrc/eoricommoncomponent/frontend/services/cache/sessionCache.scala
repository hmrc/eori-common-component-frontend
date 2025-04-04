/*
 * Copyright 2025 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.eoricommoncomponent.frontend.services.cache

import play.api.Logging
import play.api.libs.json.{Json, OFormat, Reads, Writes}
import play.api.mvc.Request
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{SubmissionCompleteData, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressLookupParams
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.CachedData._
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.mongo.cache.{DataKey, SessionCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

sealed case class CachedData(
  regDetails: Option[RegistrationDetails] = None,
  subDetails: Option[SubscriptionDetails] = None,
  sub02Outcome: Option[Sub02Outcome] = None,
  sub01Outcome: Option[Sub01Outcome] = None,
  registerWithEoriAndIdResponse: Option[RegisterWithEoriAndIdResponse] = None,
  email: Option[String] = None,
  groupEnrolment: Option[EnrolmentResponse] = None,
  keepAlive: Option[String] = None,
  eori: Option[String] = None,
  addressLookupParams: Option[AddressLookupParams] = None,
  submissionCompleteDetails: Option[SubmissionCompleteData] = None,
  completed: Option[Boolean] = None,
  userLocation: Option[UserLocationDetails] = None
)

object CachedData {
  val regDetailsKey                        = "regDetails"
  val regInfoKey                           = "regInfo"
  val subDetailsKey                        = "subDetails"
  val submissionCompleteKey                = "submissionCompleteDetails"
  val sub01OutcomeKey                      = "sub01Outcome"
  val sub02OutcomeKey                      = "sub02Outcome"
  val registerWithEoriAndIdResponseKey     = "registerWithEoriAndIdResponse"
  val emailKey                             = "email"
  val userLocationKey                      = "userLocation"
  val keepAliveKey                         = "keepAlive"
  val safeIdKey                            = "safeId"
  val groupIdKey                           = "cachedGroupId"
  val groupEnrolmentKey                    = "groupEnrolment"
  val eoriKey                              = "eori"
  val addressLookupParamsKey               = "addressLookupParams"
  val completed                            = "completed"
  implicit val format: OFormat[CachedData] = Json.format[CachedData]
}

@Singleton
class SessionCache @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  save4LaterService: Save4LaterService,
  timestampSupport: TimestampSupport
)(implicit ec: ExecutionContext)
    extends SessionCacheRepository(
      mongoComponent = mongoComponent,
      collectionName = "session-cache",
      ttl = appConfig.ttl,
      timestampSupport = timestampSupport,
      sessionIdKey = SessionKeys.sessionId
    )(ec) with Logging {

  def sessionId(implicit request: Request[_]): String =
    request.session.get("sessionId").getOrElse("Session Id is not available")

  def putData[A: Writes](key: String, data: A)(implicit request: Request[_]): Future[A] =
    preservingMdc {
      putSession[A](DataKey(key), data).map(_ => data)
    }

  def getData[A: Reads](key: String)(implicit request: Request[_]): Future[Option[A]] =
    preservingMdc {
      getFromSession[A](DataKey(key))
    }

  def journeyCompleted(implicit request: Request[_]): Future[Boolean] = putData(completed, true)

  def isJourneyComplete(implicit request: Request[_]): Future[Boolean] =
    getData[Boolean](completed).map(_.contains(true))

  def saveRegistrationDetails(rd: RegistrationDetails)(implicit request: Request[_]): Future[Boolean] =
    putData(regDetailsKey, Json.toJson(rd)) map (_ => true)

  def saveRegistrationDetails(
    rd: RegistrationDetails,
    groupId: GroupId,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier, request: Request[_]): Future[Boolean] =
    for {
      _                <- save4LaterService.saveOrgType(groupId, orgType)
      createdOrUpdated <- putData(regDetailsKey, Json.toJson(rd)) map (_ => true)
    } yield createdOrUpdated

  def saveRegistrationDetailsWithoutId(
    rd: RegistrationDetails,
    groupId: GroupId,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier, request: Request[_]): Future[Boolean] =
    for {
      _                <- save4LaterService.saveSafeId(groupId, rd.safeId)
      _                <- save4LaterService.saveOrgType(groupId, orgType)
      createdOrUpdated <- putData(regDetailsKey, Json.toJson(rd)) map (_ => true)
    } yield createdOrUpdated

  def saveRegisterWithEoriAndIdResponse(
    rd: RegisterWithEoriAndIdResponse
  )(implicit request: Request[_]): Future[Boolean] = for {
    subCompleteDetails <- submissionCompleteDetails
    _                  <- putData(registerWithEoriAndIdResponseKey, Json.toJson(rd)) map (_ => true)
    _                  <- saveSubmissionCompleteDetails(subCompleteDetails.copy(processingDate = Some(rd.responseCommon.processingDate)))
  } yield true

  def saveSub02Outcome(subscribeOutcome: Sub02Outcome)(implicit request: Request[_]): Future[Boolean] =
    putData(sub02OutcomeKey, Json.toJson(subscribeOutcome)) map (_ => true)

  def saveSub01Outcome(sub01Outcome: Sub01Outcome)(implicit request: Request[_]): Future[Boolean] =
    putData(sub01OutcomeKey, Json.toJson(sub01Outcome)) map (_ => true)

  def saveSubscriptionDetails(rdh: SubscriptionDetails)(implicit request: Request[_]): Future[Boolean] = for {
    subCompleteDetails <- submissionCompleteDetails
    _                  <- putData(subDetailsKey, Json.toJson(rdh)) map (_ => true)
    _                  <- saveSubmissionCompleteDetails(subCompleteDetails.copy(subscriptionDetails = Some(rdh)))
  } yield true

  def saveEmail(email: String)(implicit request: Request[_]): Future[Boolean] =
    putData(emailKey, Json.toJson(email)) map (_ => true)

  def saveEori(eori: Eori)(implicit request: Request[_]): Future[Boolean] =
    putData(eoriKey, Json.toJson(eori.id)) map (_ => true)

  def keepAlive(implicit request: Request[_]): Future[Boolean] =
    putData(keepAliveKey, Json.toJson(LocalDateTime.now().toString)) map (_ => true)

  def saveGroupEnrolment(groupEnrolment: EnrolmentResponse)(implicit request: Request[_]): Future[Boolean] =
    putData(groupEnrolmentKey, Json.toJson(groupEnrolment)) map (_ => true)

  def saveSubmissionCompleteDetails(
    submissionCompleteData: SubmissionCompleteData
  )(implicit request: Request[_]): Future[Boolean] =
    putData(submissionCompleteKey, Json.toJson(submissionCompleteData)) map (_ => true)

  def saveAddressLookupParams(addressLookupParams: AddressLookupParams)(implicit request: Request[_]): Future[Unit] =
    putData(addressLookupParamsKey, Json.toJson(addressLookupParams)).map(_ => ())

  def subscriptionDetails(implicit request: Request[_]): Future[SubscriptionDetails] =
    getData[SubscriptionDetails](subDetailsKey).map(_.getOrElse(SubscriptionDetails()))

  def submissionCompleteDetails(implicit request: Request[_]): Future[SubmissionCompleteData] =
    getData[SubmissionCompleteData](submissionCompleteKey).map(_.getOrElse(SubmissionCompleteData()))

  def eori(implicit request: Request[_]): Future[Option[String]] =
    getData[String](eoriKey)

  def email(implicit request: Request[_]): Future[String] =
    getData[String](emailKey).map(_.getOrElse {
      val error = s"Email with key: $emailKey not found for the sessionId: $sessionId"
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throwException(emailKey)
    })

  def userLocation(implicit request: Request[_]): Future[UserLocationDetails] =
    getData[String](userLocationKey).map(location => UserLocationDetails(location))

  def emailOpt(implicit request: Request[_]): Future[Option[String]] =
    getData[String](emailKey)

  def registrationDetails(implicit request: Request[_]): Future[RegistrationDetails] =
    getData[RegistrationDetails](regDetailsKey).map(_.getOrElse {
      val error = s"Registration details with key: $regDetailsKey not found for the sessionId: $sessionId"
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throwException(regDetailsKey)
    })

  def registerWithEoriAndIdResponse(implicit request: Request[_]): Future[RegisterWithEoriAndIdResponse] =
    getData[RegisterWithEoriAndIdResponse](registerWithEoriAndIdResponseKey).map(_.getOrElse {
      val error =
        s"Register with Eori and ID response details with key: $registerWithEoriAndIdResponseKey not found for the sessionId: $sessionId"
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throwException(registerWithEoriAndIdResponseKey)
    })

  def safeId(implicit request: Request[_]): Future[SafeId] = fetchSafeIdFromRegDetails.flatMap {
    case Some(value) => Future.successful(value)
    case None =>
      fetchSafeIdFromReg06Response.map(_.getOrElse {
        val error = s"$safeIdKey is not cached in data for the sessionId: $sessionId"
        // $COVERAGE-OFF$Loggers
        logger.warn(error)
        // $COVERAGE-ON
        throw new IllegalStateException(error)
      })
  }

  def fetchSafeIdFromReg06Response(implicit request: Request[_]): Future[Option[SafeId]] =
    registerWithEoriAndIdResponse.map(
      response =>
        response.responseDetail.flatMap(_.responseData.map(_.SAFEID))
          .map(SafeId(_))
    ).recoverWith {
      case _ => Future.successful(None)
    }

  def fetchSafeIdFromRegDetails(implicit request: Request[_]): Future[Option[SafeId]] =
    registrationDetails.map(response => if (response.safeId.id.nonEmpty) Some(response.safeId) else None)
      .recoverWith {
        case _ => Future.successful(None)
      }

  def sub01Outcome(implicit request: Request[_]): Future[Sub01Outcome] =
    getData[Sub01Outcome](sub01OutcomeKey).map(_.getOrElse {
      val error = s"$sub01OutcomeKey is not cached in data for the sessionId: $sessionId"
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throwException(sub01OutcomeKey)
    })

  def sub02Outcome(implicit request: Request[_]): Future[Sub02Outcome] =
    getData[Sub02Outcome](sub02OutcomeKey).map(_.getOrElse {
      val error = s"$sub02OutcomeKey is not cached in data for the sessionId: $sessionId"
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throwException(sub02OutcomeKey)
    })

  def groupEnrolment(implicit request: Request[_]): Future[EnrolmentResponse] =
    getData[EnrolmentResponse](groupEnrolmentKey).map(_.getOrElse {
      val error = s"$groupEnrolmentKey is not cached in data for the sessionId: $sessionId"
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throwException(groupEnrolmentKey)
    })

  def addressLookupParams(implicit request: Request[_]): Future[Option[AddressLookupParams]] =
    getData[AddressLookupParams](addressLookupParamsKey)

  def clearAddressLookupParams(implicit request: Request[_]): Future[Unit] =
    putData(addressLookupParamsKey, Json.toJson(AddressLookupParams("", None))).map(_ => ())

  def remove(implicit request: Request[_]): Future[Boolean] =
    preservingMdc {
      cacheRepo.deleteEntity(request).map(_ => true).recoverWith {
        case _ => Future.successful(false)
      }
    }

  private def throwException(name: String)(implicit request: Request[_]): Nothing =
    throw DataUnavailableException(s"$name is not cached in data for the sessionId: $sessionId")

}

case class SessionTimeOutException(errorMessage: String) extends NoStackTrace
case class DataUnavailableException(message: String)     extends RuntimeException(message)
