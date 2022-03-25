/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.Logger
import play.api.libs.json.{JsError, JsResult, JsString, JsSuccess, JsValue, Json, OFormat, Writes}
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressLookupParams
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.CachedData._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.{CacheIdType, CacheItem, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}

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
  addressLookupParams: Option[AddressLookupParams] = None
) {

  def registrationDetails(sessionId: String): RegistrationDetails =
    regDetails.getOrElse(throwException(regDetailsKey, sessionId))

  def registerWithEoriAndIdResponse(sessionId: String): RegisterWithEoriAndIdResponse =
    registerWithEoriAndIdResponse.getOrElse(
      throw new IllegalStateException(
        s"$registerWithEoriAndIdResponseKey is not cached in data for the sessionId: ${sessionId}"
      )
    )

  def sub01Outcome(sessionId: String): Sub01Outcome =
    sub01Outcome.getOrElse(throwException(sub01OutcomeKey, sessionId))

  def sub02Outcome(sessionId: String): Sub02Outcome =
    sub02Outcome.getOrElse(throwException(sub02OutcomeKey, sessionId))

  def groupEnrolment(sessionId: String): EnrolmentResponse =
    groupEnrolment.getOrElse(throwException(groupEnrolmentKey, sessionId))

  // TODO Refactor this method, sessionId is not used
  def subscriptionDetails(sessionId: String): SubscriptionDetails =
    subDetails.getOrElse(initialEmptySubscriptionDetails)

  def email(sessionId: String): String =
    email.getOrElse(throwException(emailKey, sessionId))

  def safeId(sessionId: String) = {
    lazy val mayBeMigration: Option[SafeId] = registerWithEoriAndIdResponse
      .flatMap(_.responseDetail.flatMap(_.responseData.map(_.SAFEID)))
      .map(SafeId(_))
    lazy val mayBeRegistration: Option[SafeId] =
      regDetails.flatMap(s => if (s.safeId.id.nonEmpty) Some(s.safeId) else None)
    mayBeRegistration orElse mayBeMigration getOrElse (throw new IllegalStateException(
      s"$safeIdKey is not cached in data for the sessionId: ${sessionId}"
    ))

  }

  private def throwException(name: String, sessionId: String) =
    throw DataUnavailableException(s"$name is not cached in data for the sessionId: ${sessionId}")

  private val initialEmptySubscriptionDetails = SubscriptionDetails()
}

object CachedData {
  val regDetailsKey                    = "regDetails"
  val regInfoKey                       = "regInfo"
  val subDetailsKey                    = "subDetails"
  val sub01OutcomeKey                  = "sub01Outcome"
  val sub02OutcomeKey                  = "sub02Outcome"
  val registerWithEoriAndIdResponseKey = "registerWithEoriAndIdResponse"
  val emailKey                         = "email"
  val keepAliveKey                     = "keepAlive"
  val safeIdKey                        = "safeId"
  val groupIdKey                       = "cachedGroupId"
  val groupEnrolmentKey                = "groupEnrolment"
  val eoriKey                          = "eori"
  val addressLookupParamsKey           = "addressLookupParams"
  implicit val format                  = Json.format[CachedData]
}

case class Id(id: String) extends AnyVal
object Id{
  implicit val jsonFormat: OFormat[Id] = Json.format[Id]
}

object IdType extends CacheIdType[Id] {
  def run: Id => String = _.id
}

