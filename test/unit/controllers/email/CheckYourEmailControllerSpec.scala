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

package unit.controllers.email

import common.pages.emailvericationprocess.CheckYourEmailPage
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.{CheckYourEmailController, routes => emailRoutes}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.GroupId
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.{EmailJourneyService, EmailVerificationService}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.UpdateVerifiedEmailService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.{check_your_email, email_confirmed}
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CheckYourEmailControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val yesNoInputName = "yes-no-answer"
  private val answerYes      = true.toString
  private val answerNo       = false.toString

  private val problemWithSelectionError =
    "Select yes if this is the correct email address"

  private val mockAuthConnector = mock[AuthConnector]
  private val mockAuthAction    = authAction(mockAuthConnector)

  private val mockEmailVerificationService = mock[EmailVerificationService]

  private val mockSave4LaterService          = mock[Save4LaterService]
  private val mockSessionCache               = mock[SessionCache]
  private val mockUpdateVerifiedEmailService = mock[UpdateVerifiedEmailService]
  private val mockEmailJourneyService        = mock[EmailJourneyService]

  private val checkYourEmailView = instanceOf[check_your_email]
  private val emailConfirmedView = instanceOf[email_confirmed]

  private val controller = new CheckYourEmailController(
    mockAuthAction,
    mockSave4LaterService,
    mcc,
    checkYourEmailView,
    emailConfirmedView,
    mockEmailJourneyService
  )

  val email                    = "test@example.com"
  val emailStatus: EmailStatus = EmailStatus(Some(email))

  val internalId                 = "InternalID"
  val jsonValue: JsValue         = Json.toJson(emailStatus)
  val data: Map[String, JsValue] = Map(internalId -> jsonValue)
  val unit: Unit                 = ()

  override def beforeEach(): Unit =
    when(mockSave4LaterService.fetchEmailForService(any(), any(), any())(any()))
      .thenReturn(Future.successful(Some(emailStatus)))

  override def afterEach(): Unit = {
    Mockito.reset(mockSave4LaterService)
    Mockito.reset(mockEmailVerificationService)
    Mockito.reset(mockUpdateVerifiedEmailService)
    Mockito.reset(mockSessionCache)
  }

  "Displaying the Check Your Email Page" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.createForm(atarService, subscribeJourneyShort)
    )

    "display title as 'Check your email address'" in {
      showForm(journey = subscribeJourneyShort) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Is this the email address you want to use?")
      }
    }
  }

  "Submitting the Check Your Email Page" should {

    "call EmailJourneyService.continue if the user selects yes" in {
      when(mockEmailJourneyService.continue(any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(Ok("Some response")))

      submitForm(Map(yesNoInputName -> answerYes), service = atarService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe OK
      }
    }

    "redirect to WhatIsYourEmailController.createForm if the user selects no" in {
      submitForm(Map(yesNoInputName -> answerNo), service = atarService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe SEE_OTHER
          header(LOCATION, result).value should endWith(
            emailRoutes.WhatIsYourEmailController.createForm(atarService, subscribeJourneyShort).url
          )
      }
    }

    "Email Confirmed" should {
      "redirect to SecuritySignOutController when no email in session" in {
        when(mockSave4LaterService.fetchEmailForService(any(), any(), any[GroupId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))
        emailConfirmed(defaultUserId) { result =>
          status(result) shouldBe SEE_OTHER
          header(LOCATION, result).value should endWith(routes.SecuritySignOutController.signOut(atarService).url)
        }
      }

      "display emailConfirmedView when email is not confirmed" in {
        when(mockSave4LaterService.fetchEmailForService(any(), any(), any[GroupId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(Some(EmailStatus(Some(email)))))
        when(mockSave4LaterService.saveEmailForService(any())(any(), any(), any[GroupId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(unit))
        emailConfirmed(defaultUserId) { result =>
          status(result) shouldBe OK
          val page = CdsPage(contentAsString(result))
          page.title() should startWith("You have confirmed your email address")
        }
      }
    }

    "redirect to What is Your Email Address Page on selecting No radio button" in {
      submitForm(Map(yesNoInputName -> answerNo), service = atarService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe SEE_OTHER
          header(LOCATION, result).value should endWith(
            "/customs-enrolment-services/atar/subscribe/autoenrolment/matching/what-is-your-email"
          )
      }
    }

    "display an error message when no option is selected" in {
      submitForm(Map(), service = atarService, journey = subscribeJourneyShort) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(CheckYourEmailPage.pageLevelErrorSummaryListXPath) shouldBe problemWithSelectionError
        page.getElementsText(
          CheckYourEmailPage.fieldLevelErrorYesNoAnswer
        ) shouldBe s"Error: $problemWithSelectionError"
      }
    }
  }

  private def submitForm(
    form: Map[String, String],
    userId: String = defaultUserId,
    service: Service,
    journey: SubscribeJourney
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller.submit(isInReviewMode = false, service, journey)(
      SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
    )
    test(result)
  }

  private def showForm(userId: String = defaultUserId, journey: SubscribeJourney)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .createForm(atarService, journey)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  private def emailConfirmed(userId: String)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .emailConfirmed(atarService, subscribeJourneyShort)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

}
