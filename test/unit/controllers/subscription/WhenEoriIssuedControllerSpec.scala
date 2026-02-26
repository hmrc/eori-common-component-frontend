/*
 * Copyright 2026 HM Revenue & Customs
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

import common.pages.subscription.SubscriptionContactDetailsPage.*
import common.pages.subscription.SubscriptionWhenEoriIssuedPage
import org.mockito.ArgumentMatchers.{eq as meq, *}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks.*
import org.scalatest.prop.TableFor2
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers.*
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.WhenEoriIssuedController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.*
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{DateOfEstablishmentSubscriptionFlowPageMigrate, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.when_eori_issued
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WhenEoriIssuedControllerSpec
  extends SubscriptionFlowTestSupport with BeforeAndAfterEach with SubscriptionFlowCreateModeTestSupport
    with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = SubscriptionWhenEoriIssuedPage.formId

  protected override val submitInCreateModeUrl: String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhenEoriIssuedController
      .submit(isInReviewMode = false, atarService)
      .url

  protected override val submitInReviewModeUrl: String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhenEoriIssuedController
      .submit(isInReviewMode = true, atarService)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]

  private val WhenEoriIssuedView = instanceOf[when_eori_issued]

  private val controller = new WhenEoriIssuedController(
    mockAuthAction,
    mockSubscriptionFlowManager,
    mockSubscriptionBusinessService,
    mockSubscriptionDetailsHolderService,
    mockRequestSessionData,
    mcc,
    WhenEoriIssuedView
  )

  private val WhenEoriIssuedString = "1962-05-01"
  private val WhenEoriIssued = LocalDate.parse(WhenEoriIssuedString)

  private val ValidRequest = Map(
    "when-eori-issued.month" -> WhenEoriIssued.getMonthValue.toString,
    "when-eori-issued.year" -> WhenEoriIssued.getYear.toString
  )

  val existingSubscriptionDetailsHolder: SubscriptionDetails = SubscriptionDetails()

  private val WhenEoriIssuedMissingErrorPage = "Enter the month and year this EORI number was issued"
  private val WhenEoriIssuedMissingErrorField = "Error: Enter the month and year this EORI number was issued"
  private val WhenEoriIssuedInFutureErrorPage = "Month and year this EORI number was issued must be this month or in the past"
  private val WhenEoriIssuedInFutureErrorField = "Error: Month and year this EORI number was issued must be this month or in the past"
  private val WhenEoriIssuedInvalidErrorPage = "Enter a real month and year"
  private val WhenEoriIssuedInvalidErrorField = "Error: Enter a real month and year"

  override protected def beforeEach(): Unit = {
    reset(mockSubscriptionFlowManager)
    reset(mockSubscriptionBusinessService)
    reset(mockSubscriptionDetailsHolderService)
    setupMockSubscriptionFlowManager(DateOfEstablishmentSubscriptionFlowPageMigrate)
  }

  val formModes: TableFor2[String, Map[String, String] => (Future[Result] => Any) => Unit] = Table(
    ("formMode", "submitFormFunction"),
    ("create", (form: Map[String, String]) => submitFormInCreateMode(form) _),
    ("review", (form: Map[String, String]) => submitFormInReviewMode(form) _)
  )

  "Loading the page in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(atarService))

    "display the form" in {
      showCreateForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.elementIsPresent(SubscriptionWhenEoriIssuedPage.monthOfDateFieldXpath) shouldBe true
        page.elementIsPresent(SubscriptionWhenEoriIssuedPage.yearOfDateFieldXpath) shouldBe true
      }
    }

    "set form action url to submit in create mode" in {
      showCreateForm()(verifyFormActionInCreateMode)
    }

    "display the number of 'Back' link according the current subscription flow" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "have all the required input fields without data if not cached previously" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(SubscriptionWhenEoriIssuedPage.monthOfDateFieldXpath) shouldBe Symbol("empty")
        page.getElementValue(SubscriptionWhenEoriIssuedPage.yearOfDateFieldXpath) shouldBe Symbol("empty")
      }
    }

    "have all the required input fields with data if cached previously" in {
      showCreateForm(cachedDate = Some(WhenEoriIssued)) { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(
          SubscriptionWhenEoriIssuedPage.monthOfDateFieldXpath
        ) shouldBe WhenEoriIssued.getMonthValue.toString
        page.getElementValue(
          SubscriptionWhenEoriIssuedPage.yearOfDateFieldXpath
        ) shouldBe WhenEoriIssued.getYear.toString
      }
    }
  }
  "Loading the page in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewForm(atarService))

    "display the form" in {
      showReviewForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.elementIsPresent(SubscriptionWhenEoriIssuedPage.monthOfDateFieldXpath) shouldBe true
        page.elementIsPresent(SubscriptionWhenEoriIssuedPage.yearOfDateFieldXpath) shouldBe true
      }
    }

    "set form action url to submit in review mode" in {
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "not display the 'Back' link to review page" in {
      showReviewForm()(verifyBackLinkInReviewMode)
    }

    "display relevant data in form fields when subscription details exist in the cache" in {
      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(
          SubscriptionWhenEoriIssuedPage.monthOfDateFieldXpath
        ) shouldBe WhenEoriIssued.getMonthValue.toString
        page.getElementValue(
          SubscriptionWhenEoriIssuedPage.yearOfDateFieldXpath
        ) shouldBe WhenEoriIssued.getYear.toString
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      }
    }

  }

  forAll(formModes) { (formMode, submitFormFunction) =>
    s"date of EORI number issued in $formMode mode" should {

      "be mandatory" in {
        submitFormFunction(
          ValidRequest ++ Map(
            "when-eori-issued.month" -> "",
            "when-eori-issued.year" -> ""
          )
        ) { result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(contentAsString(result))
          page.getElementsText(
            SubscriptionWhenEoriIssuedPage.pageLevelErrorSummaryListXPath
          ) shouldBe WhenEoriIssuedMissingErrorPage
          page.getElementsText(
            SubscriptionWhenEoriIssuedPage.whenEoriIssuedErrorXpath
          ) shouldBe WhenEoriIssuedMissingErrorField
        }
      }

      "not be a future date" in {
        val tomorrow = LocalDate.now().plusMonths(1)
        submitFormFunction(
          ValidRequest ++ Map(
            "when-eori-issued.month" -> tomorrow.getMonthValue.toString,
            "when-eori-issued.year" -> tomorrow.getYear.toString
          )
        ) { result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(contentAsString(result))
          page.getElementsText(
            SubscriptionWhenEoriIssuedPage.pageLevelErrorSummaryListXPath
          ) shouldBe WhenEoriIssuedInFutureErrorPage
          page.getElementsText(
            SubscriptionWhenEoriIssuedPage.whenEoriIssuedErrorXpath
          ) shouldBe WhenEoriIssuedInFutureErrorField
        }
      }

      "be a real month and year" in {
        submitFormFunction(
          ValidRequest ++ Map(
            "when-eori-issued.month" -> "0",
            "when-eori-issued.year" -> "2020"
          )
        ) { result =>
          status(result) shouldBe BAD_REQUEST
          val page = CdsPage(contentAsString(result))
          page.getElementsText(
            SubscriptionWhenEoriIssuedPage.pageLevelErrorSummaryListXPath
          ) shouldBe WhenEoriIssuedInvalidErrorPage
          page.getElementsText(
            SubscriptionWhenEoriIssuedPage.whenEoriIssuedErrorXpath
          ) shouldBe WhenEoriIssuedInvalidErrorField
        }
      }
    }

    s"Submitting the form in $formMode mode" should {

      "capture date entered by user" in {
        submitFormFunction(ValidRequest) { result =>
          await(result)
          verify(mockSubscriptionDetailsHolderService).cacheDateEstablished(meq(WhenEoriIssued))(
            any[Request[AnyContent]]
          )
        }
      }
    }
  }

  "Submitting the invalid form in create mode" should {

    "allow resubmission in create mode" in {
      submitFormInCreateMode(ValidRequest - "when-eori-issued.month")(verifyFormActionInCreateMode)
    }
  }

  "Submitting the valid form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, atarService)
    )

    "redirect to next page in subscription flow" in {
      submitFormInCreateMode(ValidRequest)(verifyRedirectToNextPageInCreateMode)
    }
  }

  "Submitting the valid form in review mode" should {

    "redirect to the review page" in {
      submitFormInReviewMode(ValidRequest)(verifyRedirectToReviewPage())
    }
  }

  "Submitting the invalid form in review mode" should {

    "allow resubmission in review mode" in {
      submitFormInReviewMode(ValidRequest - "when-eori-issued.month")(verifyFormSubmitsInReviewMode)
    }
  }

  "WhenEoriIssuedController" should {

    "redirect to Address Lookup page" when {

      "user is during UK Subscribe journey" in {

        when(mockSubscriptionDetailsHolderService.cacheDateEstablished(any())(any()))
          .thenReturn(Future.successful((): Unit))
        when(mockRequestSessionData.isUKJourney(any())).thenReturn(true)

        val session = SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, ValidRequest)
        val result = controller.submit(false, atarService).apply(session)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/customs-enrolment-services/atar/subscribe/address-postcode")
      }
    }
  }

  private def showCreateForm(userId: String = defaultUserId, cachedDate: Option[LocalDate] = None)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockSubscriptionBusinessService.maybeCachedDateEstablished(any[Request[AnyContent]]))
      .thenReturn(Future.successful(cachedDate))

    val result = controller.createForm(atarService).apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  private def showReviewForm(userId: String = defaultUserId)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockSubscriptionBusinessService.getCachedDateEstablished(any[Request[AnyContent]])).thenReturn(
      Future.successful(WhenEoriIssued)
    )

    val result = controller.reviewForm(atarService).apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  private def submitFormInCreateMode(form: Map[String, String])(test: Future[Result] => Any): Unit =
    submitForm(form, isInReviewMode = false)(test)

  private def submitFormInReviewMode(form: Map[String, String])(test: Future[Result] => Any): Unit =
    submitForm(form, isInReviewMode = true)(test)

  private def submitForm(form: Map[String, String], isInReviewMode: Boolean, userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockSubscriptionDetailsHolderService.cacheDateEstablished(any[LocalDate])(any[Request[AnyContent]]))
      .thenReturn(Future.successful(()))
    val result = controller
      .submit(isInReviewMode, atarService)
      .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    test(result)
  }

}
