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
import play.api.mvc.Request
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressLookupParams
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.CachedData._
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.mongo.cache.{CacheIdType, CacheItem, DataKey, SessionCacheRepository}
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
){
  def safeId(sessionId: String)(implicit request: Request[_]) = {
    lazy val mayBeMigration: Option[SafeId] = registerWithEoriAndIdResponse
      .flatMap(_.responseDetail.flatMap(_.responseData.map(_.SAFEID)))
      .map(SafeId(_))
    lazy val mayBeRegistration: Option[SafeId] =
      regDetails.flatMap(s => if (s.safeId.id.nonEmpty) Some(s.safeId) else None)
    mayBeRegistration orElse mayBeMigration getOrElse (throw new IllegalStateException(
      s"$safeIdKey is not cached in data for the sessionId: $sessionId"
    ))

  }
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
    )(ec) {
  private val eccLogger: Logger = Logger(this.getClass)

  def sessionId(implicit request: Request[_]): String = request.session.get("sessionId").getOrElse("Session Id is not availabale")

  def putData[A: Writes](key: String, data: A)(implicit request: Request[_]): Future[A] =
    putSession[A](DataKey(key), data).map(_ => data)

  def saveRegistrationDetails(rd: RegistrationDetails)(implicit request: Request[_]): Future[Boolean] =
    putData(regDetailsKey, Json.toJson(rd)) map (_ => true)

  def saveRegistrationDetails(rd: RegistrationDetails, groupId: GroupId, orgType: Option[CdsOrganisationType] = None)(implicit hc: HeaderCarrier,request: Request[_]): Future[Boolean] =
    for {
      _                <- save4LaterService.saveOrgType(groupId, orgType)
      createdOrUpdated <- putData(regDetailsKey, Json.toJson(rd)) map (_ => true)
    } yield createdOrUpdated

  def saveRegistrationDetailsWithoutId(
    rd: RegistrationDetails,
    groupId: GroupId,
    orgType: Option[CdsOrganisationType] = None
  )(implicit hc: HeaderCarrier,request: Request[_]): Future[Boolean] =
    for {
      _                <- save4LaterService.saveSafeId(groupId, rd.safeId)
      _                <- save4LaterService.saveOrgType(groupId, orgType)
      createdOrUpdated <- putData(regDetailsKey, Json.toJson(rd)) map (_ => true)
    } yield createdOrUpdated

  def saveRegisterWithEoriAndIdResponse(
    rd: RegisterWithEoriAndIdResponse
  )(implicit request: Request[_]): Future[Boolean] =
    putData(registerWithEoriAndIdResponseKey, Json.toJson(rd)) map (_ => true)

  def saveSub02Outcome(subscribeOutcome: Sub02Outcome)(implicit request: Request[_]): Future[Boolean] =
    putData(sub02OutcomeKey, Json.toJson(subscribeOutcome)) map (_ => true)

  def saveSub01Outcome(sub01Outcome: Sub01Outcome)(implicit request: Request[_]): Future[Boolean] =
    putData(sub01OutcomeKey, Json.toJson(sub01Outcome)) map (_ => true)

  def saveSubscriptionDetails(rdh: SubscriptionDetails)(implicit request: Request[_]): Future[Boolean] =
    putData(subDetailsKey, Json.toJson(rdh)) map (_ => true)

  def saveEmail(email: String)(implicit request: Request[_]): Future[Boolean] =
    putData(emailKey, Json.toJson(email)) map (_ => true)

  def saveEori(eori: Eori)(implicit request: Request[_]): Future[Boolean] =
    putData(eoriKey, Json.toJson(eori.id)) map (_ => true)

  def keepAlive(implicit request: Request[_]): Future[Boolean] =
   putData(keepAliveKey, Json.toJson(LocalDateTime.now().toString)) map (_ => true)

  def saveGroupEnrolment(groupEnrolment: EnrolmentResponse)(implicit request: Request[_]): Future[Boolean] =
    putData(groupEnrolmentKey, Json.toJson(groupEnrolment)) map (_ => true)

  def saveAddressLookupParams(addressLookupParams: AddressLookupParams)(implicit request: Request[_]): Future[Unit] =
    putData(addressLookupParamsKey, Json.toJson(addressLookupParams)).map(_ => ())

  def subscriptionDetails(implicit request: Request[_]): Future[SubscriptionDetails] =
    getFromSession[SubscriptionDetails](DataKey(subDetailsKey)).map(_.getOrElse(SubscriptionDetails()))

  private def getCached[T](t: (CachedData) => T)(implicit request: Request[_]): Future[T] =
    cacheRepo.findById(request).map {
      case Some(CacheItem(_, data, _, _)) =>
        Json.fromJson[CachedData](data) match {
          case d: JsSuccess[CachedData] => t(d.value)
          case _ =>
            eccLogger.error(s"No Session data is cached for the sessionId : $sessionId")
            throw SessionTimeOutException(s"No Session data is cached for the sessionId : $sessionId")
        }
      case _ =>
        eccLogger.info(s"No match session id for signed in user with session: $sessionId")
        throw SessionTimeOutException(s"No match session id for signed in user with session : $sessionId")
    }

  def eori(implicit request: Request[_]): Future[Option[String]] =
    getFromSession[String](DataKey(eoriKey))



  def safeId(implicit request: Request[_]): Future[SafeId] =
    getCached[SafeId](cachedData=> cachedData.safeId(sessionId))

  def email(implicit request: Request[_]): Future[String] =
  getFromSession[String](DataKey(emailKey)).map(_.getOrElse(throwException(emailKey)))

  def registrationDetails(implicit request: Request[_]): Future[RegistrationDetails] =
    getFromSession[RegistrationDetails](DataKey(regDetailsKey)).map(_.getOrElse(throwException(regDetailsKey)))

  def registerWithEoriAndIdResponse(implicit request: Request[_]): Future[RegisterWithEoriAndIdResponse] =
    getFromSession[RegisterWithEoriAndIdResponse](DataKey(registerWithEoriAndIdResponseKey)).map(_.getOrElse(throw new IllegalStateException(s"$registerWithEoriAndIdResponseKey is not cached in data for the sessionId: $sessionId")))

  def sub01Outcome(implicit request: Request[_]): Future[Sub01Outcome] =
    getFromSession[Sub01Outcome](DataKey(sub01OutcomeKey)).map(_.getOrElse(throwException(sub01OutcomeKey)))

  def sub02Outcome(implicit request: Request[_]): Future[Sub02Outcome] =
    getFromSession[Sub02Outcome](DataKey(sub02OutcomeKey)).map(_.getOrElse(throwException(sub02OutcomeKey)))

  def groupEnrolment(implicit request: Request[_]): Future[EnrolmentResponse] =
    getFromSession[EnrolmentResponse](DataKey(groupEnrolmentKey)).map(_.getOrElse(throwException(groupEnrolmentKey)))

  def addressLookupParams(implicit request: Request[_]): Future[Option[AddressLookupParams]] =
    getFromSession[AddressLookupParams](DataKey(addressLookupParamsKey))

  def clearAddressLookupParams(implicit request: Request[_]): Future[Unit] =
    putData(addressLookupParamsKey, Json.toJson(AddressLookupParams("", None))).map(_ => ())

  def remove(implicit request: Request[_]): Future[Boolean] =
    cacheRepo.deleteEntity(request) map (_ => true)

  private def throwException(name: String)(implicit request: Request[_])=
    throw DataUnavailableException(s"$name is not cached in data for the sessionId: $sessionId")

}

case class SessionTimeOutException(errorMessage: String) extends NoStackTrace
case class DataUnavailableException(message: String)     extends RuntimeException(message)
