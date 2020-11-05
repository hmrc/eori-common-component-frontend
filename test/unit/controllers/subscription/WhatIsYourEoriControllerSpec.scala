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

package unit.controllers.subscription

import common.pages.subscription.{EoriNumberPage, SubscriptionAmendCompanyDetailsPage, SubscriptionContactDetailsPage}
import common.support.testdata.subscription.BusinessDatesOrganisationTypeTables
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.WhatIsYourEoriController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.EoriNumberSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{CdsOrganisationType, RegistrationDetailsIndividual}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.what_is_your_eori
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionAmendCompanyDetailsFormBuilder._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class WhatIsYourEoriControllerSpec
    extends SubscriptionFlowCreateModeTestSupport with BusinessDatesOrganisationTypeTables with BeforeAndAfterEach
    with SubscriptionFlowReviewModeTestSupport {
  protected override val formId: String = EoriNumberPage.formId

  protected override def submitInCreateModeUrl: String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhatIsYourEoriController
      .submit(isInReviewMode = false, atarService, Journey.Subscribe)
      .url

  protected override def submitInReviewModeUrl: String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhatIsYourEoriController
      .submit(isInReviewMode = true, atarService, Journey.Subscribe)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val whatIsYourEoriView     = instanceOf[what_is_your_eori]

  private val controller = new WhatIsYourEoriController(
    mockAuthAction,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mcc,
    whatIsYourEoriView,
    mockRequestSessionData
  )

  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")
  val enterAGbEoriPage        = "Enter an EORI number that starts with GB"
  val enterAGbEoriField       = "Error: Enter an EORI number that starts with GB"

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockSubscriptionFlowManager,
      mockRequestSessionData,
      mockSubscriptionDetailsHolderService
    )
    when(mockSubscriptionBusinessService.cachedEoriNumber(any[HeaderCarrier])).thenReturn(None)
    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(EoriNumberSubscriptionFlowPage)
  }

  "Subscription What Is Your Eori Number form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.createForm(atarService, Journey.Subscribe)
    )

    "display title as 'What is your GB EORI number?'" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("What is your GB EORI number?")
      }
    }

    "submit in create mode" in {
      showCreateForm(journey = Journey.Subscribe)(verifyFormActionInCreateMode)
    }

    "display the back link" in {
      showCreateForm(journey = Journey.Subscribe)(verifyBackLinkInCreateModeSubscribe)
    }

    "display the back link for subscribe user journey" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        verifyBackLinkIn(result)
      }
    }

    "have Eori Number input field without data if not cached previously" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(contentAsString(result))
        verifyEoriNumberFieldExistsWithNoData(page)
      }
    }

    "have Eori Number input field prepopulated if cached previously" in {
      when(mockSubscriptionBusinessService.cachedEoriNumber(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(EoriNumber)))
      showCreateForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(contentAsString(result))
        verifyEoriNumberFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(EoriNumberPage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

  }

  "Subscription Eori Number form in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.reviewForm(atarService, Journey.Subscribe)
    )

    "display title as 'What is your GB EORI number'" in {
      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("What is your GB EORI number?")
      }
    }

    "submit in review mode" in {
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "retrieve the cached data" in {
      showReviewForm() { result =>
        CdsPage(contentAsString(result))
        verify(mockSubscriptionBusinessService).getCachedEoriNumber(any[HeaderCarrier])
      }
    }

    "have all the required input fields without data" in {
      showReviewForm(EoriNumber) { result =>
        val page = CdsPage(contentAsString(result))
        verifyEoriNumberFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(EoriNumberPage.continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      }
    }
  }

  "submitting the form with all mandatory fields filled when in create mode for all organisation types" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, atarService, Journey.Subscribe)
    )

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(mandatoryFieldsMap) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "redirect to next screen" in {
      submitFormInCreateMode(mandatoryFieldsMap)(verifyRedirectToNextPageIn(_)("next-page-url"))
    }

    "redirect to next screen when selectedOrganisationType is not set" in {
      RegistrationDetailsIndividual
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)

      submitFormInCreateMode(mandatoryFieldsMap)(verifyRedirectToNextPageIn(_)("next-page-url"))
    }

    "redirect to next screen when unmatched journey is  set" in {
      when(mockSubscriptionBusinessService.cachedEoriNumber(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some("EORINUMBER")))
      when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
      submitFormInCreateMode(mandatoryFieldsMap)(verifyRedirectToNextPageIn(_)("next-page-url"))
    }

  }

  "submitting the form with all mandatory fields filled when in review mode for all organisation types" should {

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInReviewMode(populatedEoriNumberFieldsMap) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "redirect to review screen" in {
      submitFormInReviewMode(populatedEoriNumberFieldsMap)(verifyRedirectToReviewPage(Journey.Subscribe))
    }

    "redirect to review screen for unmatched user" in {
      when(mockRequestSessionData.mayBeUnMatchedUser(any[Request[AnyContent]])).thenReturn(Some("true"))
      submitFormInReviewMode(populatedEoriNumberFieldsMap)(verifyRedirectToReviewPage(Journey.Subscribe))
    }
  }

  "Submitting in Create Mode when entries are invalid" should {

    "allow resubmission in create mode" in {
      submitFormInCreateMode(unpopulatedEoriNumberFieldsMap)(verifyFormActionInCreateMode)
    }
  }

  "Submitting in Review Mode when entries are invalid" should {

    "allow resubmission in review mode" in {
      submitFormInReviewMode(unpopulatedEoriNumberFieldsMap)(verifyFormSubmitsInReviewMode)
    }
  }

  "eori number" should {

    "be mandatory" in {
      submitFormInCreateMode(unpopulatedEoriNumberFieldsMap) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe "Enter your EORI number"
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.eoriNumberFieldLevelErrorXpath
        ) shouldBe "Error: Enter your EORI number"
      }
    }

    "have a maximum of 17 characters" in {
      submitFormInCreateMode(Map("eori-number" -> "GB3456789012345678")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe "The EORI number must be 17 characters or less"
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.eoriNumberFieldLevelErrorXpath
        ) shouldBe "Error: The EORI number must be 17 characters or less"
      }
    }

    "be of the correct format" in {
      val enterAValidEori = "Enter an EORI number in the right format"
      submitFormInCreateMode(Map("eori-number" -> "GBX45678901234")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe enterAValidEori
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.eoriNumberFieldLevelErrorXpath
        ) shouldBe s"Error: $enterAValidEori"

      }
    }

    "reject none GB EORI number" in {
      submitFormInCreateMode(Map("eori-number" -> "FR145678901234")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe enterAGbEoriPage
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.eoriNumberFieldLevelErrorXpath
        ) shouldBe enterAGbEoriField

      }
    }
    "should reject lowercase gb in EORI number" in {
      submitFormInCreateMode(Map("eori-number" -> "gb145678901234")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe enterAGbEoriPage
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.eoriNumberFieldLevelErrorXpath
        ) shouldBe enterAGbEoriField

      }
    }
  }

  private def submitFormInCreateMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    userSelectedOrgType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(userSelectedOrgType)

    test(
      controller.submit(isInReviewMode = false, atarService, Journey.Subscribe)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInReviewMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    userSelectedOrgType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(userSelectedOrgType)

    test(
      controller.submit(isInReviewMode = true, atarService, Journey.Subscribe)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def registerSaveDetailsMockSuccess() {
    when(mockSubscriptionDetailsHolderService.cacheEoriNumber(anyString())(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
  }

  private def registerSaveDetailsMockFailure(exception: Throwable) {
    when(mockSubscriptionDetailsHolderService.cacheEoriNumber(anyString)(any[HeaderCarrier]))
      .thenReturn(Future.failed(exception))
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None,
    journey: Journey.Value
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(userSelectedOrganisationType)

    test(controller.createForm(atarService, journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(
    dataToEdit: String = EoriNumber /* TODO */,
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(userSelectedOrganisationType)
    when(mockSubscriptionBusinessService.getCachedEoriNumber(any[HeaderCarrier])).thenReturn(dataToEdit)

    test(controller.reviewForm(atarService, Journey.Subscribe).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyEoriNumberFieldExistsAndPopulatedCorrectly(page: CdsPage): Unit =
    page.getElementValueForLabel(SubscriptionAmendCompanyDetailsPage.eoriNumberLabelXpath) should be(EoriNumber)

  private def verifyEoriNumberFieldExistsWithNoData(page: CdsPage): Unit =
    page.getElementValueForLabel(SubscriptionAmendCompanyDetailsPage.eoriNumberLabelXpath) shouldBe ""

  private def verifyBackLinkIn(result: Result) = {
    val page = CdsPage(contentAsString(result))
    page.getElementAttributeHref(SubscriptionContactDetailsPage.backLinkXPath) shouldBe previousPageUrl
  }

  private def verifyRedirectToNextPageIn(result: Result)(linkToVerify: String) = {
    status(result) shouldBe SEE_OTHER
    result.header.headers(LOCATION) should endWith(linkToVerify)
  }

}
