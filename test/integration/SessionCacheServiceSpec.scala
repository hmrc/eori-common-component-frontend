/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.mvc.{Request, Session}
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.ResponseCommon
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{SubmissionCompleteData, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressLookupParams
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{CachedData, DataUnavailableException, SessionCache}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.cache.DataKey
import util.builders.RegistrationDetailsBuilder._
import uk.gov.hmrc.mongo.test.MongoSupport

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

  implicit val request: Request[Any] = mock[Request[Any]]
  val hc: HeaderCarrier              = mock[HeaderCarrier]
  "Session cache" should {

    "provide default when subscription details holder not in cache" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      await(sessionCache.putSession(DataKey("regDetails"), data = Json.toJson(individualRegistrationDetails)))

      await(sessionCache.subscriptionDetails(request)) mustBe SubscriptionDetails()
    }

    "store, fetch and update Registration details correctly" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(request))

      val cacheItem = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )
      val expectedJson = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      cacheItem.data mustBe expectedJson

      await(sessionCache.registrationDetails(request)) mustBe organisationRegistrationDetails
      await(sessionCache.saveRegistrationDetails(individualRegistrationDetails)(request))

      val cacheUpdate = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )
      val expectedUpdatedJson = toJson(CachedData(regDetails = Some(individualRegistrationDetails)))
      cacheUpdate.data mustBe expectedUpdatedJson
    }

    "store, fetch and update Registration details when group ID and orgType are privided correctly" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))
      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails, GroupId("groupId"))(hc, request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))

      cache.data mustBe expectedJson

      await(sessionCache.registrationDetails(request)) mustBe organisationRegistrationDetails
      await(sessionCache.saveRegistrationDetails(individualRegistrationDetails)(request))

      val updatedCache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )
      val expectedUpdatedJson = toJson(CachedData(regDetails = Some(individualRegistrationDetails)))
      updatedCache.data mustBe expectedUpdatedJson
    }

    "calling saveRegistrationDetailsWithoutId should store, fetch and update Registration details when group ID and orgType are privided correctly" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))
      await(
        sessionCache.saveRegistrationDetailsWithoutId(organisationRegistrationDetails, GroupId("groupId"))(hc, request)
      )

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(CachedData(regDetails = Some(organisationRegistrationDetails)))
      cache.data mustBe expectedJson

      await(sessionCache.registrationDetails(request)) mustBe organisationRegistrationDetails
      await(sessionCache.saveRegistrationDetails(individualRegistrationDetails)(request))

      val updatedCache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedUpdatedJson = toJson(CachedData(regDetails = Some(individualRegistrationDetails)))
      updatedCache.data mustBe expectedUpdatedJson
    }

    "store and fetch RegisterWith EORI And Id Response correctly for Reg06 response" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

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

      await(sessionCache.saveRegisterWithEoriAndIdResponse(rd)(request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )
      val expectedJson = toJson(CachedData(registerWithEoriAndIdResponse = Some(rd)))
      cache.data mustBe expectedJson

      await(sessionCache.registerWithEoriAndIdResponse(request)) mustBe rd

    }

    "store and fetch RegisterWithEORIAndId with SubscriptionDetails pre-populated" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val subscriptionDetails =
        SubscriptionDetails(eoriNumber = Some("123456789"), nameDetails = Some(NameMatchModel("John Doe")))
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
      val submissionCompleteData = SubmissionCompleteData(Some(subscriptionDetails), None)

      await(sessionCache.saveSubmissionCompleteDetails(submissionCompleteData)(request))

      val cachePreUpdate = await(sessionCache.cacheRepo.findById(request))

      val expectedJsonPreUpdate = toJson(
        CachedData(
          registerWithEoriAndIdResponse = None,
          submissionCompleteDetails = Some(SubmissionCompleteData(Some(subscriptionDetails), None))
        )
      )

      val Some(CacheItem(_, jsonPreUpdate, _, _)) = cachePreUpdate
      jsonPreUpdate mustBe expectedJsonPreUpdate

      await(sessionCache.saveRegisterWithEoriAndIdResponse(rd)(request))

      val cache = await(sessionCache.cacheRepo.findById(request))

      val expectedJson = toJson(
        CachedData(
          registerWithEoriAndIdResponse = Some(rd),
          submissionCompleteDetails =
            Some(SubmissionCompleteData(Some(subscriptionDetails), Some(rd.responseCommon.processingDate)))
        )
      )
      val Some(CacheItem(_, json, _, _)) = cache
      json mustBe expectedJson

      await(sessionCache.registerWithEoriAndIdResponse(request)) mustBe rd

    }

    "store Address Lookup Params correctly" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val addressLookupParams = AddressLookupParams("AA11 1AA", None)

      await(sessionCache.saveAddressLookupParams(addressLookupParams)(request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(CachedData(addressLookupParams = Some(addressLookupParams)))

      cache.data mustBe expectedJson
    }
    "store and fetch group enrolment correctly" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val groupEnrolmentResponse = EnrolmentResponse(Service.cds.enrolmentKey, "Activated", List.empty)

      await(sessionCache.saveGroupEnrolment(groupEnrolmentResponse)(request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(CachedData(groupEnrolment = Some(groupEnrolmentResponse)))

      cache.data mustBe expectedJson

      await(sessionCache.groupEnrolment(request)) mustBe groupEnrolmentResponse
    }

    "throw DataUnavailableException when groupEnrolment is not present in cache" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-123"))))
      await(sessionCache.putSession(DataKey("sub01Outcome"), data = Json.toJson(sub01Outcome)))
      val caught = intercept[DataUnavailableException] {
        await(sessionCache.groupEnrolment(request))
      }
      caught.getMessage startsWith s"sub01Outcome is not cached in data for the sessionId: sessionId-123"
    }

    "throw DataUnavailableException when registerWithEoriAndIdResponse is not present in cache" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-123"))))
      await(sessionCache.putSession(DataKey("sub01Outcome"), data = Json.toJson(sub01Outcome)))
      val caught = intercept[DataUnavailableException] {
        await(sessionCache.registerWithEoriAndIdResponse(request))
      }
      caught.getMessage startsWith s"registerWithEoriAndIdResponse is not cached in data for the sessionId: sessionId-123"
    }

    "return empty subscription details if the subscription details are missing" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-123"))))
      await(sessionCache.putSession(DataKey("sub01Outcome"), data = Json.toJson(sub01Outcome)))

      val response = await(sessionCache.subscriptionDetails(request))
      response mustBe SubscriptionDetails()
    }

    "throw DataUnavailableException when sub02Outcome is not present in cache" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-123"))))
      await(sessionCache.putSession(DataKey("sub01Outcome"), data = Json.toJson(sub01Outcome)))
      val caught = intercept[DataUnavailableException] {
        await(sessionCache.sub02Outcome(request))
      }
      caught.getMessage startsWith s"sub02Outcome is not cached in data for the sessionId: sessionId-123"
    }

    "throw DataUnavailableException when sub01Outcome is not present in cache" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-123"))))
      await(sessionCache.putSession(DataKey("regDetails"), data = Json.toJson(individualRegistrationDetails)))
      val caught = intercept[DataUnavailableException] {
        await(sessionCache.sub02Outcome(request))
      }
      caught.getMessage startsWith s"sub01Outcome is not cached in data for the sessionId: sessionId-123"
    }

    "throw DataUnavailableException when email is not present in cache" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-123"))))
      await(sessionCache.putSession(DataKey("sub01Outcome"), data = Json.toJson(sub01Outcome)))
      val caught = intercept[DataUnavailableException] {
        await(sessionCache.email(request))
      }
      caught.getMessage startsWith s"email is not cached in data for the sessionId: sessionId-123"
    }

    "fetchSafeIdFromReg06Response returns None if reg06response is not present in cache" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-123"))))
      await(sessionCache.putSession(DataKey("sub01Outcome"), data = Json.toJson(sub01Outcome)))
      val response = await(sessionCache.fetchSafeIdFromReg06Response(request))
      response mustBe None
    }

    "fetch safeId correctly from registration details" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))
      val registrationDetails: RegistrationDetails = RegistrationDetails.individual(
        sapNumber = "0123456789",
        safeId = SafeId("safe-id"),
        name = "John Doe",
        address = defaultAddress,
        dateOfBirth = LocalDate.parse("1980-07-23"),
        customsId = Some(Utr("123UTRNO"))
      )
      await(sessionCache.putSession(DataKey("regDetails"), data = Json.toJson(registrationDetails)))

      await(sessionCache.safeId(request)) mustBe SafeId("safe-id")
    }

    "fetch safeId correctly from registerWithEoriAndIdResponse details" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))
      def registerWithEoriAndIdResponse = RegisterWithEoriAndIdResponse(
        ResponseCommon("OK", None, LocalDateTime.now(), None),
        Some(
          RegisterWithEoriAndIdResponseDetail(
            Some("PASS"),
            Some("C001"),
            responseData = Some(
              ResponseData(
                "someSafeId",
                Trader("John Doe", "Mr D"),
                EstablishmentAddress("Line 1", "City Name", Some("SE28 1AA"), "GB"),
                Some(
                  ContactDetail(
                    EstablishmentAddress("Line 1", "City Name", Some("SE28 1AA"), "GB"),
                    "John Contact Doe",
                    Some("1234567"),
                    Some("89067"),
                    Some("john.doe@example.com")
                  )
                ),
                VATIDs = Some(Seq(VatIds("AD", "1234"), VatIds("GB", "4567"))),
                hasInternetPublication = false,
                principalEconomicActivity = Some("P001"),
                hasEstablishmentInCustomsTerritory = Some(true),
                legalStatus = Some("Official"),
                thirdCountryIDNumber = Some(Seq("1234", "67890")),
                personType = Some(9),
                dateOfEstablishmentBirth = Some("2018-05-16"),
                startDate = "2018-05-15",
                expiryDate = Some("2018-05-16")
              )
            )
          )
        )
      )
      await(
        sessionCache.putSession(
          DataKey("registerWithEoriAndIdResponse"),
          data = Json.toJson(registerWithEoriAndIdResponse)
        )
      )

      await(sessionCache.safeId(request)) mustBe SafeId("someSafeId")
    }

    "throw exception if  safeId couldn't be retrieved from cache" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val eori = Eori("GB123456789123")

      await(sessionCache.saveEori(eori)(request))
      val caught = intercept[IllegalStateException] {

        await(sessionCache.safeId(request))
      }
      caught.getMessage must startWith("safeId is not cached in data for the sessionId")
    }

    "store and fetch Eori correctly" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val eori = Eori("GB123456789123")

      await(sessionCache.saveEori(eori)(request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(CachedData(eori = Some(eori.id)))

      cache.data mustBe expectedJson

      await(sessionCache.eori(request)) mustBe Some(eori.id)
    }

    "store and fetch email correctly" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val email = "email@email.com"

      await(sessionCache.saveEmail(email)(request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(CachedData(email = Some(email)))

      cache.data mustBe expectedJson
      await(sessionCache.email(request)) mustBe email

    }

    "store subscription details correctly" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val subscriptionDetails = SubscriptionDetails(email = Some("email@email.com"))

      await(sessionCache.saveSubscriptionDetails(subscriptionDetails)(request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(
        CachedData(
          subDetails = Some(subscriptionDetails),
          submissionCompleteDetails = Some(SubmissionCompleteData(Some(subscriptionDetails), None))
        )
      )

      cache.data mustBe expectedJson
    }

    "store subscription details correctly with processedDate pre-populated" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val now = Some(LocalDateTime.now())

      val submissionCompleteData = SubmissionCompleteData(None, now)

      await(sessionCache.saveSubmissionCompleteDetails(submissionCompleteData)(request))

      val cachePreUpdate =
        await(sessionCache.cacheRepo.findById(request)).getOrElse(throw new IllegalStateException("cache not found"))

      val expectedJsonPreUpdate =
        toJson(CachedData(subDetails = None, submissionCompleteDetails = Some(SubmissionCompleteData(None, now))))

      cachePreUpdate.data mustBe expectedJsonPreUpdate

      val subscriptionDetails = SubscriptionDetails(email = Some("email@email.com"))

      await(sessionCache.saveSubscriptionDetails(subscriptionDetails)(request))

      val cache =
        await(sessionCache.cacheRepo.findById(request)).getOrElse(throw new IllegalStateException("cache not found"))

      val expectedJson = toJson(
        CachedData(
          subDetails = Some(subscriptionDetails),
          submissionCompleteDetails = Some(SubmissionCompleteData(Some(subscriptionDetails), now))
        )
      )
      cache.data mustBe expectedJson
    }

    "store and fetch sub01Outcome details correctly" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val sub01Outcome = Sub01Outcome(LocalDate.of(1961, 4, 12).toString)

      await(sessionCache.saveSub01Outcome(sub01Outcome)(request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(CachedData(sub01Outcome = Some(sub01Outcome)))

      cache.data mustBe expectedJson

      await(sessionCache.sub01Outcome(request)) mustBe sub01Outcome
    }

    "store and fetch sub02Outcome details correctly" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val sub02Outcome = Sub02Outcome(LocalDate.of(1961, 4, 12).toString, "fullName", Some("GB123456789123"))

      await(sessionCache.saveSub02Outcome(sub02Outcome)(request))

      val cache = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJson = toJson(CachedData(sub02Outcome = Some(sub02Outcome)))

      cache.data mustBe expectedJson
      await(sessionCache.sub02Outcome(request)) mustBe sub02Outcome
    }

    "store keepAlive details correctly" in {
      await(sessionCache.keepAlive(request)) mustBe true
    }
    "clear Address Lookup Params" in {

      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))

      val addressLookupParams = AddressLookupParams("AA11 1AA", None)

      await(sessionCache.saveAddressLookupParams(addressLookupParams)(request))

      await(sessionCache.clearAddressLookupParams(request))

      val cacheItem = await(sessionCache.cacheRepo.findById(request)).getOrElse(
        throw new IllegalStateException("Cache returned None")
      )

      val expectedJsonAfter = toJson(CachedData(addressLookupParams = Some(AddressLookupParams("", None))))

      cacheItem.data mustBe expectedJsonAfter
      await(sessionCache.addressLookupParams(request)) mustBe Some(AddressLookupParams("", None))
    }

    "remove from the cache" in {
      when(request.session).thenReturn(Session(Map(("sessionId", "sessionId-" + UUID.randomUUID()))))
      await(sessionCache.saveRegistrationDetails(organisationRegistrationDetails)(request))

      await(sessionCache.remove(request))

      val cached = await(sessionCache.cacheRepo.findById(request))
      cached mustBe None
    }

  }

}