@Singleton
class SessionCache @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  save4LaterService: Save4LaterService,
  timestampSupport: TimestampSupport
)(implicit ec: ExecutionContext)
    extends MongoCacheRepository(
      mongoComponent = mongoComponent,
      collectionName = "session-cache",
      ttl = appConfig.ttl,
      timestampSupport = timestampSupport,
      cacheIdType = IdType
    )(ec) {
  private val eccLogger: Logger = Logger(this.getClass)

  private def sessionId(implicit hc: HeaderCarrier): Id =
    hc.sessionId match {
      case None =>
        throw new IllegalStateException("Session id is not available")
      case Some(sessionId) => Id(sessionId.value)
    }

  def putData[A: Writes](id: Id, key: String, data: A): Future[A] =
    put[A](id)(DataKey(key), data).map(_ => data)

  def saveRegistrationDetails(rd: RegistrationDetails)(implicit hc: HeaderCarrier): Future[Boolean] =
    putData(sessionId, regDetailsKey, Json.toJson(rd)) map (_ => true)

  def saveRegistrationDetails(rd: RegistrationDetails, groupId: GroupId, orgType: Option[CdsOrganisationType] = None)(
    implicit hc: HeaderCarrier
  ): Future[Boolean] =
    for {
      _                <- save4LaterService.saveOrgType(groupId, orgType)
      createdOrUpdated <- putData(sessionId, regDetailsKey, Json.toJson(rd)) map (_ => true)
    } yield createdOrUpdated

  def saveRegistrationDetailsWithoutId(
    rd: RegistrationDetails,
    groupId: GroupId,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier): Future[Boolean] =
    for {
      _                <- save4LaterService.saveSafeId(groupId, rd.safeId)
      _                <- save4LaterService.saveOrgType(groupId, orgType)
      createdOrUpdated <- putData(sessionId, regDetailsKey, Json.toJson(rd)) map (_ => true)
    } yield createdOrUpdated

  def saveRegisterWithEoriAndIdResponse(
    rd: RegisterWithEoriAndIdResponse
  )(implicit hc: HeaderCarrier): Future[Boolean] =
    putData(sessionId, registerWithEoriAndIdResponseKey, Json.toJson(rd)) map (_ => true)

  def saveSub02Outcome(subscribeOutcome: Sub02Outcome)(implicit hc: HeaderCarrier): Future[Boolean] =
    putData(sessionId, sub02OutcomeKey, Json.toJson(subscribeOutcome)) map (_ => true)

  def saveSub01Outcome(sub01Outcome: Sub01Outcome)(implicit hc: HeaderCarrier): Future[Boolean] =
    putData(sessionId, sub01OutcomeKey, Json.toJson(sub01Outcome)) map (_ => true)

  def saveSubscriptionDetails(rdh: SubscriptionDetails)(implicit hc: HeaderCarrier): Future[Boolean] =
    putData(sessionId, subDetailsKey, Json.toJson(rdh)) map (_ => true)

  def saveEmail(email: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    putData(sessionId, emailKey, Json.toJson(email)) map (_ => true)

  def saveEori(eori: Eori)(implicit hc: HeaderCarrier): Future[Boolean] =
    putData(sessionId, eoriKey, Json.toJson(eori.id)) map (_ => true)

  def keepAlive(implicit hc: HeaderCarrier): Future[Boolean] =
    hc.sessionId.map(
      id => putData(Id(id.value), keepAliveKey, Json.toJson(LocalDateTime.now().toString)) map (_ => true)
    ).getOrElse(Future.successful(true))

  def saveGroupEnrolment(groupEnrolment: EnrolmentResponse)(implicit hc: HeaderCarrier): Future[Boolean] =
    putData(sessionId, groupEnrolmentKey, Json.toJson(groupEnrolment)) map (_ => true)

  def saveAddressLookupParams(addressLookupParams: AddressLookupParams)(implicit hc: HeaderCarrier): Future[Unit] =
    putData(sessionId, addressLookupParamsKey, Json.toJson(addressLookupParams)).map(_ => ())

  private def getCached[T](sessionId: Id, t: (CachedData, String) => T): Future[T] =
    findById(sessionId).map {
      case Some(CacheItem(_, data, _, _)) =>
        Json.fromJson[CachedData](data) match {
          case d: JsSuccess[CachedData] => t(d.value, sessionId.id)
          case _ =>
            eccLogger.error(s"No Session data is cached for the sessionId : ${sessionId}")
            throw SessionTimeOutException(s"No Session data is cached for the sessionId : ${sessionId}")
        }
      case _ =>
        eccLogger.info(s"No match session id for signed in user with session: ${sessionId}")
        throw SessionTimeOutException(s"No match session id for signed in user with session : ${sessionId}")
    }

  def subscriptionDetails(implicit hc: HeaderCarrier): Future[SubscriptionDetails] =
    getCached[SubscriptionDetails](sessionId, (cachedData, id) => cachedData.subscriptionDetails(id))

  def email(implicit hc: HeaderCarrier): Future[String] =
    getCached[String](sessionId, (cachedData, id) => cachedData.email(id))

  def eori(implicit hc: HeaderCarrier): Future[Option[String]] =
    getCached[Option[String]](sessionId, (cachedData, _) => cachedData.eori)

  def safeId(implicit hc: HeaderCarrier): Future[SafeId] =
    getCached[SafeId](sessionId, (cachedData, id) => cachedData.safeId(id))

  def registrationDetails(implicit hc: HeaderCarrier): Future[RegistrationDetails] =
    getCached[RegistrationDetails](sessionId, (cachedData, id) => cachedData.registrationDetails(id))

  def registerWithEoriAndIdResponse(implicit hc: HeaderCarrier): Future[RegisterWithEoriAndIdResponse] =
    getCached[RegisterWithEoriAndIdResponse](
      sessionId,
      (cachedData, id) => cachedData.registerWithEoriAndIdResponse(id)
    )

  def sub01Outcome(implicit hc: HeaderCarrier): Future[Sub01Outcome] =
    getCached[Sub01Outcome](sessionId, (cachedData, id) => cachedData.sub01Outcome(id))

  def sub02Outcome(implicit hc: HeaderCarrier): Future[Sub02Outcome] =
    getCached[Sub02Outcome](sessionId, (cachedData, id) => cachedData.sub02Outcome(id))

  def groupEnrolment(implicit hc: HeaderCarrier): Future[EnrolmentResponse] =
    getCached[EnrolmentResponse](sessionId, (cachedData, id) => cachedData.groupEnrolment(id))

  def addressLookupParams(implicit hc: HeaderCarrier): Future[Option[AddressLookupParams]] =
    getCached[Option[AddressLookupParams]](sessionId, (cachedData, _) => cachedData.addressLookupParams)

  def clearAddressLookupParams(implicit hc: HeaderCarrier): Future[Unit] =
    putData(sessionId, addressLookupParamsKey, Json.toJson(AddressLookupParams("", None))).map(_ => ())

  def remove(implicit hc: HeaderCarrier): Future[Boolean] =
    deleteEntity(sessionId) map (_ => true)

}

case class SessionTimeOutException(errorMessage: String) extends NoStackTrace
case class DataUnavailableException(message: String)     extends RuntimeException(message)
