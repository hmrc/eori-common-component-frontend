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

import common.pages.subscription.{ShortNamePage, SubscriptionAmendCompanyDetailsPage}
import common.support.testdata.subscription.BusinessDatesOrganisationTypeTables
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.BusinessShortNameController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{
  BusinessShortName,
  BusinessShortNameSubscriptionFlowPage
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.business_short_name
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionAmendCompanyDetailsFormBuilder._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class BusinessShortNameControllerSpec
    extends SubscriptionFlowCreateModeTestSupport with BusinessDatesOrganisationTypeTables with BeforeAndAfterEach
    with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = ShortNamePage.formId

  protected override def submitInCreateModeUrl: String =
    BusinessShortNameController.submit(isInReviewMode = false, Service.ATaR, Journey.Register).url

  protected override def submitInReviewModeUrl: String =
    BusinessShortNameController.submit(isInReviewMode = true, Service.ATaR, Journey.Register).url

  private val mockOrgTypeLookup  = mock[OrgTypeLookup]
  private val mockRequestSession = mock[RequestSessionData]
  private val businessShortName  = app.injector.instanceOf[business_short_name]

  val allFieldsMap = Map("use-short-name" -> withShortName, "short-name" -> ShortName)

  val allShortNameFieldsAsShortName = BusinessShortName(allShortNameFields.shortName)

  private val controller = new BusinessShortNameController(
    app,
    mockAuthConnector,
    mockSubscriptionBusinessService,
    mockSubscriptionDetailsHolderService,
    mockSubscriptionFlowManager,
    mockRequestSession,
    mcc,
    businessShortName,
    mockOrgTypeLookup
  )

  private val emulatedFailure              = new UnsupportedOperationException("Emulation of service call failure")
  private val useShortNameError            = "Tell us if your organisation uses a shortened name"
  private val partnershipUseShortNameError = "Tell us if your partnership uses a shortened name"
  private val shortNameError               = "Enter your organisation's shortened name"
  private val partnershipShortNameError    = "Enter your partnership's shortened name"

  override def beforeEach: Unit = {
    reset(
      mockSubscriptionBusinessService,
      mockSubscriptionFlowManager,
      mockOrgTypeLookup,
      mockSubscriptionDetailsHolderService
    )
    when(mockSubscriptionBusinessService.companyShortName(any[HeaderCarrier])).thenReturn(None)
    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(BusinessShortNameSubscriptionFlowPage)
  }

  "Displaying the form in create mode" should {
    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.createForm(Service.ATaR, Journey.Register)
    )

    "display title as 'Does your organisation use a shortened name?' for non partnership org type" in {
      showCreateForm(orgType = CorporateBody) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Does your organisation use a shortened name?")
      }
    }

    "display heading as 'Does your organisation use a shortened name?' for non partnership org type" in {
      showCreateForm(orgType = CorporateBody) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(ShortNamePage.headingXpath) shouldBe "Does your organisation use a shortened name?"
      }
    }

    "display title as 'Does your partnership use a shortened name?' for org type of Partnership" in {
      showCreateForm(orgType = Partnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Does your partnership use a shortened name?")
      }
    }

    "display heading as 'Does your partnership use a shortened name?' for org type of Partnership" in {
      showCreateForm(orgType = Partnership) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(ShortNamePage.headingXpath) shouldBe "Does your partnership use a shortened name?"
      }
    }

    "display title as 'Does your partnership use a shortened name?' for org type of Limited Liability Partnership" in {
      showCreateForm(orgType = LLP) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Does your partnership use a shortened name?")
      }
    }

    "display heading as 'Does your partnership use a shortened name?' for org type of Limited Liability Partnership" in {
      showCreateForm(orgType = LLP) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementText(ShortNamePage.headingXpath) shouldBe "Does your partnership use a shortened name?"
      }
    }

    "submit to correct url " in {
      showCreateForm()(verifyFormActionInCreateMode)
    }

    "display correct back link" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "have all the required input fields without data if not cached previously" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        verifyShortNameFieldExistWithNoData(page)
      }
    }

    "short name input field is prepopulated with data retrieved from cache" in {
      when(mockSubscriptionBusinessService.companyShortName(any[HeaderCarrier]))
        .thenReturn(Some(allShortNameFieldsAsShortName))
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        verifyShortNameFieldExistAndPopulatedCorrectly(page, allShortNameFieldsAsShortName)
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(ShortNamePage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

  }

  "Displaying the form in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.reviewForm(Service.ATaR, Journey.Register)
    )

    "display title as 'Does your organisation use a shortened name?'" in {
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Does your organisation use a shortened name?")
      }
    }

    "submit to correct url " in {
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "display the number of steps and back link" in {
      showReviewForm()(verifyNoStepsAndBackLinkInReviewMode)
    }

    "have all the required input fields with data" in {
      showReviewForm(allShortNameFieldsAsShortName) { result =>
        val page = CdsPage(bodyOf(result))
        verifyShortNameFieldExistAndPopulatedCorrectly(page, allShortNameFieldsAsShortName)
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm() { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementValue(ShortNamePage.continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      }
    }
  }

  "Submitting in Create Mode with all mandatory fields filled for all organisation types" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, Service.ATaR, Journey.Register)
    )

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateMode(mandatoryShortNameFieldsMap) { result =>
          await(result)
        }
      }
      caught shouldBe emulatedFailure
    }

    "redirect to next screen" in {
      submitFormInCreateMode(mandatoryShortNameFieldsMap)(verifyRedirectToNextPageInCreateMode)
    }

    "not save the short name if the user has entered it but then answered that a short name is not used" in {
      submitFormInCreateMode(
        mandatoryShortNameFieldsMap + ("use-short-name" -> withoutShortName, "short-name" -> ShortName)
      ) { result =>
        await(result)
        verify(mockSubscriptionDetailsHolderService).cacheCompanyShortName(meq(mandatoryShortNameFieldsAsShortName))(
          any[HeaderCarrier]
        )
      }
    }
  }

  "Submitting in Review Mode with all mandatory fields filled for all organisation types" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.submit(isInReviewMode = true, Service.ATaR, Journey.Register)
    )

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInReviewMode(mandatoryShortNameFieldsMap) { result =>
          await(result)
        }
      }
      caught shouldBe emulatedFailure
    }

    "redirect to review screen" in {
      submitFormInReviewMode(mandatoryShortNameFieldsMap)(verifyRedirectToReviewPage(Journey.Register))
    }
  }

  "Submitting in Create Mode when entries are invalid" should {

    "allow resubmission in create mode" in {
      submitFormInCreateMode(allShortNameFieldsMap - "short-name")(verifyFormActionInCreateMode)
    }
  }

  "Submitting in Review Mode when entries are invalid" should {

    "allow resubmission in review mode" in {
      submitFormInReviewMode(allShortNameFieldsMap - "short-name")(verifyFormSubmitsInReviewMode)
    }
  }

  "page level error summary" should {

    "display errors in the same order as the fields appear on the page when 'use short name' is not answered" in {
      submitFormInCreateMode(emptyShortNameFieldsMap) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe useShortNameError
      }
    }

    "display errors in the same order as the fields appear on the page when 'use short name' is answered yes" in {
      submitFormInCreateMode(emptyShortNameFieldsMap + ("use-short-name" -> withShortName)) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldBe shortNameError
      }
    }

    "display partnership specific errors when 'use short name' is not answered" in {
      when(mockRequestSession.isPartnership(any())).thenReturn(true)
      submitFormInCreateMode(emptyShortNameFieldsMap) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe partnershipUseShortNameError
      }
    }

    "display partnership specific errors when 'use short name' is answered yes" in {
      when(mockRequestSession.isPartnership(any())).thenReturn(true)
      submitFormInCreateMode(emptyShortNameFieldsMap + ("use-short-name" -> withShortName)) { result =>
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe partnershipShortNameError
      }
    }
  }

  "'does your company use a shortened name' question" should {

    "be mandatory" in {
      when(mockRequestSession.isPartnership(any())).thenReturn(false)

      submitFormInCreateMode(emptyShortNameFieldsMap) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe useShortNameError
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.useShortNameFieldLevelErrorXpath
        ) shouldBe useShortNameError
      }
    }

    val values = Table(
      ("value", "state", "response"),
      ("true", "valid", SEE_OTHER),
      ("false", "valid", SEE_OTHER),
      ("anything else", "invalid", BAD_REQUEST)
    )

    forAll(values) { (value, state, response) =>
      s"be $state when value is $value" in {
        submitFormInCreateMode(allFieldsMap + ("use-short-name" -> value)) { result =>
          status(result) shouldBe response
        }
      }
    }
  }

  "short name" should {

    "can be blank when 'does your company use a shortened name' is answered no" in {
      submitFormInCreateMode(allShortNameFieldsMap + ("use-short-name" -> withoutShortName, "short-name" -> "")) {
        result =>
          status(result) shouldBe SEE_OTHER
          val page = CdsPage(bodyOf(result))
          page.getElementsText(SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath) shouldEqual ""
          page.getElementsText(SubscriptionAmendCompanyDetailsPage.shortNameFieldLevelErrorXpath) shouldEqual ""
      }
    }

    "be mandatory when 'does your company use a shortened name' is answered yes" in {
      when(mockRequestSession.isPartnership(any())).thenReturn(false)

      submitFormInCreateMode(allShortNameFieldsMap + ("use-short-name" -> withShortName, "short-name" -> "")) {
        result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(bodyOf(result))
          page.getElementsText(
            SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
          ) shouldBe shortNameError
          page.getElementsText(
            SubscriptionAmendCompanyDetailsPage.shortNameFieldLevelErrorXpath
          ) shouldBe shortNameError
      }
    }

    "be restricted to 70 characters" in {
      submitFormInCreateMode(allShortNameFieldsMap + ("short-name" -> oversizedString(70))) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.pageLevelErrorSummaryListXPath
        ) shouldBe "The shortened name must be 70 characters or less"
        page.getElementsText(
          SubscriptionAmendCompanyDetailsPage.shortNameFieldLevelErrorXpath
        ) shouldBe "The shortened name must be 70 characters or less"
      }
    }
  }

  private def submitFormInCreateMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(orgType))

    test(
      controller.submit(isInReviewMode = false, Service.ATaR, Journey.Register)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInReviewMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(orgType))

    test(
      controller.submit(isInReviewMode = true, Service.ATaR, Journey.Register)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def registerSaveDetailsMockSuccess() {
    when(mockSubscriptionDetailsHolderService.cacheCompanyShortName(any[BusinessShortName])(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
  }

  private def registerSaveDetailsMockFailure(exception: Throwable) {
    when(mockSubscriptionDetailsHolderService.cacheCompanyShortName(any[BusinessShortName])(any[HeaderCarrier]))
      .thenReturn(Future.failed(exception))
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody,
    journey: Journey.Value = Journey.Register
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(orgType))

    test(controller.createForm(Service.ATaR, journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(
    dataToEdit: BusinessShortName = mandatoryShortNameFieldsAsShortName,
    userId: String = defaultUserId,
    orgType: EtmpOrganisationType = CorporateBody,
    journey: Journey.Value = Journey.Register
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockOrgTypeLookup.etmpOrgType(any[Request[AnyContent]], any[HeaderCarrier])).thenReturn(Some(orgType))
    when(mockSubscriptionBusinessService.getCachedCompanyShortName(any[HeaderCarrier])).thenReturn(dataToEdit)

    test(controller.reviewForm(Service.ATaR, journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyShortNameFieldExistAndPopulatedCorrectly(page: CdsPage, testData: BusinessShortName): Unit =
    Some(
      page.getElementValueForLabel(SubscriptionAmendCompanyDetailsPage.shortNameLabelXpath)
    ) shouldBe testData.shortName

  private def verifyShortNameFieldExistWithNoData(page: CdsPage): Unit =
    page.getElementValueForLabel(ShortNamePage.shortNameLabelXpath) shouldBe empty

}
