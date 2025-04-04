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

package unit.controllers.registration

import common.pages.subscription.{ApplicationPendingPage, ApplicationUnsuccessfulPage}
import common.pages.{RegistrationProcessingPage, RegistrationRejectedPage}
import common.support.testdata.TestData
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.{Assertion, BeforeAndAfterEach}
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Request, Result, Session}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.GroupEnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.RegisterWithEoriAndIdController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.RegisterWithEoriAndIdResponse._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.{Address, ResponseCommon}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{
  FormData,
  SubmissionCompleteData,
  SubscriptionDetails
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.{MatchingService, Reg06Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.error_template
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.language.LanguageUtils
import unit.controllers.CdsPage
import util.builders.AuthActionMock
import util.builders.AuthBuilder._
import util.{CSRFTest, ControllerSpec}

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.global
import scala.concurrent.{ExecutionContext, Future}

class RegisterWithEoriAndIdControllerSpec
    extends ControllerSpec with BeforeAndAfterEach with AuthActionMock with CSRFTest {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockCache                      = mock[SessionCache]
  private val mockReg06Service               = mock[Reg06Service]
  private val mockMatchingService            = mock[MatchingService]
  private val mockNotifyRcmService           = mock[NotifyRcmService]
  private val mockCdsSubscriber              = mock[CdsSubscriber]
  private val mockSubscriptionStatusService  = mock[SubscriptionStatusService]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockSubscriptionDetails        = mock[SubscriptionDetails]
  private val mockSub01Outcome               = mock[Sub01Outcome]
  private val groupEnrolmentExtractor        = mock[GroupEnrolmentExtractor]
  private val sub01OutcomeProcessingView     = instanceOf[sub01_outcome_processing]
  private val sub01OutcomeRejectedView       = instanceOf[sub01_outcome_rejected]
  private val errorTemplateView              = instanceOf[error_template]
  private val subscriptionOutcomePendingView = instanceOf[subscription_outcome_pending]

  private val reg06EoriAlreadyLinked                       = instanceOf[reg06_eori_already_linked]
  private val reg06IdAlreadyLinked                         = instanceOf[reg06_id_already_linked]
  private val languageUtils                                = instanceOf[LanguageUtils]
  private val subscriptionOutcomeFailCompanyView           = instanceOf[subscription_outcome_fail_company]
  private val subscriptionOutcomeFailLlpView               = instanceOf[subscription_outcome_fail_llp]
  private val subscriptionOutcomeFailPartnershipView       = instanceOf[subscription_outcome_fail_partnership]
  private val subscriptionOutcomeFailOrganisationView      = instanceOf[subscription_outcome_fail_organisation]
  private val subscriptionOutcomeFailSoloAndIndividualView = instanceOf[subscription_outcome_fail_solo_and_individual]
  private val subscriptionOutcomeFailRowUtrView            = instanceOf[subscription_outcome_fail_row_utr_organisation]
  private val subscriptionOutcomeFailRowView               = instanceOf[subscription_outcome_fail_row]

  private val controller = new RegisterWithEoriAndIdController(
    mockAuthAction,
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
    subscriptionOutcomeFailCompanyView,
    subscriptionOutcomeFailPartnershipView,
    subscriptionOutcomeFailLlpView,
    subscriptionOutcomeFailOrganisationView,
    subscriptionOutcomeFailSoloAndIndividualView,
    subscriptionOutcomeFailRowUtrView,
    subscriptionOutcomeFailRowView,
    reg06EoriAlreadyLinked,
    reg06IdAlreadyLinked,
    groupEnrolmentExtractor,
    languageUtils,
    mockNotifyRcmService
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
    val processingDate = LocalDateTime.now()
    val responseCommon =
      ResponseCommon(status = "OK", processingDate = processingDate)
    val trader               = Trader(fullName = "New trading", shortName = "nt")
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
    val processingDate = LocalDateTime.now()
    val responseCommon = ResponseCommon(status = "OK", statusText = Some(statusText), processingDate = processingDate)
    RegisterWithEoriAndIdResponse(responseCommon, None)
  }

  private val stubRegisterWithEoriAndIdResponseFail =
    stubRegisterWithEoriAndIdResponse("FAIL")

  private val stubRegisterWithEoriAndIdResponseDeferred =
    stubRegisterWithEoriAndIdResponse("DEFERRED")

  private val stubRegisterWithEoriAndIdResponseExceptionCase =
    stubRegisterWithEoriAndIdResponse("ANYTHING ELSE")

  override def beforeEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockCdsSubscriber)
    reset(mockCache)
    reset(mockReg06Service)
    reset(mockSubscriptionStatusService)
    reset(mockSubscriptionDetailsService)
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]]))
      .thenReturn(Future.successful(Some(Utr(""))))
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any()))
      .thenReturn(Future.successful(false))
    when(mockCache.journeyCompleted(any[Request[AnyContent]]))
      .thenReturn(Future.successful(true))
  }

  private def assertCleanedSession(result: Future[Result]): Unit = {
    val currentSession: Session = session(result)

    currentSession.data.get("selected-user-location") shouldBe None
    currentSession.data.get("subscription-flow") shouldBe None
    currentSession.data.get("selected-organisation-type") shouldBe None
    currentSession.data.get("uri-before-subscription-flow") shouldBe None
  }

  "Register with existing eori" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.registerWithEoriAndId(atarService))
    val processingDateResponse: String = "19 April 2018"
    val emailVerificationTimestamp     = TestData.emailVerificationTimestamp

    "redirect to enrolment exists if user has group enrolment to service" in {
      when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any())).thenReturn(Future.successful(true))

      regExistingEori() { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe EnrolmentAlreadyExistsController
          .enrolmentAlreadyExistsForGroup(atarService)
          .url
      }
    }

    "create a subscription for organisation" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
          .sendOrganisationRequest(any(), any[HeaderCarrier], any())
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any(), any(), any())
      ).thenReturn(Future.successful(NewSubscription))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any())
      ).thenReturn(Future.successful(()))

      regExistingEori() { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe Sub02Controller
          .migrationEnd(atarService)
          .url
      }
    }

    "create a subscription for sole trader" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockReg06Service.sendIndividualRequest(any(), any[HeaderCarrier], any())).thenReturn(Future.successful(true))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any(), any(), any())
      ).thenReturn(Future.successful(NewSubscription))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any())
      ).thenReturn(Future.successful(()))

      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))

      regExistingEori() { result =>
        assertCleanedSession(result)

        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe Sub02Controller
          .migrationEnd(atarService)
          .url
      }
    }

    "create a subscription for sole trader with status SubscriptionRejected" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockReg06Service.sendIndividualRequest(any(), any[HeaderCarrier], any())).thenReturn(Future.successful(true))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any(), any(), any())
      ).thenReturn(Future.successful(SubscriptionRejected))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any())
      ).thenReturn(Future.successful(()))

      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe Sub02Controller
          .migrationEnd(atarService)
          .url
      }
    }

    "create a subscription for individual ROW" in {
      when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]]))
        .thenReturn(Future.successful(None))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(
        mockMatchingService
          .sendIndividualRequestForMatchingService(any[LoggedInUserWithEnrolments], any[HeaderCarrier], any(), any())
      ).thenReturn(Future.successful(true))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("taxPayerID"), meq("SapNumber000000000000000000000000000000000"))(any(), any(), any())
      ).thenReturn(Future.successful(NewSubscription))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.Individual))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any())
      ).thenReturn(Future.successful(()))

      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe Sub02Controller
          .migrationEnd(atarService)
          .url
      }
    }

    "create a subscription for organisation ROW when cachedCustomsId is present" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
          .sendOrganisationRequest(any(), any[HeaderCarrier], any())
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any())
      ).thenReturn(Future.successful(()))

      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any(), any(), any())
      ).thenReturn(Future.successful(NewSubscription))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe Sub02Controller
          .migrationEnd(atarService)
          .url
      }
    }

    "create a subscription for sole trader ROW when cachedCustomsId is present" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]]))
        .thenReturn(Some(UserLocation.ThirdCountry))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockReg06Service.sendIndividualRequest(any(), any[HeaderCarrier], any())).thenReturn(Future.successful(true))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any(), any(), any())
      ).thenReturn(Future.successful(NewSubscription))
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any())
      ).thenReturn(Future.successful(()))

      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe Sub02Controller
          .migrationEnd(atarService)
          .url
      }
    }

    "redirect to pending when subscription for organisation returns status as WORKLIST within SubscriptionPending" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
        )
      ).thenReturn(
        Future.successful(
          SubscriptionPending(formBundleIdResponse, processingDateResponse, Some(emailVerificationTimestamp))
        )
      )
      when(mockReg06Service.sendOrganisationRequest(any(), any[HeaderCarrier], any())).thenReturn(
        Future.successful(true)
      )
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any())
      ).thenReturn(Future.successful(()))

      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any(), any(), any())
      ).thenReturn(Future.successful(NewSubscription))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .pending(atarService)
          .url
      }
    }

    "redirect to fail when REGO6 outcome is 'FAIL'" in {
      when(mockReg06Service.sendOrganisationRequest(any(), any[HeaderCarrier], any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponseFail))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .fail(atarService)
          .url
      }
    }

    "redirect to pending when REGO6 outcome is 'DEFERRED'" in {
      when(
        mockReg06Service
          .sendOrganisationRequest(any(), any[HeaderCarrier], any())
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponseDeferred))
      when(mockNotifyRcmService.notifyRcm(meq(atarService))(any(), any(), any()))
        .thenReturn(Future.successful(()))
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .pending(atarService)
          .url
        verify(mockNotifyRcmService)
          .notifyRcm(meq(atarService))(any[HeaderCarrier], any[Request[_]], any[ExecutionContext])
      }
    }

    "throw an exception when REGO6 outcome is unexpected type" in {
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponseExceptionCase))

      regExistingEori() { result =>
        the[IllegalStateException] thrownBy {
          status(result) shouldBe SEE_OTHER
        } should have message "Unknown RegistrationDetailsOutCome"
      }
    }

    "redirect to fail when REG01 fails to match for ROW Journey type" in {
      when(
        mockMatchingService
          .sendOrganisationRequestForMatchingService(
            any[Request[AnyContent]],
            any[LoggedInUserWithEnrolments],
            any[HeaderCarrier],
            any()
          )
      ).thenReturn(Future.successful(false))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]]))
        .thenReturn(Future.successful(None))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .fail(atarService)
          .url
      }
    }

    "redirect to processing when Subscription Status (SUB01) response is SubscriptionProcessing" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any(), any(), any())
      ).thenReturn(Future.successful(SubscriptionProcessing))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .processing(atarService)
          .url
        verify(mockReg06Service).sendOrganisationRequest(any(), any(), any())
        verify(mockSubscriptionStatusService)
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any[HeaderCarrier], any(), any())
      }
    }

    "redirect to CompleteEnrolment when Subscription Status (SUB01) response is SubscriptionExists and enrolment service returns false" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(
        mockSubscriptionStatusService
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any(), any(), any())
      ).thenReturn(Future.successful(SubscriptionExists))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe SubscriptionRecoveryController.complete(atarService).url
        verify(mockReg06Service).sendOrganisationRequest(any(), any(), any())
        verify(mockSubscriptionStatusService)
          .getStatus(meq("SAFE"), meq("SomeSafeId"))(any[HeaderCarrier], any(), any())
      }
    }

    "redirect to Application unsuccessful page when Subscription (SUB02) is failed" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
          .sendOrganisationRequest(any(), any[HeaderCarrier], any())
      ).thenReturn(Future.successful(true))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockSubscriptionStatusService.getStatus(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(NewSubscription))
      when(
        mockSubscriptionDetailsService
          .saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any())
      ).thenReturn(Future.successful(()))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .fail(atarService)
          .url
      }
    }

    "return success with error code as 'EORI already linked to a different ID'" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse(EoriAlreadyLinked)))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .eoriAlreadyLinked(atarService)
          .url
      }
    }

    "return success with error code as 'EORI already linked to a different ID' ignoring letter case" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse("600 - EORI Already Linked TO a different ID")))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .eoriAlreadyLinked(atarService)
          .url
      }
    }

    "return success with error code as 'ID already linked to a different EORI'" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse(IDLinkedWithEori)))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .idAlreadyLinked(atarService)
          .url
      }
    }

    "return success with error code as 'ID already linked to a different EORI' ignoring letter case" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse("602 - ID Already Linked To A Different EORI")))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .idAlreadyLinked(atarService)
          .url
      }
    }

    "return success with error code as 'Rejected previously'" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(
          Future
            .successful(stubHandleErrorCodeResponse(RejectedPreviouslyAndRetry))
        )
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .rejectedPreviously(atarService)
          .url
      }
    }

    "return success with error code as 'Rejected previously' ignoring letter case" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(
          Future
            .successful(stubHandleErrorCodeResponse("601 - Rejected Previously AND Retry Failed"))
        )
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .rejectedPreviously(atarService)
          .url
      }
    }

    "return success with error code as 'Request could not be processed'" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(
          Future
            .successful(stubHandleErrorCodeResponse(RequestCouldNotBeProcessed))
        )
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .fail(atarService)
          .url
      }
    }

    "return success with error code as 'Request could not be processed' ignoring letter case" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(
          Future
            .successful(stubHandleErrorCodeResponse("003 - Request Could Not Be Processed"))
        )
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        assertCleanedSession(result)
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe RegisterWithEoriAndIdController
          .fail(atarService)
          .url
      }
    }

    "return unexpected status text" in {
      when(
        mockCdsSubscriber.subscribeWithCachedDetails(any[Service])(
          any[HeaderCarrier],
          any[Request[AnyContent]],
          any[Messages]
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
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse("")))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      regExistingEori() { result =>
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    "throw an exception when safeId is not found" in {
      val mockRegisterWithEoriAndIdResponse =
        mock[RegisterWithEoriAndIdResponse]

      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(mockReg06Service.sendOrganisationRequest(any(), any(), any()))
        .thenReturn(Future.successful(true))
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail)
        .thenReturn(Some(RegisterWithEoriAndIdResponseDetail(Some("PASS"), None)))

      regExistingEori() { result =>
        the[IllegalStateException] thrownBy {
          status(result) shouldBe OK
        } should have message "SafeId can't be none"
      }
    }

    "Call the processing function for ROW" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockCache.sub01Outcome(any[Request[_]]))
        .thenReturn(Future.successful(mockSub01Outcome))
      when(mockSub01Outcome.processedDate).thenReturn("11 January 2015")

      invokeProcessing() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(RegistrationProcessingPage.title)
        page.getElementsText(
          RegistrationProcessingPage.pageHeadingXpath
        ) shouldBe RegistrationProcessingPage.individualHeading
      }
    }

    "Call the processing function for UK" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))
      when(mockCache.subscriptionDetails(any[Request[_]]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.name).thenReturn("Name")
      when(mockCache.sub01Outcome(any[Request[_]]))
        .thenReturn(Future.successful(mockSub01Outcome))
      when(mockSub01Outcome.processedDate).thenReturn("11 January 2015")

      invokeProcessing() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(RegistrationProcessingPage.title)
        page.getElementsText(
          RegistrationProcessingPage.pageHeadingXpath
        ) shouldBe RegistrationProcessingPage.individualHeading
      }
    }

    "Call the rejected function" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCache.registrationDetails(any[Request[_]]))
        .thenReturn(Future.successful(individualRegistrationDetails))
      when(mockCache.sub01Outcome(any[Request[_]]))
        .thenReturn(Future.successful(mockSub01Outcome))
      when(mockSub01Outcome.processedDate).thenReturn("11 January 2015")

      invokeRejected() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(messages("cds.sub01.outcome.rejected.subscribe.title"))
        page.getElementsText(
          RegistrationRejectedPage.pageHeadingXpath
        ) shouldBe RegistrationRejectedPage.individualHeadingSubscription

      }
    }

    "Call the pending function" in {
      val subscriptionDetails =
        SubscriptionDetails(eoriNumber = Some("123456789"), nameDetails = Some(NameMatchModel("John Doe")))
      val submissionCompleteData = SubmissionCompleteData(
        Some(subscriptionDetails),
        Some(stubRegisterWithEoriAndIdResponse().responseCommon.processingDate)
      )

      when(mockCache.submissionCompleteDetails(any[Request[_]]))
        .thenReturn(Future.successful(submissionCompleteData))
      when(mockCache.saveSubmissionCompleteDetails(any())(any[Request[_]]))
        .thenReturn(Future.successful(true))
      when(mockCache.remove(any[Request[_]]))
        .thenReturn(Future.successful(true))

      invokePending() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(ApplicationPendingPage.title)
      }
    }

    "redirect to start page when cache required for pending function is not available" in {

      val subscriptionDetails = SubscriptionDetails(eoriNumber = None, nameDetails = Some(NameMatchModel("John Doe")))
      val submissionCompleteData = SubmissionCompleteData(
        Some(subscriptionDetails),
        Some(stubRegisterWithEoriAndIdResponse().responseCommon.processingDate)
      )

      when(mockCache.submissionCompleteDetails(any[Request[_]]))
        .thenReturn(Future.successful(submissionCompleteData))
      when(mockCache.saveSubmissionCompleteDetails(any())(any[Request[_]]))
        .thenReturn(Future.successful(true))
      when(mockCache.remove(any[Request[_]]))
        .thenReturn(Future.successful(true))

      invokePending() { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe "/customs-enrolment-services/atar/subscribe"
      }
    }

    "Call the eoriAlreadyLinked function" in {
      when(mockCache.subscriptionDetails(any[Request[_]]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.name).thenReturn("reg06-Eori-Already-Linked")
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(Future.successful(stubHandleErrorCodeResponse(EoriAlreadyLinked)))
      when(mockCache.remove(any[Request[_]]))
        .thenReturn(Future.successful(true))
      when(mockCache.email(any[Request[_]]))
        .thenReturn(Future.successful("email@mail.com"))

      invokeEoriAlreadyLinked() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("The details you gave us did not match our records")
      }
    }

    "Call the rejectedPreviously function" in {
      when(mockCache.subscriptionDetails(any[Request[_]]))
        .thenReturn(Future.successful(mockSubscriptionDetails))
      when(mockSubscriptionDetails.name).thenReturn("reg06-rejected-previously")
      when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
        .thenReturn(
          Future
            .successful(stubHandleErrorCodeResponse(RejectedPreviouslyAndRetry))
        )
      when(mockCache.remove(any[Request[_]]))
        .thenReturn(Future.successful(true))

      invokeRejectedPreviously() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(messages("cds.sub01.outcome.rejected.subscribe.title"))
      }
    }

    "Call the fail function" in {
      val formData            = FormData(organisationType = Some(CdsOrganisationType.Company))
      val subscriptionDetails = SubscriptionDetails(customsId = Some(Utr("UTR")), formData = formData)
      when(mockCache.subscriptionDetails(any[Request[_]]))
        .thenReturn(Future.successful(subscriptionDetails))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Uk))

      invokeFail() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(ApplicationUnsuccessfulPage.title)
      }
    }
  }

  "Call the idAlreadyLinked function" in {
    val utr       = Some(Utr("UTRXXXXX"))
    val nameIdOrg = Some(NameIdOrganisationMatchModel("Name", utr.get.id))
    when(mockSubscriptionDetails.nameIdOrganisationDetails).thenReturn(nameIdOrg)
    when(mockCache.subscriptionDetails(any[Request[_]]))
      .thenReturn(Future.successful(mockSubscriptionDetails))
    when(mockSubscriptionDetails.name).thenReturn("reg06_id_already_linked")
    when(mockCache.registerWithEoriAndIdResponse(any[Request[_]]))
      .thenReturn(Future.successful(stubHandleErrorCodeResponse(IDLinkedWithEori)))
    when(mockCache.remove(any[Request[_]]))
      .thenReturn(Future.successful(true))
    when(mockCache.email(any[Request[_]]))
      .thenReturn(Future.successful("email@mail.com"))

    invokeIdAlreadyLinked() { result =>
      status(result) shouldBe OK
      val page = CdsPage(contentAsString(result))
      page.title() should startWith(
        "The details you gave us are matched to a different EORI number - Subscribe to Advance Tariff Rulings - GOV.UK"
      )
    }
  }

  "determineFailView" should {
    implicit val fakeRequest: Request[AnyContentAsEmpty.type] = defaultLangFakeRequest

    "populate Company view" in {
      val company              = CdsOrganisationType.Company
      val isCustomsIdPopulated = true
      val result               = controller.determineFailView(atarService, Some(company), isCustomsIdPopulated, isUk = true)
      val page                 = CdsPage(result.toString())
      page.title() should startWith("Some details you entered do not match our records")
      page.getElementById("orgType").text shouldBe messages("cds.subscription.outcomes.rejected.heading2.company")
    }

    "populate LLP view" in {

      val company              = CdsOrganisationType.LimitedLiabilityPartnership
      val isCustomsIdPopulated = true
      val result               = controller.determineFailView(atarService, Some(company), isCustomsIdPopulated, isUk = true)
      val page                 = CdsPage(result.toString())
      page.title() should startWith("Some details you entered do not match our records")
      page.getElementById("orgType").text shouldBe messages("cds.subscription.outcomes.rejected.heading2.llp")

    }
    "populate Partnership view" in {

      val company              = CdsOrganisationType.Partnership
      val isCustomsIdPopulated = true
      val result               = controller.determineFailView(atarService, Some(company), isCustomsIdPopulated, isUk = true)
      val page                 = CdsPage(result.toString())
      page.title() should startWith("Some details you entered do not match our records")
      page.getElementById("orgType").text shouldBe messages("cds.subscription.outcomes.rejected.heading2.llp")

    }
    "populate Organisation view" in {

      val company              = CdsOrganisationType.CharityPublicBodyNotForProfit
      val isCustomsIdPopulated = true
      val result               = controller.determineFailView(atarService, Some(company), isCustomsIdPopulated, isUk = true)
      val page                 = CdsPage(result.toString())
      page.title() should startWith("Some details you entered do not match our records")
      page.getElementById("orgType").text shouldBe messages("cds.subscription.outcomes.rejected.heading2.organisation")

    }
    "populate Solo view" in {

      val company              = CdsOrganisationType.SoleTrader
      val isCustomsIdPopulated = true
      val result               = controller.determineFailView(atarService, Some(company), isCustomsIdPopulated, isUk = true)
      val page                 = CdsPage(result.toString())
      page.title() should startWith("Some details you entered do not match our records")
      page.getElementById("orgType").text shouldBe messages("cds.subscription.outcomes.rejected.heading2.sole")

    }

    "populate Individual  view" in {

      val company              = CdsOrganisationType.Individual
      val isCustomsIdPopulated = true
      val result               = controller.determineFailView(atarService, Some(company), isCustomsIdPopulated, isUk = true)
      val page                 = CdsPage(result.toString())
      page.title() should startWith("Some details you entered do not match our records")
      page.getElementById("orgType").text shouldBe messages("cds.subscription.outcomes.rejected.heading2.sole")

    }
    "populate Row with UTR view" in {

      val company              = CdsOrganisationType.Company
      val isCustomsIdPopulated = true
      val result               = controller.determineFailView(atarService, Some(company), isCustomsIdPopulated, false)
      val page                 = CdsPage(result.toString())
      page.title() should startWith("Some details you entered do not match our records")
      page.getElementById("orgType").text shouldBe messages("cds.subscription.outcomes.rejected.heading2.organisation")

    }
    "populate Row without UTR view" in {

      val company              = CdsOrganisationType.ThirdCountryOrganisation
      val isCustomsIdPopulated = false
      val result               = controller.determineFailView(atarService, Some(company), isCustomsIdPopulated, isUk = false)
      val page                 = CdsPage(result.toString())
      page.title() should startWith("The name you entered does not match our records")

    }
  }

  private def regExistingEori()(test: Future[Result] => Any) =
    test(controller.registerWithEoriAndId(atarService)(withFakeCSRF(fakeAtarSubscribeRequest)))

  private def invokeProcessing()(test: Future[Result] => Any) =
    test(controller.processing(atarService).apply(withFakeCSRF(fakeAtarSubscribeRequest)))

  private def invokeRejected()(test: Future[Result] => Any) = test(
    controller.rejected(atarService).apply(withFakeCSRF(fakeAtarSubscribeRequest))
  )

  private def invokePending()(test: Future[Result] => Any) =
    test(controller.pending(atarService).apply(withFakeCSRF(fakeAtarSubscribeRequest)))

  private def invokeFail()(test: Future[Result] => Any) =
    test(controller.fail(atarService).apply(withFakeCSRF(fakeAtarSubscribeRequest)))

  private def invokeEoriAlreadyLinked()(test: Future[Result] => Assertion): Unit =
    test(controller.eoriAlreadyLinked(atarService).apply(withFakeCSRF(fakeAtarSubscribeRequest)))

  private def invokeIdAlreadyLinked()(test: Future[Result] => Assertion): Unit =
    test(controller.idAlreadyLinked(atarService).apply(withFakeCSRF(fakeAtarSubscribeRequest)))

  private def invokeRejectedPreviously()(test: Future[Result] => Assertion): Unit =
    test(controller.rejectedPreviously(atarService).apply(withFakeCSRF(fakeAtarSubscribeRequest)))

}
