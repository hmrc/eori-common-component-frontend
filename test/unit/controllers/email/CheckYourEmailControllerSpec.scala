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
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.CheckYourEmailController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.GroupId
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  Error,
  UpdateEmailError,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.{check_your_email, email_confirmed, verify_your_email}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email_error_template
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.YesNoFormBuilder.ValidRequest
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

  private val checkYourEmailView = instanceOf[check_your_email]
  private val emailConfirmedView = instanceOf[email_confirmed]
  private val verifyYourEmail    = instanceOf[verify_your_email]
  private val errorTemplate      = instanceOf[email_error_template]

  private val controller = new CheckYourEmailController(
    mockAuthAction,
    mockSave4LaterService,
    mockSessionCache,
    mcc,
    checkYourEmailView,
    emailConfirmedView,
    verifyYourEmail,
    mockEmailVerificationService,
    mockUpdateVerifiedEmailService,
    errorTemplate
  )

  val email       = "test@example.com"
  val emailStatus = EmailStatus(Some(email))

  val internalId = "InternalID"
  val jsonValue  = Json.toJson(emailStatus)
  val data       = Map(internalId -> jsonValue)
  val unit       = ()

  override def beforeEach: Unit = {
    when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(true)))

    when(mockSave4LaterService.fetchEmailForService(any(), any(), any())(any()))
      .thenReturn(Future.successful(Some(emailStatus)))
  }

  override def afterEach(): Unit =
    Mockito.reset(mockSave4LaterService, mockEmailVerificationService, mockUpdateVerifiedEmailService, mockSessionCache)

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

    "redirect to Verify Your Email Address page for unverified email address" in {
      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(true)))
      submitForm(ValidRequest + (yesNoInputName -> answerYes), service = atarService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(
            "/customs-enrolment-services/atar/subscribe/autoenrolment/matching/verify-your-email"
          )
      }
    }

    "redirect to Are You based in UK for Already verified email" in {
      when(mockSessionCache.eori(any[Request[AnyContent]]))
        .thenReturn(Future.successful(Some("GB123456789")))
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right()))
      when(mockSave4LaterService.saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockSessionCache.saveEmail(any[String])(any[Request[AnyContent]]))
        .thenReturn(Future.successful(true))

      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))

      submitForm(ValidRequest + (yesNoInputName -> answerYes), service = atarService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(
            "/customs-enrolment-services/atar/subscribe/autoenrolment/check-user"
          )
      }
    }

    "redirect to Are You based in UK for Already verified email (Long Journey)" in {
      when(mockSave4LaterService.saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      when(mockSessionCache.saveEmail(any[String])(any[Request[AnyContent]]))
        .thenReturn(Future.successful(true))

      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))

      submitForm(ValidRequest + (yesNoInputName -> answerYes), service = atarService, journey = subscribeJourneyLong) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(
            "/customs-enrolment-services/atar/subscribe/longjourney/check-user"
          )
      }
    }

    "update verified email for CDS Short Journey (Auto-enrolment)" in {
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right()))

      when(mockSessionCache.eori(any[Request[AnyContent]]))
        .thenReturn(Future.successful(Some("GB123456789")))

      when(mockSave4LaterService.saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      when(mockSessionCache.saveEmail(any[String])(any[Request[AnyContent]]))
        .thenReturn(Future.successful(true))

      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))

      submitForm(ValidRequest + (yesNoInputName -> answerYes), service = cdsService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(
            "/customs-enrolment-services/cds/subscribe/autoenrolment/check-user"
          )
      }
      verify(mockUpdateVerifiedEmailService, times(1)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])
    }

    "do not save email when updating email fails" in {
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(Error)))

      when(mockSessionCache.eori(any[Request[AnyContent]]))
        .thenReturn(Future.successful(Some("GB123456789")))

      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))

      the[IllegalArgumentException] thrownBy submitForm(
        ValidRequest + (yesNoInputName -> answerYes),
        service = cdsService,
        journey = subscribeJourneyShort
      ) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(
            "/customs-enrolment-services/cds/subscribe/autoenrolment/check-user"
          )
      }

      verify(mockSave4LaterService, times(0)).saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier])
    }

    "do not save email when updating verified email with retriable failure and display error page" in {
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(UpdateEmailError)))

      when(mockSessionCache.eori(any[Request[AnyContent]]))
        .thenReturn(Future.successful(Some("GB123456789")))

      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))

      submitForm(ValidRequest + (yesNoInputName -> answerYes), service = cdsService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe OK
      }

      verify(mockSave4LaterService, times(0)).saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier])
    }

    "do not update verified email for Long Journey" in {

      when(mockSave4LaterService.saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      when(mockSessionCache.saveEmail(any[String])(any[Request[AnyContent]]))
        .thenReturn(Future.successful(true))

      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))

      submitForm(ValidRequest + (yesNoInputName -> answerYes), service = atarService, journey = subscribeJourneyLong) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(
            "/customs-enrolment-services/atar/subscribe/longjourney/check-user"
          )
      }
      verify(mockUpdateVerifiedEmailService, times(0)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])
    }

    "do not update verified email for non-CDS Short Journey" in {
      when(mockSave4LaterService.saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))

      when(mockSessionCache.saveEmail(any[String])(any[Request[AnyContent]]))
        .thenReturn(Future.successful(true))

      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(false)))

      submitForm(ValidRequest + (yesNoInputName -> answerYes), service = atarService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(
            "/customs-enrolment-services/atar/subscribe/autoenrolment/check-user"
          )
      }
      verify(mockUpdateVerifiedEmailService, times(0)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])
    }

    "throw  IllegalStateException when downstream CreateEmailVerificationRequest Fails" in {
      when(mockEmailVerificationService.createEmailVerificationRequest(any[String], any[String])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))

      the[IllegalStateException] thrownBy {
        submitForm(
          ValidRequest + (yesNoInputName -> answerYes),
          service = atarService,
          journey = subscribeJourneyShort
        ) {
          result =>
            status(result) shouldBe SEE_OTHER
        }
      } should have message "CreateEmailVerificationRequest Failed"

    }

    "Email Confirmed" should {
      "redirect to SecuritySignOutController when no email in session" in {
        when(mockSave4LaterService.fetchEmailForService(any(), any(), any[GroupId])(any[HeaderCarrier]))
          .thenReturn(Future.successful(None))
        emailConfirmed(defaultUserId) { result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(routes.SecuritySignOutController.signOut(atarService).url)
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
      submitForm(ValidRequest + (yesNoInputName -> answerNo), service = atarService, journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers("Location") should endWith(
            "/customs-enrolment-services/atar/subscribe/autoenrolment/matching/what-is-your-email"
          )
      }
    }

    "display an error message when no option is selected" in {
      submitForm(ValidRequest - yesNoInputName, service = atarService, journey = subscribeJourneyShort) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(CheckYourEmailPage.pageLevelErrorSummaryListXPath) shouldBe problemWithSelectionError
        page.getElementsText(
          CheckYourEmailPage.fieldLevelErrorYesNoAnswer
        ) shouldBe s"Error: $problemWithSelectionError"
      }
    }
  }

  "Redirecting to Verify Your Email Address Page" should {
    "display title as 'Confirm your email address'" in {
      verifyEmailViewForm(journey = subscribeJourneyShort) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Confirm your email address")
      }
    }
  }

  "Pressing Save and continue on the Email Confirmation page" should {
    "redirect to HasExistingEoriController.enrol for the short journey" in {
      acceptEmailConfirmation(journey = subscribeJourneyShort) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith(
          "/customs-enrolment-services/atar/subscribe/check-existing-eori"
        )
      }
    }

    "redirect to WhatIsYourEoriController.createForm for the long journey" in {
      acceptEmailConfirmation(journey = subscribeJourneyLong) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith(
          "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori"
        )
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

  private def verifyEmailViewForm(userId: String = defaultUserId, journey: SubscribeJourney)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .verifyEmailView(atarService, journey)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

  private def acceptEmailConfirmation(userId: String = defaultUserId, journey: SubscribeJourney)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .acceptConfirmation(atarService, journey)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

}
