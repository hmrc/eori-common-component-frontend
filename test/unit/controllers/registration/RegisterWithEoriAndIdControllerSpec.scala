/*
 * Copyright 2020 HM Revenue & Customs
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

package unit.controllers.registration

import common.pages.subscription.{ApplicationPendingPage, ApplicationUnsuccessfulPage}
import common.pages.{RegistrationProcessingPage, RegistrationRejectedPage}
import common.support.testdata.TestData
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.{Assertion, BeforeAndAfterEach}
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.RegisterWithEoriAndIdController
import uk.gov.hmrc.customs.rosmfrontend.controllers.registration.routes._
import uk.gov.hmrc.customs.rosmfrontend.controllers.subscription.routes._
import uk.gov.hmrc.customs.rosmfrontend.domain.RegisterWithEoriAndIdResponse._
import uk.gov.hmrc.customs.rosmfrontend.domain.{CacheIds, _}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.SubscriptionCreateResponse.EoriAlreadyExists
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.{Address, ResponseCommon}
import uk.gov.hmrc.customs.rosmfrontend.domain.registration.UserLocation
import uk.gov.hmrc.customs.rosmfrontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.customs.rosmfrontend.models.Journey
import uk.gov.hmrc.customs.rosmfrontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.customs.rosmfrontend.services.registration.{MatchingService, Reg06Service}
import uk.gov.hmrc.customs.rosmfrontend.services.subscription._
import uk.gov.hmrc.customs.rosmfrontend.views.html.error_template
import uk.gov.hmrc.customs.rosmfrontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder._
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class RegisterWithEoriAndIdControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockCache = mock[SessionCache]
  private val mockReg06Service = mock[Reg06Service]
  private val mockMatchingService = mock[MatchingService]
  private val mockCdsSubscriber = mock[CdsSubscriber]
  private val mockSubscriptionStatusService = mock[SubscriptionStatusService]
  private val mockTaxEnrolmentsService = mock[TaxEnrolmentsService]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockSubscriptionDetails = mock[SubscriptionDetails]
  private val mockSub01Outcome = mock[Sub01Outcome]

  private val sub01OutcomeProcessingView =
    app.injector.instanceOf[sub01_outcome_processing]
  private val sub01OutcomeRejectedView =
    app.injector.instanceOf[sub01_outcome_rejected]
  private val errorTemplateView = app.injector.instanceOf[error_template]
  private val subscriptionOutcomePendingView =
    app.injector.instanceOf[subscription_outcome_pending]
  private val subscriptionOutcomeFailView =
    app.injector.instanceOf[subscription_outcome_fail]
  private val reg06EoriAlreadyLinked =
    app.injector.instanceOf[reg06_eori_already_linked]

  private val controller = new RegisterWithEoriAndIdController(
    app,
    mockAuthConnector,
    mockRequestSessionData,
    mockCache,
    mockReg06Service,
    mockMatchingService,
    mockCdsSubscriber,
    mockSubscriptionStatusService,
    mockSubscriptionDetailsService,
    mcc,
    sub01OutcomeProcessingView,
    sub01OutcomeRejectedView,
    errorTemplateView,
    subscriptionOutcomePendingView,
    subscriptionOutcomeFailView,
    reg06EoriAlreadyLinked,
    mockTaxEnrolmentsService
  )(global)

  private val formBundleIdResponse: String = "Form-Bundle-Id"

  private val organisationRegistrationDetails =
    RegistrationDetailsOrganisation(
      Some(Utr("someId")),
      TaxPayerId("SapNumber"),
      SafeId("safe-id"),
      "Name",
      Address("LineOne", None, None, None, postalCode = Some("Postcode"), countryCode = "GB"),
      Some(LocalDate.parse("2018-01-01")),
      Some(CorporateBody)
    )

  private val individualRegistrationDetails =
    RegistrationDetailsIndividual(
      Some(Nino("someNino")),
      TaxPayerId("SapNumber"),
      SafeId("safe-id"),
      "Name",
      Address("LineOne", None, None, None, postalCode = Some("Postcode"), countryCode = "GB"),
      LocalDate.parse("1975-03-26")
    )

  private def stubRegisterWithEoriAndIdResponse(outcomeType: String = "PASS"): RegisterWithEoriAndIdResponse = {
    val processingDate = DateTime.now.withTimeAtStartOfDay()
    val responseCommon =
      ResponseCommon(status = "OK", processingDate = processingDate)
    val trader = Trader(fullName = "New trading", shortName = "nt")
    val establishmentAddress = EstablishmentAddress(streetAndNumber = "new street", city = "leeds", countryCode = "GB")
    val responseData: ResponseData = ResponseData(
      SAFEID = "SomeSafeId",
      trader = trader,
      establishmentAddress = establishmentAddress,
      hasInternetPublication = true,
      startDate = "2018-01-01"
    )
    val registerWithEoriAndIdResponseDetail =
      RegisterWithEoriAndIdResponseDetail(
        outcome = Some(outcomeType),
        caseNumber = Some("case no 1"),
        responseData = Some(responseData)
      )
    RegisterWithEoriAndIdResponse(responseCommon, Some(registerWithEoriAndIdResponseDetail))
  }

  private def stubHandleErrorCodeResponse(statusText: String): RegisterWithEoriAndIdResponse = {
    val processingDate = DateTime.now.withTimeAtStartOfDay()
    val responseCommon = ResponseCommon(status = "OK", statusText = Some(statusText), processingDate = processingDate)
    RegisterWithEoriAndIdResponse(responseCommon, None)
  }

  private val stubRegisterWithEoriAndIdResponseFail =
    stubRegisterWithEoriAndIdResponse("FAIL")
  private val stubRegisterWithEoriAndIdResponseDeferred =
    stubRegisterWithEoriAndIdResponse("DEFERRED")
  private val stubRegisterWithEoriAndIdResponseExceptionCase =
    stubRegisterWithEoriAndIdResponse("ANYTHING ELSE")

  override def beforeEach: Unit = {
    reset(
      mockAuthConnector,
      mockCdsSubscriber,
      mockCache,
      mockReg06Service,
      mockSubscriptionStatusService,
      mockSubscriptionDetailsService
    )
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(Utr(""))))
  }

  "Register with existing eori" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.registerWithEoriAndId(Journey.Migrate)
    )
    val processingDateResponse: String = "19 April 2018"
    val emailVerificationTimestamp = TestData.emailVerificationTimestamp
    "create a subscription for organisation" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(
        mockReg06Service
          .sendOrganisationRequest(any(), any(), any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(NewSubscription))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId])(any())
      ).thenReturn(Future.successful(()))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe Sub02Controller
            .migrationEnd()
            .url
        }
      }
    }

    "create a subscription for sole trader" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockReg06Service.sendIndividualRequest(any(), any(), any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(NewSubscription))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId])(any())
      ).thenReturn(Future.successful(()))

      when(
        mockCdsSubscriber.subscribeWithCachedDetails(meq(Some(CdsOrganisationType.SoleTrader)), any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe Sub02Controller
            .migrationEnd()
            .url
        }
      }
    }

    "create a subscription for sole trader with status SubscriptionRejected" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockReg06Service.sendIndividualRequest(any(), any(), any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(SubscriptionRejected))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId])(any())
      ).thenReturn(Future.successful(()))

      when(
        mockCdsSubscriber.subscribeWithCachedDetails(meq(Some(CdsOrganisationType.SoleTrader)), any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe Sub02Controller
            .migrationEnd()
            .url
        }
      }
    }

    "create a subscription for individual ROW" in {
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(
        mockMatchingService
          .sendIndividualRequestForMatchingService(any[Request[AnyContent]], any[LoggedInUser], any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("taxPayerID"), meq("SapNumber000000000000000000000000000000000"))(any())
      ).thenReturn(Future.successful(NewSubscription))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.Individual))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId])(any())
      ).thenReturn(Future.successful(()))

      when(
        mockCdsSubscriber.subscribeWithCachedDetails(meq(Some(CdsOrganisationType.Individual)), any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe Sub02Controller
            .migrationEnd()
            .url
        }
      }
    }

    "create a subscription for organisation ROW when cachedCustomsId is present" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(
        mockReg06Service
          .sendOrganisationRequest(any(), any(), any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId])(any())
      ).thenReturn(Future.successful(()))

      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(NewSubscription))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe Sub02Controller
            .migrationEnd()
            .url
        }
      }
    }

    "create a subscription for sole trader ROW when cachedCustomsId is present" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockReg06Service.sendIndividualRequest(any(), any(), any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(NewSubscription))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId])(any())
      ).thenReturn(Future.successful(()))

      when(
        mockCdsSubscriber.subscribeWithCachedDetails(meq(Some(CdsOrganisationType.SoleTrader)), any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe Sub02Controller
            .migrationEnd()
            .url
        }
      }
    }

    "redirect to pending when subscription for organisation returns status as WORKLIST within SubscriptionPending" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionPending(formBundleIdResponse, processingDateResponse, Some(emailVerificationTimestamp))
        )
      )
      when(
        mockReg06Service
          .sendOrganisationRequest(any(), any(), any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId])(any())
      ).thenReturn(Future.successful(()))

      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(NewSubscription))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe RegisterWithEoriAndIdController
            .pending(processingDateResponse)
            .url
        }
      }
    }

    "redirect to fail when REGO6 outcome is 'FAIL'" in {
      when(
        mockReg06Service
          .sendOrganisationRequest(any(), any(), any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponseFail))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe RegisterWithEoriAndIdController
            .fail(DateTime.now.withTimeAtStartOfDay().toString("d MMMM yyyy"))
            .url
        }
      }
    }

    "redirect to pending when REGO6 outcome is 'DEFERRED'" in {
      when(
        mockReg06Service
          .sendOrganisationRequest(any(), any(), any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponseDeferred))
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe RegisterWithEoriAndIdController
            .pending(DateTime.now.withTimeAtStartOfDay().toString("d MMMM yyyy"))
            .url
        }
      }
    }

    "throw an exception when REGO6 outcome is unexpected type" in {
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponseExceptionCase))

      regExistingEori { result =>
        {
          the[IllegalStateException] thrownBy {
            status(result) shouldBe SEE_OTHER
          } should have message "Unknown RegistrationDetailsOutCome"
        }
      }
    }

    "redirect to fail when REG01 fails to match for ROW Journey type" in {
      when(
        mockMatchingService
          .sendOrganisationRequestForMatchingService(any[Request[AnyContent]], any[LoggedInUser], any[HeaderCarrier])
      ).thenReturn(Future.successful(false))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe RegisterWithEoriAndIdController
            .fail(DateTime.now.withTimeAtStartOfDay().toString("d MMMM yyyy"))
            .url
        }
      }
    }

    "redirect to processing when Subscription Status (SUB01) response is SubscriptionProcessing" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(SubscriptionProcessing))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe RegisterWithEoriAndIdController
            .processing()
            .url
          verify(mockReg06Service).sendOrganisationRequest(any(), any(), any())
          verify(mockSubscriptionStatusService)
            .getStatus(meq("SAFE"), meq("SomeSafeId"))(any[HeaderCarrier])
        }
      }
    }

    "redirect to SignInWithDifferentDetails when Subscription Status (SUB01) response is SubscriptionExists and enrolment service returns true" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(SubscriptionExists))
      when(
        mockTaxEnrolmentsService
          .doesEnrolmentExist(meq(SafeId("SomeSafeId")))(any(), any())
      ).thenReturn(Future.successful(true))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe SignInWithDifferentDetailsController
            .form(journey = Journey.Migrate)
            .url
          verify(mockReg06Service).sendOrganisationRequest(any(), any(), any())
          verify(mockSubscriptionStatusService)
            .getStatus(meq("SAFE"), meq("SomeSafeId"))(any[HeaderCarrier])
        }
      }
    }

    "redirect to CompleteEnrolment when Subscription Status (SUB01) response is SubscriptionExists and enrolment service returns false" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any())
      ).thenReturn(Future.successful(SubscriptionExists))
      when(
        mockTaxEnrolmentsService
          .doesEnrolmentExist(meq(SafeId("SomeSafeId")))(any(), any())
      ).thenReturn(Future.successful(false))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe SubscriptionRecoveryController
            .complete(Journey.Migrate)
            .url
          verify(mockReg06Service).sendOrganisationRequest(any(), any(), any())
          verify(mockSubscriptionStatusService)
            .getStatus(meq("SAFE"), meq("SomeSafeId"))(any[HeaderCarrier])
        }
      }
    }

    "redirect to Application unsuccessful page when Subscription (SUB02) is failed" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionFailed(
            "Response status of FAIL returned for a SUB02: Create Subscription.",
            processingDateResponse
          )
        )
      )
      when(
        mockReg06Service
          .sendOrganisationRequest(any(), any(), any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockSubscriptionStatusService.getStatus(any(), any())(any()))
        .thenReturn(Future.successful(NewSubscription))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId])(any())
      ).thenReturn(Future.successful(()))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe RegisterWithEoriAndIdController
            .fail(processingDateResponse)
            .url
        }
      }
    }

    "return success with error code as 'EORI already linked to a different ID'" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse(EoriAlreadyLinked)))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe RegisterWithEoriAndIdController
            .eoriAlreadyLinked()
            .url
        }
      }
    }

    "return success with error code as 'Rejected previously'" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(
          Future
            .successful(stubHandleErrorCodeResponse(RejectedPreviouslyAndRetry))
        )
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori { result =>
        {
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe RegisterWithEoriAndIdController
            .rejectedPreviously()
            .url
        }
      }
    }

    "return unexpected status text" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Option[CdsOrganisationType]], any[Journey.Value])(
          any[HeaderCarrier],
          any[Request[AnyContent]]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionSuccessful(
            Eori("EORI-Number"),
            formBundleIdResponse,
            processingDateResponse,
            Some(emailVerificationTimestamp)
          )
        )
      )
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse("")))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori { result =>
        {
          status(result) shouldBe SERVICE_UNAVAILABLE
        }
      }
    }

    "throw an exception when safeId is not found" in {
      val mockRegisterWithEoriAndIdResponse =
        mock[RegisterWithEoriAndIdResponse]

      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail)
        .thenReturn(Some(RegisterWithEoriAndIdResponseDetail(Some("PASS"), None)))

      regExistingEori { result =>
        {
          the[IllegalStateException] thrownBy {
            status(result) shouldBe OK
          } should have message "SafeId can't be none"
        }
      }
    }

    "Call the processing function for ROW" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockCache.sub01Outcome(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSub01Outcome))
      when(mockSub01Outcome.processedDate).thenReturn("11 January 2015")

      invokeProcessing { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title() should startWith(RegistrationProcessingPage.title)
        page.getElementsText(RegistrationProcessingPage.pageHeadingXpath) shouldBe RegistrationProcessingPage.individualHeading
        page.getElementsText(RegistrationProcessingPage.processedDateXpath) shouldBe "Application received by HMRC on 11 January 2015"
      }
    }

    "Call the processing function for UK" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.name).thenReturn("Name")
      when(mockCache.sub01Outcome(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSub01Outcome))
      when(mockSub01Outcome.processedDate).thenReturn("11 January 2015")

      invokeProcessing { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title() should startWith(RegistrationProcessingPage.title)
        page.getElementsText(RegistrationProcessingPage.pageHeadingXpath) shouldBe RegistrationProcessingPage.individualHeading
        page.getElementsText(RegistrationProcessingPage.processedDateXpath) shouldBe "Application received by HMRC on 11 January 2015"
      }
    }

    "Call the rejected function" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockCache.sub01Outcome(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSub01Outcome))
      when(mockSub01Outcome.processedDate).thenReturn("11 January 2015")

      invokeRejected { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title() should startWith(RegistrationRejectedPage.title)
        page.getElementsText(RegistrationRejectedPage.pageHeadingXpath) shouldBe RegistrationRejectedPage.individualHeading
        page.getElementsText(RegistrationRejectedPage.processedDateXpath) shouldBe "Application received by HMRC on 11 January 2015"
      }
    }

    "Call the pending function" in {
      when(mockCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.eoriNumber)
        .thenReturn(Some("someEoriNumber"))
      when(mockSubscriptionDetails.name).thenReturn("name")
      when(mockCache.remove(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      invokePending() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title() should startWith(ApplicationPendingPage.title)
      }
    }

    "throws exception when Eori number is not found for pending function" in {
      when(mockCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.eoriNumber).thenReturn(None)
      when(mockSubscriptionDetails.name).thenReturn("name")
      when(mockCache.remove(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      invokePending() { result =>
        the[IllegalStateException] thrownBy {
          status(result) shouldBe OK
        } should have message "No EORI found in cache"
      }
    }

    "Call the eoriAlreadyLinked function" in {
      when(mockCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.name).thenReturn("reg06-Eori-Already-Linked")
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse(EoriAlreadyLinked)))
      when(mockCache.remove(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      invokeEoriAlreadyLinked() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("The application for")
      }
    }

    "Call the rejectedPreviously function" in {
      when(mockCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.name).thenReturn("reg06-rejected-previously")
      when(mockCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(
          Future
            .successful(stubHandleErrorCodeResponse(RejectedPreviouslyAndRetry))
        )
      when(mockCache.remove(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      invokeRejectedPreviously() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("The EORI application has been unsuccessful")
      }
    }

    "Call the fail function" in {
      when(mockCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.name).thenReturn("reg06-FAIL")
      when(mockCache.remove(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      invokeFail() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title() should startWith(ApplicationUnsuccessfulPage.title)
      }
    }
  }

  private def regExistingEori(test: Future[Result] => Any) {
    test(controller.registerWithEoriAndId(Journey.Migrate)(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def invokeProcessing(test: Future[Result] => Any) {
    test(
      controller.processing
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def invokeRejected(test: Future[Result] => Any) {
    test(
      controller.rejected
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def invokePending(date: String = "11 August 2015")(test: Future[Result] => Any) {
    test(
      controller
        .pending(date)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def invokeFail(date: String = "11 September 2015")(test: Future[Result] => Any) {
    test(
      controller
        .fail(date)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def invokeEoriAlreadyLinked()(test: Future[Result] => Assertion): Unit =
    test(
      controller
        .eoriAlreadyLinked()
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )

  private def invokeRejectedPreviously()(test: Future[Result] => Assertion): Unit =
    test(
      controller
        .rejectedPreviously()
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
}
