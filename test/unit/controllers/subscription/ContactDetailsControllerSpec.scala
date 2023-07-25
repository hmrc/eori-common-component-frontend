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

package unit.controllers.subscription

import common.pages.subscription.SubscriptionContactDetailsPage
import common.pages.subscription.SubscriptionContactDetailsPage._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.{
  ContactDetailsController,
  SubscriptionFlowManager
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.contact_details
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionContactDetailsFormBuilder._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactDetailsControllerSpec extends SubscriptionFlowSpec with BeforeAndAfterEach {

  protected override val mockSubscriptionFlowManager: SubscriptionFlowManager = mock[SubscriptionFlowManager]
  protected override val formId: String                                       = SubscriptionContactDetailsPage.formId

  protected override val submitInCreateModeUrl: String =
    ContactDetailsController.submit(isInReviewMode = false, atarService).url

  protected override val submitInReviewModeUrl: String =
    ContactDetailsController.submit(isInReviewMode = true, atarService).url

  private val mockRequestSessionData  = mock[RequestSessionData]
  private val mockRegistrationDetails = mock[RegistrationDetails](RETURNS_DEEP_STUBS)
  private val mockSubscriptionDetails = mock[SubscriptionDetails](RETURNS_DEEP_STUBS)

  private val mockCdsFrontendDataCache = mock[SessionCache]
  private val mockOrgTypeLookup        = mock[OrgTypeLookup]
  private val contactDetailsView       = instanceOf[contact_details]

  private val controller = new ContactDetailsController(
    mockAuthAction,
    mockSubscriptionBusinessService,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mockOrgTypeLookup,
    mcc,
    contactDetailsView
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(mockSubscriptionBusinessService.cachedContactDetailsModel(any[Request[AnyContent]])).thenReturn(None)
    when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(mockSubscriptionDetails)
    registerSaveContactDetailsMockSuccess()
    mockFunctionWithRegistrationDetails(mockRegistrationDetails)
    setupMockSubscriptionFlowManager(ContactDetailsSubscriptionFlowPageMigrate)
    setupMockSubscriptionFlowManager(ConfirmContactAddressSubscriptionFlowPage)
    when(mockCdsFrontendDataCache.email(any[Request[AnyContent]])).thenReturn(Future.successful(Email))
    when(mockCdsFrontendDataCache.email(any[Request[AnyContent]])).thenReturn(Future.successful(Email))
    when(mockSubscriptionDetailsHolderService.cachedCustomsId(any())).thenReturn(Future.successful(None))
    when(mockSubscriptionDetailsHolderService.cachedNameIdDetails(any())).thenReturn(Future.successful(None))

  }

  override protected def afterEach(): Unit = {
    reset(mockSubscriptionBusinessService)
    reset(mockCdsFrontendDataCache)
    reset(mockSubscriptionFlowManager)
    reset(mockSubscriptionDetailsHolderService)

    super.afterEach()
  }

  "Viewing the create form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(atarService))

    "display back link correctly" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "redirect to next page when ContactDetails is present " in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)

      when(mockOrgTypeLookup.etmpOrgTypeOpt(any[Request[AnyContent]])).thenReturn(Future.successful(Some(NA)))
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
        .thenReturn(Some(CdsOrganisationType("invalid")))
      when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(OrganisationFlow)
      when(mockSubscriptionDetailsHolderService.cachedCustomsId(any())).thenReturn(
        Future.successful(Some(Utr("1111111111k")))
      )
      val result =
        await(controller.createForm(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
      status(result) shouldBe SEE_OTHER
    }

    "display the correct text in the heading and intro" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(headingXPath) shouldBe "Who can we contact?"
        page.getElementsText(
          introXPathSubscribe
        ) shouldBe "We’ll only use this if we need to talk to you about this application."
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }
  }

  "Viewing the review form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewForm(atarService))

    "display relevant data in form fields when subscription details exist in the cache" in {

      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(fullNameFieldXPath) shouldBe FullName
        page.getElementValue(telephoneFieldXPath) shouldBe Telephone
      }
    }

    "not display the number of steps and back link to review page" in {
      showReviewForm()(verifyNoStepsAndBackLinkInReviewMode)
    }

    "display the correct text for the continue button" in {
      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      }
    }
  }

  "submitting the form in Create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, atarService)
    )

    "produce validation error when full name is not submitted" in {
      submitFormInCreateMode(createFormMandatoryFieldsMapSubscribe + (fullNameFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your contact name"
        page.getElementsText(fullNameFieldLevelErrorXPath) shouldBe "Error: Enter your contact name"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when full name more than 70 characters" in {
      submitFormInCreateMode(createFormMandatoryFieldsMapSubscribe + (fullNameFieldName -> oversizedString(70))) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(contentAsString(result))
          page.getElementsText(
            pageLevelErrorSummaryListXPath
          ) shouldBe "The full name can be a maximum of 70 characters"
          page.getElementsText(
            fullNameFieldLevelErrorXPath
          ) shouldBe "Error: The full name can be a maximum of 70 characters"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "Optional telephone number field submits successfully when empty" in {
      submitFormInCreateMode(createFormMandatoryFieldsMapSubscribe + (telephoneFieldName -> "")) { result =>
        status(result) shouldBe SEE_OTHER
        val location = redirectLocation(result)
        location shouldBe Some("next-page-url")
      }
    }

    "produce validation error when Telephone more than 24 characters" in {
      submitFormInCreateMode(createFormMandatoryFieldsMapSubscribe + (telephoneFieldName -> oversizedString(24))) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(contentAsString(result))
          page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The telephone number must be 24 digits or less"
          page.getElementsText(
            telephoneFieldLevelErrorXPath
          ) shouldBe "Error: The telephone number must be 24 digits or less"
          page.getElementsText("title") should startWith("Error: ")
      }
    }

    "produce validation error when Telephone contains invalid characters" in {
      submitFormInCreateMode(createFormMandatoryFieldsMapSubscribe + (telephoneFieldName -> "$£")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Please enter a valid telephone number"
        page.getElementsText(telephoneFieldLevelErrorXPath) shouldBe "Error: Please enter a valid telephone number"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "Allow when Telephone contains plus character" in {
      submitFormInCreateMode(createFormMandatoryFieldsMapSubscribe + (telephoneFieldName -> "+")) { result =>
        status(result) shouldBe SEE_OTHER
      }
    }

    "display page level errors when nothing is entered" in {
      submitFormInCreateMode(createFormAllFieldsEmptyMap) { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe
          "Enter your contact name"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "fail when system fails to create contact details" in {
      val unsupportedException = new UnsupportedOperationException("Emulation of service call failure")

      registerSaveContactDetailsMockFailure(unsupportedException)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(createFormMandatoryFieldsMapSubscribe) { result =>
          await(result)
        }
      }
      caught shouldBe unsupportedException
    }

    "allow resubmission in create mode when details are invalid" in {
      submitFormInCreateMode(createFormMandatoryFieldsMapSubscribe - fullNameFieldName)(verifyFormActionInCreateMode)
    }

    "redirect to check your details page if its feature switched off and not CDS enrolment when details are valid" in {
      when(nextPage.url(any[Service], any[SubscribeJourney])).thenReturn("/check-your-details")
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      val result = controller
        .submit(isInReviewMode = false, atarService)(
          SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, createFormMandatoryFieldsMapSubscribe)
        )

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) should endWith("/check-your-details")
    }

    "redirect to check your details page if its feature switched on but it is not CDS enrolment when details are valid" in {
      when(nextPage.url(any[Service], any[SubscribeJourney])).thenReturn("/check-your-details")
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      val result = controller
        .submit(isInReviewMode = false, atarService)(
          SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, createFormMandatoryFieldsMapSubscribe)
        )

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) should endWith("/check-your-details")
    }
    "redirect to contact address page if its feature switched is on and CDS enrolment when details are valid" in {
      when(nextPage.url(any[Service], any[SubscribeJourney])).thenReturn("/contact-address")
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      val result = controller
        .submit(isInReviewMode = false, cdsService)(
          SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, createFormMandatoryFieldsMapSubscribe)
        )

      status(result) shouldBe SEE_OTHER
      result.header.headers(LOCATION) should endWith("/contact-address")
    }

    "redirect to next page without validating contact address when 'Is this the right contact address' is Yes and country code is GB" in {
      val params = Map(
        fullNameFieldName                 -> FullName,
        emailFieldName                    -> Email,
        telephoneFieldName                -> Telephone,
        useRegisteredAddressFlagFieldName -> "true",
        countryCodeFieldName              -> "GB"
      )
      submitFormInCreateMode(params)(verifyRedirectToNextPageInCreateMode)
    }
  }
  "submitting the form in Review mode" should {
    "redirect to next page when details are valid in review mode" in {
      submitFormInReviewMode(createFormMandatoryFieldsMapSubscribe)(verifyRedirectToNextPageInReviewMode)
    }

  }

  private def mockFunctionWithRegistrationDetails(registrationDetails: RegistrationDetails): Unit =
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[AnyContent]])).thenReturn(registrationDetails)

  private def submitFormInCreateMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(isInReviewMode = false, atarService)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  private def submitFormInReviewMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(isInReviewMode = true, atarService)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  private def showCreateForm(
    subscriptionFlow: SubscriptionFlow = OrganisationFlow,
    orgType: EtmpOrganisationType = CorporateBody
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgTypeOpt(any[Request[AnyContent]])).thenReturn(Some(orgType))

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)

    test(controller.createForm(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def showReviewForm(
    subscriptionFlow: SubscriptionFlow = OrganisationFlow,
    contactDetailsModel: ContactDetailsModel = contactDetailsModel
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)
    when(mockSubscriptionBusinessService.cachedContactDetailsModel(any[Request[AnyContent]]))
      .thenReturn(Some(contactDetailsModel))

    test(controller.reviewForm(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def registerSaveContactDetailsMockSuccess(): Unit =
    when(
      mockSubscriptionDetailsHolderService
        .cacheContactDetails(any[ContactDetailsModel], any[Boolean])(any[Request[AnyContent]])
    ).thenReturn(Future.successful(()))

  private def registerSaveContactDetailsMockFailure(exception: Throwable): Unit =
    when(
      mockSubscriptionDetailsHolderService
        .cacheContactDetails(any[ContactDetailsModel], any[Boolean])(any[Request[AnyContent]])
    ).thenReturn(Future.failed(exception))

}
