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

import java.util.UUID

import common.pages.matching.ConfirmPage
import common.pages.{RegistrationProcessingPage, RegistrationRejectedPage}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterEach, mock => _}
import play.api.mvc._
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.{
  CheckYourDetailsRegisterController,
  ConfirmContactDetailsController
}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Address
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{SubscriptionDetails, SubscriptionPage}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.confirm_contact_details
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.{
  sub01_outcome_processing,
  sub01_outcome_rejected
}
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.RegistrationDetailsBuilder._
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ConfirmContactDetailsControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockRegistrationConfirmService = mock[RegistrationConfirmService]
  private val mockRequestSessionData         = mock[RequestSessionData]

  private val mockSubscriptionDetailsReviewController =
    mock[CheckYourDetailsRegisterController]

  private val mockCdsFrontendDataCache      = mock[SessionCache]
  private val mockSubscriptionFlowManager   = mock[SubscriptionFlowManager]
  private val mockOrgTypeLookup             = mock[OrgTypeLookup]
  private val mockTaxEnrolmentsService      = mock[TaxEnrolmentsService]
  private val mockHandleSubscriptionService = mock[HandleSubscriptionService]

  private val confirmContactDetailsView =
    app.injector.instanceOf[confirm_contact_details]

  private val sub01OutcomeProcessingView =
    app.injector.instanceOf[sub01_outcome_processing]

  private val sub01OutcomeRejected =
    app.injector.instanceOf[sub01_outcome_rejected]

  private val controller = new ConfirmContactDetailsController(
    mockAuthAction,
    mockRegistrationConfirmService,
    mockRequestSessionData,
    mockCdsFrontendDataCache,
    mockOrgTypeLookup,
    mockSubscriptionFlowManager,
    mockTaxEnrolmentsService,
    mcc,
    confirmContactDetailsView,
    sub01OutcomeProcessingView,
    sub01OutcomeRejected
  )

  private val mockSubscriptionPage         = mock[SubscriptionPage]
  private val mockSubscriptionStartSession = mock[Session]
  private val mockRequestHeader            = mock[RequestHeader]

  private val mockFlowStart =
    (mockSubscriptionPage, mockSubscriptionStartSession)

  private val mockSub01Outcome = mock[Sub01Outcome]
  private val mockRegDetails   = mock[RegistrationDetails]

  private val testSessionData =
    Map[String, String]("some_session_key" -> "some_session_value")

  private val testSubscriptionStartPageUrl = "some_page_url"

  private val subscriptionDetailsHolder = SubscriptionDetails()

  override def beforeEach {
    reset(
      mockAuthConnector,
      mockRegistrationConfirmService,
      mockRequestSessionData,
      mockSubscriptionDetailsReviewController,
      mockCdsFrontendDataCache,
      mockSubscriptionFlowManager,
      mockOrgTypeLookup,
      mockTaxEnrolmentsService,
      mockHandleSubscriptionService
    )
  }

  "Reviewing the details" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.form(Service.ATaR, Journey.Register)
    )

    "return ok when data has been provided" in {
      mockCacheWithRegistrationDetails(organisationRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirm() { result =>
        status(result) shouldBe OK
      }
    }

    "display registered partnership name when org type is LLP" in {
      mockCacheWithRegistrationDetails(organisationRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(LLP)))

      invokeConfirm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText("//*[@id='content']/div/div/dl/div[2]/dt") shouldBe "Registered partnership name"
      }
    }

    "display registered partnership name when org type is Partnership" in {
      mockCacheWithRegistrationDetails(PartneshipRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText("//*[@id='content']/div/div/dl/div[2]/dt") shouldBe "Registered partnership name"
      }
    }

    "throw illegal state exception when Registration Details are not available" in {
      mockCacheWithRegistrationDetails(limitedLiabilityPartnershipRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenThrow(new IllegalStateException("No Registration details in cache."))

      invokeConfirm() { result =>
        val thrown = intercept[IllegalStateException] {
          await(result)
        }
        thrown.getMessage shouldBe "No Registration details in cache."
      }
    }

    "clear the cache and redirect to Select Organisation Type when org type is empty" in {
      mockCacheWithRegistrationDetails(limitedLiabilityPartnershipRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(None))

      invokeConfirm() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(
          LOCATION
        ) shouldBe uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.OrganisationTypeController
          .form(Service.ATaR, Journey.Register)
          .url
        verify(mockCdsFrontendDataCache).remove(any[HeaderCarrier])
      }
    }

    "display all fields when all are provided from the cache" in {
      mockCacheWithRegistrationDetails(organisationRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          "//*[@id='content']/div/div/dl/div[1]/dt"
        ) shouldBe "Partnership Self Assessment UTR number"

        page.getElementsText(ConfirmPage.fullDetailsXpath) shouldBe strim(
          """123UTRNO orgName Line 1 line 2 line 3 SE28 1AA United Kingdom"""
        )
      }
    }

    "display all fields when all are provided from the cache for sole trader" in {
      mockCacheWithRegistrationDetails(soleTraderRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText("//*[@id='content']/div/div/dl/div[1]/dt") shouldBe "Self Assessment UTR number"

        page.getElementsText(ConfirmPage.fullDetailsXpath) shouldBe strim(
          """123UTRNO John Doe Sole Trader Line 1 line 2 line 3 SE28 1AA United Kingdom"""
        )
      }
    }

    "display all fields when all are provided from the cache for sole trader with nino" in {
      mockCacheWithRegistrationDetails(soleTraderRegistrationDetails.copy(customsId = Some(Nino("QQ123456C"))))
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText("//*[@id='content']/div/div/dl/div[1]/dt") shouldBe "National Insurance number"
        page.getElementsText(ConfirmPage.fullDetailsXpath) shouldBe strim(
          """QQ123456C John Doe Sole Trader Line 1 line 2 line 3 SE28 1AA United Kingdom"""
        )
      }
    }

    "truncate the business address when optional values are missing" in {
      mockCacheWithRegistrationDetails(
        organisationRegistrationDetails
          .withBusinessAddress(Address("line1", None, None, None, None, "ZZ"))
      )
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(ConfirmPage.BusinessAddressXPath) shouldBe
          strim("""
              |123UTRNO orgName
              |line1
              |ZZ
            """)
      }
    }

    "display back link correctly" in {
      mockCacheWithRegistrationDetails(
        organisationRegistrationDetails.withBusinessAddress(
          Address("line1", addressLine2 = None, addressLine3 = None, addressLine4 = None, postalCode = None, "ZZ")
        )
      )
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementAttributeHref(ConfirmPage.backLinkXPath) shouldBe previousPageUrl
      }
    }
  }

  "Selecting Yes" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(Service.ATaR, Journey.Register)
    )

    "redirect to the page defined by subscription flow start when service returns NewSubscription for organisation" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))

      mockCacheWithRegistrationDetails(organisationRegistrationDetails)
      mockNewSubscriptionFromSubscriptionStatus()
      mockSubscriptionFlowStart()
      invokeConfirmContactDetailsWithSelectedOption() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe testSubscriptionStartPageUrl
        testSessionData foreach (
          newSessionValue => result.session(mockRequestHeader).data should contain(newSessionValue)
        )
      }
    }

    "redirect to the page defined by subscription flow start when service returns NewSubscription for individual with selected type" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(
        mockRequestSessionData
          .userSelectedOrganisationType(any[Request[AnyContent]])
      ).thenReturn(Some(CdsOrganisationType.Individual))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))

      mockCacheWithRegistrationDetails(individualRegistrationDetails)
      mockNewSubscriptionFromSubscriptionStatus()
      mockSubscriptionFlowStart()
      invokeConfirmContactDetailsWithSelectedOption() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe testSubscriptionStartPageUrl
        testSessionData foreach (
          newSessionValue => result.session(mockRequestHeader).data should contain(newSessionValue)
        )
      }
    }

    "redirect to the confirm individual type page when service returns NewSubscription for individual without selected type" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))

      mockCacheWithRegistrationDetails(individualRegistrationDetails)
      when(
        mockRequestSessionData
          .userSelectedOrganisationType(any[Request[AnyContent]])
      ).thenReturn(None)
      mockNewSubscriptionFromSubscriptionStatus()
      invokeConfirmContactDetailsWithSelectedOption() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(
          LOCATION
        ) shouldBe uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ConfirmIndividualTypeController
          .form(Service.ATaR, Journey.Register)
          .url
      }
    }

    "redirect to the confirm individual type page when service returns SubscriptionRejected for individual without selected type" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))

      mockCacheWithRegistrationDetails(individualRegistrationDetails)
      when(
        mockRequestSessionData
          .userSelectedOrganisationType(any[Request[AnyContent]])
      ).thenReturn(None)

      when(
        mockRegistrationConfirmService
          .currentSubscriptionStatus(any[HeaderCarrier], any[Request[AnyContent]])
      ).thenReturn(Future.successful(SubscriptionRejected))

      invokeConfirmContactDetailsWithSelectedOption() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(
          LOCATION
        ) shouldBe uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ConfirmIndividualTypeController
          .form(Service.ATaR, Journey.Register)
          .url
      }
    }

    val redirectUrl =
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.ConfirmContactDetailsController
        .processing()
        .url
    val subscriptionStatus = SubscriptionProcessing
    s"redirect to $redirectUrl when subscription status is $subscriptionStatus" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(mockRegistrationConfirmService.currentSubscriptionStatus(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(subscriptionStatus))
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))

      invokeConfirmContactDetailsWithSelectedOption() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe redirectUrl
      }
    }

    "redirect to SignInWithDifferentDetailsController when subscription status is SubscriptionExists and Existing Enrolment Exist" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(mockRegistrationConfirmService.currentSubscriptionStatus(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(SubscriptionExists))
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(mockTaxEnrolmentsService.doesEnrolmentExist(any[SafeId])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(true)

      invokeConfirmContactDetailsWithSelectedOption() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(
          LOCATION
        ) shouldBe uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.SignInWithDifferentDetailsController
          .form(Journey.Register)
          .url
      }
    }

    "redirect to SignInWithDifferentDetailsController when subscription status is SubscriptionExists and Existing Enrolment DOES NOT Exist" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(mockRegistrationConfirmService.currentSubscriptionStatus(any[HeaderCarrier], any[Request[AnyContent]]))
        .thenReturn(Future.successful(SubscriptionExists))
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))
      when(mockTaxEnrolmentsService.doesEnrolmentExist(any[SafeId])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(false)

      invokeConfirmContactDetailsWithSelectedOption() { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(
          LOCATION
        ) shouldBe uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.SubscriptionRecoveryController
          .complete(Service.ATaR, Journey.Register)
          .url
      }
    }

    "allow authenticated users to access the rejected page" in {
      invokeRejectedPageWithAuthenticatedUser() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should startWith(RegistrationRejectedPage.title)
        page.getElementsText(RegistrationRejectedPage.pageHeadingXpath) shouldBe RegistrationRejectedPage.heading
        page.getElementsText(
          RegistrationRejectedPage.processedDateXpath
        ) shouldBe "Application received by HMRC on 22 May 2016"
      }
    }

    "allow authenticated users to access the processing page" in {
      invokeProcessingPageWithAuthenticatedUser() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should startWith(RegistrationProcessingPage.title)
        page.getElementsText(RegistrationProcessingPage.pageHeadingXpath) shouldBe RegistrationProcessingPage.heading
        page.getElementsText(
          RegistrationProcessingPage.processedDateXpath
        ) shouldBe "Application received by HMRC on 22 May 2016"
      }
    }
  }

  "Selecting No" should {
    "clear data and redirect to organisation type page" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(
        mockRegistrationConfirmService
          .clearRegistrationData(any[LoggedInUser])(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))

      invokeConfirmContactDetailsWithSelectedOption(selectedOption = "no") { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(
          LOCATION
        ) shouldBe uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.OrganisationTypeController
          .form(Service.ATaR, Journey.Register)
          .url
      }
    }

    "throw an exception when an unexpected error occurs" in {
      val emulatedFailure = new RuntimeException("Something bad happened")
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(
        mockRegistrationConfirmService
          .clearRegistrationData(any[LoggedInUser])(any[HeaderCarrier])
      ).thenReturn(Future.failed(emulatedFailure))
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))

      val caught = intercept[RuntimeException] {
        invokeConfirmContactDetailsWithSelectedOption(selectedOption = "no") { result =>
          await(result)
        }
      }
      caught shouldBe emulatedFailure
    }
  }

  "Selecting wrong address" should {
    "clear data and redirect to organisation type page" in {
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(subscriptionDetailsHolder))
      when(
        mockRegistrationConfirmService
          .clearRegistrationData(any[LoggedInUser])(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(organisationRegistrationDetails))
      when(
        mockCdsFrontendDataCache
          .saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier])
      ).thenReturn(Future.successful(true))

      invokeConfirmContactDetailsWithSelectedOption(selectedOption = "wrong-address") { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(
          LOCATION
        ) shouldBe uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController
          .createForm(Service.ATaR, Journey.Register)
          .url
      }
    }
  }

  "The Yes No WrongAddress Radio Button " should {

    "display a relevant error if nothing is chosen" in {
      mockCacheWithRegistrationDetails(organisationRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirmContactDetailsWithoutOptionSelected() { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          ConfirmPage.pageLevelErrorSummaryListXPath
        ) shouldBe "Tell us if these are the details you want to use"
        page.getElementsText(
          ConfirmPage.fieldLevelErrorYesNoWrongAddress
        ) shouldBe "Tell us if these are the details you want to use"
      }
    }

    "displays a relevant error if no option is chosen and org type is sole-trader" in {
      mockCacheWithRegistrationDetails(individualRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirmContactDetailsWithoutOptionSelected() { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          ConfirmPage.pageLevelErrorSummaryListXPath
        ) shouldBe "Tell us if these are the details you want to use"
        page.getElementsText(
          ConfirmPage.fieldLevelErrorYesNoWrongAddress
        ) shouldBe "Tell us if these are the details you want to use"
      }
    }

    "display a relevant error if an invalid answer option is selected" in {
      val invalidOption = UUID.randomUUID.toString
      mockCacheWithRegistrationDetails(organisationRegistrationDetails)
      when(
        mockOrgTypeLookup
          .etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(Partnership)))

      invokeConfirmContactDetailsWithSelectedOption(selectedOption = invalidOption) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          ConfirmPage.pageLevelErrorSummaryListXPath
        ) shouldBe "Tell us if these are the details you want to use"
        page.getElementsText(
          ConfirmPage.fieldLevelErrorYesNoWrongAddress
        ) shouldBe "Tell us if these are the details you want to use"
      }
    }
  }

  private def mockCacheWithRegistrationDetails(details: RegistrationDetails): Unit =
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(details)

  private def mockNewSubscriptionFromSubscriptionStatus() =
    when(
      mockRegistrationConfirmService
        .currentSubscriptionStatus(any[HeaderCarrier], any[Request[AnyContent]])
    ).thenReturn(Future.successful(NewSubscription))

  private def mockSubscriptionFlowStart() {
    when(mockSubscriptionPage.url(Service.ATaR)).thenReturn(testSubscriptionStartPageUrl)
    when(mockSubscriptionStartSession.data).thenReturn(testSessionData)
    when(
      mockSubscriptionFlowManager
        .startSubscriptionFlow(any[Service], any[Journey.Value])(any[HeaderCarrier], any[Request[AnyContent]])
    ).thenReturn(Future.successful(mockFlowStart))
  }

  private def invokeConfirm(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .form(Service.ATaR, Journey.Register)
        .apply(SessionBuilder.buildRequestWithSession(userId))
    )
  }

  private def invokeConfirmContactDetailsWithSelectedOption(
    userId: String = defaultUserId,
    selectedOption: String = "yes"
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(Service.ATaR, Journey.Register)
        .apply(
          SessionBuilder.buildRequestWithSessionAndFormValues(userId, Map("yes-no-wrong-address" -> selectedOption))
        )
    )
  }

  private def invokeConfirmContactDetailsWithoutOptionSelected(
    userId: String = defaultUserId
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(Service.ATaR, Journey.Register)
        .apply(SessionBuilder.buildRequestWithSession(userId))
    )
  }

  private def setupMocksForRejectedAndProcessingPages = {
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(mockRegDetails)
    when(mockRegDetails.name).thenReturn("orgName")
    when(mockCdsFrontendDataCache.sub01Outcome(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockSub01Outcome))
    when(mockSub01Outcome.processedDate).thenReturn("22 May 2016")
  }

  def invokeRejectedPageWithAuthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    setupMocksForRejectedAndProcessingPages
    test(controller.rejected.apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  def invokeProcessingPageWithAuthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    setupMocksForRejectedAndProcessingPages
    test(
      controller.processing
        .apply(SessionBuilder.buildRequestWithSession(userId))
    )
  }

}
