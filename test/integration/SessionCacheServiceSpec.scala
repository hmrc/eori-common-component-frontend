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

package integration

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.ResponseCommon
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressLookupParams
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.CachedData.regDetailsKey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{
  CachedData,
  DataUnavailableException,
  SessionCache,
  SessionTimeOutException
}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, SessionId}
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.mongo.test.MongoSupport
import util.builders.RegistrationDetailsBuilder._

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionCacheSpec extends IntegrationTestsSpec with MockitoSugar with MongoSupport {

  lazy val appConfig: AppConfig                     = app.injector.instanceOf[AppConfig]
  lazy val mockHttpClient: HttpClient               = mock[HttpClient]
  lazy val mockSave4LaterService: Save4LaterService = mock[Save4LaterService]
  val mockTimeStampSupport                          = new CurrentTimestampSupport()

  private val save4LaterService = mock[Save4LaterService]
  when(save4LaterService.saveOrgType(any(), any())(any())).thenReturn(Future.successful(()))
  when(save4LaterService.saveSafeId(any(), any())(any())).thenReturn(Future.successful(()))
  val sessionCache = new SessionCache(mongoComponent, appConfig, save4LaterService, mockTimeStampSupport)

  val hc: HeaderCarrier = mock[HeaderCarrier]

  "Session cache" should {

    "provide default when subscription details holder not in cache" in {
      when(hc.sessionId).thenReturn(Some(SessionId("does-not-exist")))

      val e1 = intercept[SessionTimeOutException] {
        await(sessionCache.subscriptionDetails(hc))
      }
      e1.errorMessage mustBe "No match session id for signed in user with session : does-not-exist"

      val s1 = setupSession

      val e2 = intercept[SessionTimeOutException] {
        await(sessionCache.subscriptionDetails(hc))
      }
      e2.errorMessage mustBe s"No match session id for signed in user with session : ${s1.value}"

      val s2 = setupSession

      await(sessionCache.putData(s2.value, "regDetails", data = Json.toJson(individualRegistrationDetails)))

      await(sessionCache.subscriptionDetails(hc)) mustBe SubscriptionDetails()
    }

    "store, fetch and update Registration details correctly" in {
      val sessionId: SessionId = setupSession

      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson                   = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      val Some(CacheItem(_, json, _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registrationDetails(hc)) mustBe organisationRegistrationDetails
      await(sessionCache.saveRegistrationDetails(individualRegistrationDetails)(hc))

      val updatedCache = await(sessionCache.findById(sessionId.value))

      val expectedUpdatedJson                   = toJson(CachedData(regDetails = Some(individualRegistrationDetails)))
      val Some(CacheItem(_, updatedJson, _, _)) = updatedCache
      updatedJson mustBe expectedUpdatedJson
    }

    "store, fetch and update Registration details when group ID and orgType are privided correctly" in {
      val sessionId: SessionId = setupSession
      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails, GroupId("groupId"))(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson                   = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      val Some(CacheItem(_, json, _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registrationDetails(hc)) mustBe organisationRegistrationDetails
      await(sessionCache.saveRegistrationDetails(individualRegistrationDetails)(hc))

      val updatedCache = await(sessionCache.findById(sessionId.value))

      val expectedUpdatedJson                   = toJson(CachedData(regDetails = Some(individualRegistrationDetails)))
      val Some(CacheItem(_, updatedJson, _, _)) = updatedCache
      updatedJson mustBe expectedUpdatedJson
    }

    "calling saveRegistrationDetailsWithoutId should store, fetch and update Registration details when group ID and orgType are privided correctly" in {
      val sessionId: SessionId = setupSession
      await(sessionCache.saveRegistrationDetailsWithoutId(organisationRegistrationDetails, GroupId("groupId"))(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson                   = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      val Some(CacheItem(_, json, _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registrationDetails(hc)) mustBe organisationRegistrationDetails
      await(sessionCache.saveRegistrationDetails(individualRegistrationDetails)(hc))

      val updatedCache = await(sessionCache.findById(sessionId.value))

      val expectedUpdatedJson                   = toJson(CachedData(regDetails = Some(individualRegistrationDetails)))
      val Some(CacheItem(_, updatedJson, _, _)) = updatedCache
      updatedJson mustBe expectedUpdatedJson
    }

    "store and fetch RegisterWith EORI And Id Response correctly for Reg06 response" in {
      val sessionId: SessionId = setupSession

      val processingDate = LocalDateTime.now().withNano(0)
      val responseCommon = ResponseCommon(status = "OK", processingDate = processingDate)
      val trader         = Trader(fullName = "New trading", shortName = "nt")
      val establishmentAddress =
        EstablishmentAddress(streetAndNumber = "new street", city = "leeds", countryCode = "GB")
      val responseData: ResponseData = ResponseData(
        SAFEID = "SomeSafeId",
        trader = trader,
        establishmentAddress = establishmentAddress,
        hasInternetPublication = true,
        startDate = "2018-01-01"
      )
      val registerWithEoriAndIdResponseDetail = RegisterWithEoriAndIdResponseDetail(
        outcome = Some("PASS"),
        caseNumber = Some("case no 1"),
        responseData = Some(responseData)
      )
      val rd = RegisterWithEoriAndIdResponse(
        responseCommon = responseCommon,
        responseDetail = Some(registerWithEoriAndIdResponseDetail)
      )

      await(sessionCache.saveRegisterWithEoriAndIdResponse(rd)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson                   = toJson(CachedData(registerWithEoriAndIdResponse = Some(rd)))
      val Some(CacheItem(_, json, _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registerWithEoriAndIdResponse(hc)) mustBe rd

    }

    "throw exception when registration Details requested and not available in cache" in {
      val s = setupSession
      await(sessionCache.putData(s.value, "sub01Outcome", data = Json.toJson(sub01Outcome)))

      val caught = intercept[DataUnavailableException] {
        await(sessionCache.registrationDetails(hc))
      }
      caught.getMessage mustBe s"regDetails is not cached in data for the sessionId: ${s.value}"
    }

    "store Address Lookup Params correctly" in {

      val sessionId: SessionId = setupSession

      val addressLookupParams = AddressLookupParams("AA11 1AA", None)

      await(sessionCache.saveAddressLookupParams(addressLookupParams)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson = toJson(CachedData(addressLookupParams = Some(addressLookupParams)))

      val Some(CacheItem(_, json, _, _)) = cache

      json mustBe expectedJson
    }
    "store and fetch group enrolment correctly" in {

      val sessionId: SessionId = setupSession

      val groupEnrolmentResponse = EnrolmentResponse(Service.cds.enrolmentKey, "Activated", List.empty)

      await(sessionCache.saveGroupEnrolment(groupEnrolmentResponse)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson = toJson(CachedData(groupEnrolment = Some(groupEnrolmentResponse)))

      val Some(CacheItem(_, json, _, _)) = cache

      json mustBe expectedJson

      await(sessionCache.groupEnrolment(hc)) mustBe groupEnrolmentResponse
    }

    "throw DataUnavailableException when groupEnrolment is not present in cache" in {
      val sessionId: SessionId = setupSession
      await(sessionCache.putData(sessionId.value, "sub01Outcome", data = Json.toJson(sub01Outcome)))
      intercept[DataUnavailableException] {
        await(sessionCache.groupEnrolment(hc))
      }
    }
    "fetch safeId correctly" in {
      val sessionId: SessionId = setupSession
      val registrationDetails: RegistrationDetails = RegistrationDetails.individual(
        sapNumber = "0123456789",
        safeId = SafeId("safe-id"),
        name = "John Doe",
        address = defaultAddress,
        dateOfBirth = LocalDate.parse("1980-07-23"),
        customsId = Some(Utr("123UTRNO"))
      )
      await(sessionCache.putData(sessionId.value, "regDetails", data = Json.toJson(registrationDetails)))

      await(sessionCache.safeId(hc)) mustBe SafeId("safe-id")
    }

    "store and fetch Eori correctly" in {

      val sessionId: SessionId = setupSession

      val eori = Eori("GB123456789123")

      await(sessionCache.saveEori(eori)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson = toJson(CachedData(eori = Some(eori.id)))

      val Some(CacheItem(_, json, _, _)) = cache

      json mustBe expectedJson

      await(sessionCache.eori(hc)) mustBe Some(eori.id)
    }

    "store and fetch email correctly" in {

      val sessionId: SessionId = setupSession

      val email = "email@email.com"

      await(sessionCache.saveEmail(email)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson = toJson(CachedData(email = Some(email)))

      val Some(CacheItem(_, json, _, _)) = cache

      json mustBe expectedJson
      await(sessionCache.email(hc)) mustBe email

    }

    "store subscription details correctly" in {

      val sessionId: SessionId = setupSession

      val subscriptionDetails = SubscriptionDetails(email = Some("email@email.com"))

      await(sessionCache.saveSubscriptionDetails(subscriptionDetails)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson = toJson(CachedData(subDetails = Some(subscriptionDetails)))

      val Some(CacheItem(_, json, _, _)) = cache

      json mustBe expectedJson
    }

    "store and fetch sub01Outcome details correctly" in {

      val sessionId: SessionId = setupSession

      val sub01Outcome = Sub01Outcome(LocalDate.of(1961, 4, 12).toString)

      await(sessionCache.saveSub01Outcome(sub01Outcome)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson = toJson(CachedData(sub01Outcome = Some(sub01Outcome)))

      val Some(CacheItem(_, json, _, _)) = cache

      json mustBe expectedJson
      await(sessionCache.sub01Outcome(hc)) mustBe sub01Outcome
    }

    "store and fetch sub02Outcome details correctly" in {

      val sessionId: SessionId = setupSession

      val sub02Outcome = Sub02Outcome(LocalDate.of(1961, 4, 12).toString, "fullName", Some("GB123456789123"))

      await(sessionCache.saveSub02Outcome(sub02Outcome)(hc))

      val cache = await(sessionCache.findById(sessionId.value))

      val expectedJson = toJson(CachedData(sub02Outcome = Some(sub02Outcome)))

      val Some(CacheItem(_, json, _, _)) = cache

      json mustBe expectedJson
      await(sessionCache.sub02Outcome(hc)) mustBe sub02Outcome
    }

    "store keepAlive details correctly" in {
      await(sessionCache.keepAlive(hc)) mustBe true
    }
    "clear Address Lookup Params" in {

      val sessionId: SessionId = setupSession

      val addressLookupParams = AddressLookupParams("AA11 1AA", None)

      await(sessionCache.saveAddressLookupParams(addressLookupParams)(hc))

      await(sessionCache.clearAddressLookupParams(hc))

      val Some(CacheItem(_, jsonAfter, _, _)) = await(sessionCache.findById(sessionId.value))

      val expectedJsonAfter = toJson(CachedData(addressLookupParams = Some(AddressLookupParams("", None))))

      jsonAfter mustBe expectedJsonAfter
      await(sessionCache.addressLookupParams(hc)) mustBe Some(AddressLookupParams("", None))
    }

    "remove from the cache" in {
      val sessionId: SessionId = setupSession
      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(hc))

      await(sessionCache.remove(hc))

      val cached = await(sessionCache.findById(sessionId.value))
      cached mustBe None
    }

    "throw IllegalStateException when sessionId is not available" in {
      when(hc.sessionId).thenReturn(None)
      val addressLookupParams = AddressLookupParams("AA11 1AA", None)
      intercept[IllegalStateException] {
        await(sessionCache.saveAddressLookupParams(addressLookupParams)(hc))
      }

    }
  }

  private def setupSession: SessionId = {
    val sessionId = SessionId("sessionId-" + UUID.randomUUID())
    when(hc.sessionId).thenReturn(Some(sessionId))
    sessionId
  }

}
