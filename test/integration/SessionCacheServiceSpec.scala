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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any

import java.util.UUID
import java.time.{LocalDate, LocalDateTime}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json.toJson
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.Save4LaterConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.ResponseCommon
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressLookupParams
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.models.address.AddressRequestBody
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{
  CachedData,
  DataUnavailableException,
  SessionCache,
  SessionTimeOutException
}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, SessionId}
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import util.builders.RegistrationDetailsBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SessionCacheSpec extends IntegrationTestsSpec with MockitoSugar with MongoSpecSupport {

  lazy val appConfig      = app.injector.instanceOf[AppConfig]
  lazy val mockHttpClient = mock[HttpClient]

  private val reactiveMongoComponent = new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  }

  implicit private val save4LaterConnector = new Save4LaterConnector(mockHttpClient, appConfig)
  private val save4LaterService            = new Save4LaterService(save4LaterConnector)

  val sessionCache = new SessionCache(appConfig, reactiveMongoComponent, save4LaterService)

  val hc = mock[HeaderCarrier]

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
      await(
        sessionCache.insert(
          Cache(Id(s2.value), data = Some(toJson(CachedData(regDetails = Some(individualRegistrationDetails)))))
        )
      )

      await(sessionCache.subscriptionDetails(hc)) mustBe SubscriptionDetails()
    }

    "store, fetch and update Registration details correctly" in {
      val sessionId: SessionId = setupSession

      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson                     = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registrationDetails(hc)) mustBe organisationRegistrationDetails
      await(sessionCache.saveRegistrationDetails(individualRegistrationDetails)(hc))

      val updatedCache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedUpdatedJson                     = toJson(CachedData(regDetails = Some(individualRegistrationDetails)))
      val Some(Cache(_, Some(updatedJson), _, _)) = updatedCache
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

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson                     = toJson(CachedData(registerWithEoriAndIdResponse = Some(rd)))
      val Some(Cache(_, Some(json), _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registerWithEoriAndIdResponse(hc)) mustBe rd

    }

    "throw exception when registration Details requested and not available in cache" in {
      val s = setupSession
      await(sessionCache.insert(Cache(Id(s.value), data = Some(toJson(CachedData())))))

      val caught = intercept[DataUnavailableException] {
        await(sessionCache.registrationDetails(hc))
      }
      caught.getMessage mustBe s"regDetails is not cached in data for the sessionId: ${s.value}"
    }

    "store Address Lookup Params correctly" in {

      val sessionId: SessionId = setupSession

      val addressLookupParams = AddressLookupParams("AA11 1AA", None)

      await(sessionCache.saveAddressLookupParams(addressLookupParams)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(addressLookupParams = Some(addressLookupParams)))

      val Some(Cache(_, Some(json), _, _)) = cache

      json mustBe expectedJson
    }
    "store and fetch group enrolment correctly" in {

      val sessionId: SessionId = setupSession

      val groupEnrolmentResponse = EnrolmentResponse(Service.cds.enrolmentKey, "Activated", List.empty)

      await(sessionCache.saveGroupEnrolment(groupEnrolmentResponse)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(groupEnrolment = Some(groupEnrolmentResponse)))

      val Some(Cache(_, Some(json), _, _)) = cache

      json mustBe expectedJson

      await(sessionCache.groupEnrolment(hc)) mustBe groupEnrolmentResponse
    }

    "store Eori correctly" in {

      val sessionId: SessionId = setupSession

      val eori = Eori("GB123456789123")

      await(sessionCache.saveEori(eori)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(eori = Some(eori.id)))

      val Some(Cache(_, Some(json), _, _)) = cache

      json mustBe expectedJson
    }

    "store email correctly" in {

      val sessionId: SessionId = setupSession

      val email = "email@email.com"

      await(sessionCache.saveEmail(email)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(email = Some(email)))

      val Some(Cache(_, Some(json), _, _)) = cache

      json mustBe expectedJson
    }

    "store subscription details correctly" in {

      val sessionId: SessionId = setupSession

      val subscriptionDetails = SubscriptionDetails(email = Some("email@email.com"))

      await(sessionCache.saveSubscriptionDetails(subscriptionDetails)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(subDetails = Some(subscriptionDetails)))

      val Some(Cache(_, Some(json), _, _)) = cache

      json mustBe expectedJson
    }

    "store sub01Outcome details correctly" in {

      val sessionId: SessionId = setupSession

      val sub01Outcome = Sub01Outcome(LocalDate.of(1961, 4, 12).toString)

      await(sessionCache.saveSub01Outcome(sub01Outcome)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(sub01Outcome = Some(sub01Outcome)))

      val Some(Cache(_, Some(json), _, _)) = cache

      json mustBe expectedJson
    }

    "store sub02Outcome details correctly" in {

      val sessionId: SessionId = setupSession

      val sub01Outcome = Sub01Outcome(LocalDate.of(1961, 4, 12).toString)

      await(sessionCache.saveSub01Outcome(sub01Outcome)(hc))

      val cache = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJson = toJson(CachedData(sub01Outcome = Some(sub01Outcome)))

      val Some(Cache(_, Some(json), _, _)) = cache

      json mustBe expectedJson
    }

    "clear Address Lookup Params" in {

      val sessionId: SessionId = setupSession

      val addressLookupParams = AddressLookupParams("AA11 1AA", None)

      await(sessionCache.saveAddressLookupParams(addressLookupParams)(hc))

      val Some(Cache(_, Some(jsonBefore), _, _)) = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJsonBefore = toJson(CachedData(addressLookupParams = Some(addressLookupParams)))

      jsonBefore mustBe expectedJsonBefore

      await(sessionCache.clearAddressLookupParams(hc))

      val Some(Cache(_, Some(jsonAfter), _, _)) = await(sessionCache.findById(Id(sessionId.value)))

      val expectedJsonAfter = toJson(CachedData(addressLookupParams = Some(AddressLookupParams("", None))))

      jsonAfter mustBe expectedJsonAfter
    }

    "remove from the cache" in {
      val sessionId: SessionId = setupSession
      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(hc))

      await(sessionCache.remove(hc))

      val cached = await(sessionCache.findById(Id(sessionId.value)))
      cached mustBe None
    }

    "throw IllegalStateException when sessionId is not available" in {
      val sessionId: SessionId = setupSession
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
