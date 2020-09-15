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

package unit.controllers.email

import common.pages.emailvericationprocess.CheckYourEmailPage
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.CheckYourEmailController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.InternalId
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.{check_your_email, email_confirmed, verify_your_email}
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.YesNoFormBuilder.ValidRequest

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CheckYourEmailControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val yesNoInputName = "yes-no-answer"
  private val answerYes      = true.toString
  private val answerNo       = false.toString

  private val problemWithSelectionError =
    "Tell us if this is the correct email address"

  private val mockAuthConnector = mock[AuthConnector]

  private val mockEmailVerificationService = mock[EmailVerificationService]

  private val mockSave4LaterService = mock[Save4LaterService]
  private val mockSessionCache      = mock[SessionCache]

  private val checkYourEmailView = app.injector.instanceOf[check_your_email]
  private val emailConfirmedView = app.injector.instanceOf[email_confirmed]
  private val verifyYourEmail    = app.injector.instanceOf[verify_your_email]

  private val controller = new CheckYourEmailController(
    app,
    mockAuthConnector,
    mockSave4LaterService,
    mockSessionCache,
    mcc,
    checkYourEmailView,
    emailConfirmedView,
    verifyYourEmail,
    mockEmailVerificationService
  )

  val email       = "test@example.com"
  val emailStatus = EmailStatus(email)

  val internalId = "InternalID"
  val jsonValue  = Json.toJson(emailStatus)
  val data       = Map(internalId -> jsonValue)
  val unit       = ()

  override def beforeEach: Unit = {
    when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(emailStatus)))

    when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(true)))
  }

  "Displaying the Check Your Email Page" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(Journey.Subscribe))

    "display title as 'Check your email address'" in {
      showForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Check your email address")
      }
    }
  }

  "Submitting the Check Your Email Page" should {

    "redirect to Verify Your Email Address page for unverified email address" in {
      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(true)))
      submitForm(ValidRequest + (yesNoInputName -> answerYes), journey = Journey.Subscribe) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith(
          "/customs-enrolment-services/subscribe/matching/verify-your-email"
        )
      }
    }

    "redirect to Are You based in UK for Already verified email" in {
      when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = true))))
      when(
        mockSave4LaterService
          .saveEmail(any[InternalId], any[EmailStatus])(any[HeaderCarrier])
      ).thenReturn(Future.successful(unit))
      when(mockSessionCache.saveEmail(any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))

      submitForm(ValidRequest + (yesNoInputName -> answerYes), journey = Journey.Subscribe) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("/customs-enrolment-services/subscribe/are-you-based-in-uk")
      }
    }

    "throw  IllegalStateException when downstream CreateEmailVerificationRequest Fails" in {
      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      the[IllegalStateException] thrownBy {
        submitForm(ValidRequest + (yesNoInputName -> answerYes), journey = Journey.Subscribe) { result =>
          status(result) shouldBe SEE_OTHER
        }
      } should have message "CreateEmailVerificationRequest Failed"
    }

    "redirect to What is Your Email Address Page on selecting No radio button" in {
      submitForm(ValidRequest + (yesNoInputName -> answerNo), journey = Journey.Subscribe) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith(
          "/customs-enrolment-services/subscribe/matching/what-is-your-email"
        )
      }
    }

    "display an error message when no option is selected" in {
      submitForm(ValidRequest - yesNoInputName, journey = Journey.Subscribe) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(CheckYourEmailPage.pageLevelErrorSummaryListXPath) shouldBe problemWithSelectionError
        page.getElementsText(CheckYourEmailPage.fieldLevelErrorYesNoAnswer) shouldBe problemWithSelectionError
      }
    }
  }

  "Redirecting to Verify Your Email Address Page" should {
    "display title as 'Verify your email address'" in {
      verifyEmailViewForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("Verify your email address")
      }
    }
  }

  private def submitForm(form: Map[String, String], userId: String = defaultUserId, journey: Journey.Value)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller.submit(isInReviewMode = false, journey)(
      SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
    )
    test(result)
  }

  private def showForm(userId: String = defaultUserId, journey: Journey.Value)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .createForm(journey)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  private def verifyEmailViewForm(userId: String = defaultUserId, journey: Journey.Value)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .verifyEmailView(journey)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

}
