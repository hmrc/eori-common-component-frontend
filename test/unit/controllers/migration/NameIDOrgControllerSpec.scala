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

package unit.controllers.migration

import common.pages.migration.NameIdDetailsPage
import common.pages.migration.NameIdDetailsPage._
import common.pages.subscription.SubscriptionContactDetailsPage.pageLevelErrorSummaryListXPath
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor2
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.NameIDOrgController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.NameIdOrganisationDisplayMode.RegisteredCompanyDM
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType.PartnershipId
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.nameUtrOrganisationForm
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{
  DataUnavailableException,
  RequestSessionData,
  SessionCache
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.nameId
import unit.controllers.CdsPage
import unit.controllers.subscription.SubscriptionFlowSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NameIDOrgControllerSpec extends SubscriptionFlowSpec with BeforeAndAfterEach {

  protected override val mockSubscriptionFlowManager: SubscriptionFlowManager = mock[SubscriptionFlowManager]
  protected override val formId: String                                       = NameIdDetailsPage.formId

  protected override val submitInCreateModeUrl: String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.NameIDOrgController
      .submit(isInReviewMode = false, atarService)
      .url

  protected override val submitInReviewModeUrl: String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.NameIDOrgController
      .submit(isInReviewMode = true, atarService)
      .url

  private val mockRequestSessionData   = mock[RequestSessionData]
  private val mockRegistrationDetails  = mock[RegistrationDetails](RETURNS_DEEP_STUBS)
  private val mockCdsFrontendDataCache = mock[SessionCache]

  private val nameIdView = instanceOf[nameId]

  private val controller = new NameIDOrgController(
    mockAuthAction,
    mockSubscriptionBusinessService,
    mockRequestSessionData,
    mockCdsFrontendDataCache,
    mockSubscriptionFlowManager,
    mcc,
    nameIdView,
    mockSubscriptionDetailsHolderService
  )

  private val emulatedFailure           = new UnsupportedOperationException("Emulation of service call failure")
  private val emulatedInvalidURLFailure = DataUnavailableException("Organisation type is not available in cache")

  override def beforeEach(): Unit = {
    reset(mockSubscriptionBusinessService)
    reset(mockCdsFrontendDataCache)
    reset(mockSubscriptionFlowManager)
    reset(mockSubscriptionDetailsHolderService)

    when(mockSubscriptionBusinessService.cachedNameIdOrganisationViewModel(any[Request[_]])).thenReturn(
      Future.successful(None)
    )
    when(mockSubscriptionBusinessService.getCachedNameIdViewModel(any[Request[_]]))
      .thenReturn(Future.successful(NameIdDetailsPage.filledValues))

    when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(Some(CdsOrganisationType.Company))

    registerSaveNameIdDetailsMockSuccess()
    mockFunctionWithRegistrationDetails(mockRegistrationDetails)

    setupMockSubscriptionFlowManager(NameUtrDetailsSubscriptionFlowPage)
  }

  val subscriptionFlows: TableFor2[SubscriptionFlow, String] =
    Table[SubscriptionFlow, String](("Flow name", "Label"), (OrganisationFlow, "Company details"))

  val formModes: TableFor2[String, SubscriptionFlow => (Future[Result] => Any) => Unit] = Table(
    ("formMode", "showFormFunction"),
    ("create", (flow: SubscriptionFlow) => showCreateForm(flow)(_)),
    ("review", (flow: SubscriptionFlow) => showReviewForm(flow)(_))
  )

  forAll(formModes) { (formMode, showFormFunction) =>
    s"The name / id when viewing the $formMode form" should {

      forAll(subscriptionFlows) {
        case (subscriptionFlow, expectedLabel) =>
          s"display appropriate label in subscription flow $subscriptionFlow" in {
            showFormFunction(subscriptionFlow) { result =>
              val page = CdsPage(contentAsString(result))
              page.getElementsText(pageTitleXPath) shouldBe expectedLabel
            }
          }
      }

      "display name / id correctly when all fields are populated" in {
        when(mockSubscriptionBusinessService.cachedNameIdOrganisationViewModel(any[Request[_]]))
          .thenReturn(Future.successful(Some(NameIdDetailsPage.filledValues)))

        showFormFunction(OrganisationFlow) { result =>
          val page         = CdsPage(contentAsString(result))
          val expectedName = s"${NameIdDetailsPage.filledValues.name}"
          val expectedUtr  = s"${NameIdDetailsPage.filledValues.id}"

          page.getElementValue(nameFieldXPath) shouldBe expectedName
          page.getElementValue(utrFieldXPath) shouldBe expectedUtr

        }
      }
    }
  }

  "Viewing the create form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(atarService))

    "display back link correctly" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

    "display the form when the organisation type is partnership" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(
        Some(CdsOrganisationType(PartnershipId))
      )

      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

    "throw DataUnavailableException if there is no session data for organisation type" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(None)
      val caught = intercept[DataUnavailableException] {
        showCreateForm() { result =>
          await(result)
        }
      }
      caught shouldBe emulatedInvalidURLFailure
    }

    "fill fields with details if stored in cache" in {
      when(mockSubscriptionBusinessService.cachedNameIdOrganisationViewModel(any[Request[_]]))
        .thenReturn(Future.successful(Some(NameIdDetailsPage.filledValues)))
      showCreateForm() { result =>
        val page         = CdsPage(contentAsString(result))
        val expectedName = s"${NameIdDetailsPage.filledValues.name}"
        val expectedUtr  = s"${NameIdDetailsPage.filledValues.id}"

        page.getElementValue(nameFieldXPath) shouldBe expectedName
        page.getElementValue(utrFieldXPath) shouldBe expectedUtr
      }
    }

    "leave fields empty if details weren't found in cache" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(nameFieldXPath) shouldBe Symbol("empty")
        page.getElementValue(utrFieldXPath) shouldBe Symbol("empty")
      }
    }

  }

  "Viewing the review form " should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewForm(atarService))

    "display relevant data in form fields when subscription details exist in the cache" in {
      when(mockSubscriptionBusinessService.getCachedNameIdViewModel(any())).thenReturn(
        Future.successful(NameIdDetailsPage.filledValues)
      )

      showReviewForm() { result =>
        val page         = CdsPage(contentAsString(result))
        val expectedName = s"${NameIdDetailsPage.filledValues.name}"
        val expectedUtr  = s"${NameIdDetailsPage.filledValues.id}"

        page.getElementValue(nameFieldXPath) shouldBe expectedName
        page.getElementValue(utrFieldXPath) shouldBe expectedUtr
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

    "throw DataUnavailableException if there is no session data for organisation type" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(None)
      val caught = intercept[DataUnavailableException] {
        showReviewForm() { result =>
          await(result)
        }
      }
      caught shouldBe emulatedInvalidURLFailure
    }
  }

  "submitting the form in Create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, atarService)
    )

    "save the details" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService).cacheNameIdDetails(meq(NameIdDetailsPage.filledValues))(
          any[Request[_]]
        )
      }
    }

    "validation error when full name is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap + (nameFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your registered organisation name"
        page.getElementsText(nameFieldLevelErrorXPath) shouldBe "Error: Enter your registered organisation name"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoMoreInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when full name more than 105 characters" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap + (nameFieldName -> List.fill(106)("D").mkString)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          pageLevelErrorSummaryListXPath
        ) shouldBe "The organisation name must be 105 characters or less"
        page.getElementsText(
          nameFieldLevelErrorXPath
        ) shouldBe "Error: The organisation name must be 105 characters or less"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoMoreInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when ID UTR is not submitted" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap + (utrFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your UTR"
        page.getElementsText(utrFieldLevelErrorXPath) shouldBe "Error: Enter your UTR"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoMoreInteractions(mockSubscriptionBusinessService)
      }
    }

    "display page level errors when nothing is entered" in {
      submitFormInCreateMode(createEmptyFormUtrMap) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          pageLevelErrorSummaryListXPath
        ) shouldBe "Enter your registered organisation name Enter your UTR"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "fail when system fails to create details" in {
      registerSaveNameIdDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(createFormAllFieldsUtrMap) { result =>
          await(result)
        }
      }

      caught shouldBe emulatedFailure
    }

    "throw DataUnavailableException if there is no session data for organisation type" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(None)
      val caught = intercept[DataUnavailableException] {
        submitFormInCreateMode(createEmptyFormUtrMap) { result =>
          await(result)
        }
      }
      caught shouldBe emulatedInvalidURLFailure
    }

    "allow resubmission in create mode when details are invalid" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap - utrFieldName)(verifyFormActionInCreateMode)
    }

    "redirect to next page when details are valid" in {
      submitFormInCreateMode(createFormAllFieldsUtrMap)(verifyRedirectToNextPageInCreateMode)
    }
    "validation error when full name is not submitted for Company" in {

      when(mockRequestSessionData.isCompany(any())).thenReturn(true)
      submitFormInCreateMode(createFormAllFieldsUtrMap + (nameFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your registered company name"
        page.getElementsText(nameFieldLevelErrorXPath) shouldBe "Error: Enter your registered company name"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoMoreInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when full name more than 105 characters for Company" in {

      when(mockRequestSessionData.isCompany(any())).thenReturn(true)
      submitFormInCreateMode(createFormAllFieldsUtrMap + (nameFieldName -> List.fill(106)("D").mkString)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "The company name must be 105 characters or less"
        page.getElementsText(nameFieldLevelErrorXPath) shouldBe "Error: The company name must be 105 characters or less"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoMoreInteractions(mockSubscriptionBusinessService)
      }
    }
    "validation error when full name is not submitted for Partnership" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(
        Some(CdsOrganisationType(PartnershipId))
      )
      when(mockRequestSessionData.isPartnership(any())).thenReturn(true)
      submitFormInCreateMode(createFormAllFieldsUtrMap + (nameFieldName -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your registered partnership name"
        page.getElementsText(nameFieldLevelErrorXPath) shouldBe "Error: Enter your registered partnership name"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoMoreInteractions(mockSubscriptionBusinessService)
      }
    }

    "validation error when full name more than 105 characters for Partnership" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any())).thenReturn(
        Some(CdsOrganisationType(PartnershipId))
      )
      when(mockRequestSessionData.isPartnership(any())).thenReturn(true)
      submitFormInCreateMode(createFormAllFieldsUtrMap + (nameFieldName -> List.fill(106)("D").mkString)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          pageLevelErrorSummaryListXPath
        ) shouldBe "The partnership name must be 105 characters or less"
        page.getElementsText(
          nameFieldLevelErrorXPath
        ) shouldBe "Error: The partnership name must be 105 characters or less"
        page.getElementsText("title") should startWith("Error: ")
        verifyNoMoreInteractions(mockSubscriptionBusinessService)
      }
    }

  }

  "submitting the form in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = true, atarService)
    )

    "allow resubmission in review mode when details are invalid" in {
      submitFormInReviewMode(createFormAllFieldsUtrMap - utrFieldName)(verifyFormSubmitsInReviewMode)
    }

    "redirect to the review page when details are valid" in {
      submitFormInReviewMode(createFormAllFieldsUtrMap)(verifyRedirectToReviewPage())
    }
  }

  "UtrConfiguration returns correct NameIdOrgViewModel" should {

    "for RegExistingEoriLimitedCompanyId" in {
      val cfg = controller.NameIdOrgViewModel("Corporate Body", displayMode = RegisteredCompanyDM)
      cfg.matchingServiceType shouldBe "Corporate Body"
      cfg.displayMode shouldBe "registered-company"
      cfg.isNameAddressRegistrationAvailable shouldBe false
      cfg.form shouldBe nameUtrOrganisationForm
    }

  }

  "invalidOrganisationType returns correct message" should {

    "for wrong-org-type" in {
      val msg = controller.invalidOrganisationType("wrong-org-type")
      msg shouldBe "Invalid organisation type 'wrong-org-type'."

    }
  }

  val createFormAllFieldsUtrMap: Map[String, String] =
    Map(nameFieldName -> "Test Business Name", utrFieldName -> "2108834503")

  val createEmptyFormUtrMap: Map[String, String] = Map(nameFieldName -> "", utrFieldName -> "")

  private def mockFunctionWithRegistrationDetails(registrationDetails: RegistrationDetails): Unit =
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]]))
      .thenReturn(Future.successful(registrationDetails))

  private def submitFormInCreateMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    val result = controller.submit(isInReviewMode = false, atarService)(
      SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
    )
    test(result)
  }

  private def submitFormInReviewMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    val result = controller.submit(isInReviewMode = true, atarService)(
      SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
    )
    test(result)
  }

  private def showCreateForm(
    subscriptionFlow: SubscriptionFlow = OrganisationFlow
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)

    val result =
      controller.createForm(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    test(result)
  }

  private def showReviewForm(
    subscriptionFlow: SubscriptionFlow = OrganisationFlow
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(subscriptionFlow)
    when(mockSubscriptionBusinessService.getCachedNameIdViewModel(any[Request[_]]))
      .thenReturn(Future.successful(NameIdDetailsPage.filledValues))

    val result =
      controller.reviewForm(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    test(result)
  }

  private def registerSaveNameIdDetailsMockSuccess(): Unit =
    when(mockSubscriptionDetailsHolderService.cacheNameIdDetails(any[NameIdOrganisationMatchModel])(any[Request[_]]))
      .thenReturn(Future.successful(()))

  private def registerSaveNameIdDetailsMockFailure(exception: Throwable): Unit =
    when(mockSubscriptionDetailsHolderService.cacheNameIdDetails(any[NameIdOrganisationMatchModel])(any[Request[_]]))
      .thenReturn(Future.failed(exception))

}
