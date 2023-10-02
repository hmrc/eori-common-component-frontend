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

package unit.controllers

import cats.data.EitherT
import common.pages.matching.AddressPageFactoring
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.ResponseError
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.EmailController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.email.{EmailVerificationStatus, ResponseWithURI}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.{EmailJourneyService, EmailVerificationService}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  Error,
  SubscriptionProcessing,
  SubscriptionStatusService,
  UpdateEmailError,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.{
  ExistingEoriService,
  Save4LaterService,
  UserGroupIdSubscriptionStatusCheckService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{
  email_error_template,
  enrolment_pending_against_group_id,
  enrolment_pending_for_user,
  error_template
}
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailControllerSpec
    extends ControllerSpec with AddressPageFactoring with MockitoSugar with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector                                          = mock[AuthConnector]
  private val mockAuthAction                                             = authAction(mockAuthConnector)
  private val mockEmailVerificationService: EmailVerificationService     = mock[EmailVerificationService]
  private val mockSave4LaterService: Save4LaterService                   = mock[Save4LaterService]
  private val mockSessionCache: SessionCache                             = mock[SessionCache]
  private val mockSubscriptionStatusService                              = mock[SubscriptionStatusService]
  private val mockUpdateVerifiedEmailService: UpdateVerifiedEmailService = mock[UpdateVerifiedEmailService]
  private val mockExistingEoriService                                    = mock[ExistingEoriService]
  private val enrolmentPendingAgainstGroupIdView                         = instanceOf[enrolment_pending_against_group_id]
  private val enrolmentPendingForUserView                                = instanceOf[enrolment_pending_for_user]
  private val errorEmailView                                             = instanceOf[email_error_template]
  private val errorView                                                  = instanceOf[error_template]

  private val userGroupIdSubscriptionStatusCheckService =
    new UserGroupIdSubscriptionStatusCheckService(mockSubscriptionStatusService, mockSave4LaterService)

  private val emailStatus = EmailStatus(Some("test@example.com"))

  trait TestFixture {

    val emailJourneyService = new EmailJourneyService(
      mockEmailVerificationService,
      mockSessionCache,
      mockSave4LaterService,
      mockUpdateVerifiedEmailService,
      errorEmailView,
      errorView,
      appConfig,
      mockExistingEoriService
    )

    val controller = new EmailController(
      mockAuthAction,
      mcc,
      mockSave4LaterService,
      userGroupIdSubscriptionStatusCheckService,
      enrolmentPendingForUserView,
      enrolmentPendingAgainstGroupIdView,
      emailJourneyService
    )

  }

  val verifiedEitherT: Future[Either[ResponseError, EmailVerificationStatus]] =
    Future.successful(Right(EmailVerificationStatus.Verified))

  val unverifiedEitherT: Future[Either[ResponseError, EmailVerificationStatus]] =
    Future.successful(Right(EmailVerificationStatus.Unverified))

  val lockedEitherT: Future[Either[ResponseError, EmailVerificationStatus]] =
    Future.successful(Right(EmailVerificationStatus.Locked))

  override def beforeEach(): Unit = {
    when(mockSave4LaterService.fetchEmailForService(any(), any(), any())(any()))
      .thenReturn(Future.successful(Some(emailStatus)))
    when(
      mockEmailVerificationService
        .getVerificationStatus(any[String], any[String])(any[HeaderCarrier])
    ).thenReturn(EitherT(verifiedEitherT))
    when(mockSave4LaterService.saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
    when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])).thenReturn(
      Future.successful(Right((): Unit))
    )
    when(mockSessionCache.saveEmail(any())(any()))
      .thenReturn(Future.successful(true))
    when(mockSessionCache.eori(any()))
      .thenReturn(Future.successful(Some("GB123456789")))
    when(mockSave4LaterService.fetchCacheIds(any())(any()))
      .thenReturn(Future.successful(None))
    when(mockSave4LaterService.fetchProcessingService(any())(any(), any())).thenReturn(Future.successful(None))
  }

  override def afterEach(): Unit =
    Mockito.reset(mockSave4LaterService)

  Mockito.reset(mockEmailVerificationService)
  Mockito.reset(mockUpdateVerifiedEmailService)
  Mockito.reset(mockSessionCache)

  "Calling the EmailController endpoint" should {

    "redirect when cache has no email status" in new TestFixture {
      when(mockSave4LaterService.fetchEmailForService(any(), any(), any())(any()))
        .thenReturn(Future.successful(None))

      callEndpointDefaulting(controller)(journey = subscribeJourneyShort) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith(
          "/atar/subscribe/autoenrolment/matching/what-is-your-email"
        )
      }
    }

    "redirect when cache has no email status (Long Journey)" in new TestFixture {
      when(mockSave4LaterService.fetchEmailForService(any(), any(), any())(any()))
        .thenReturn(Future.successful(None))

      callEndpointDefaulting(controller)(journey = subscribeJourneyLong) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith(
          "/atar/subscribe/longjourney/matching/what-is-your-email"
        )
      }
    }

    "redirect when email not verified" in new TestFixture {
      when(mockSave4LaterService.fetchEmail(any[GroupId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = false))))
      when(
        mockEmailVerificationService
          .getVerificationStatus(any[String], any[String])(any[HeaderCarrier])
      ).thenReturn(EitherT(unverifiedEitherT))
      val startVerificationResponse: Future[Either[ResponseError, ResponseWithURI]] =
        Future.successful(Right(ResponseWithURI("Some URI")))
      when(
        mockEmailVerificationService
          .startVerificationJourney(any(), any(), any(), any())(any(), any())
      ).thenReturn(EitherT(startVerificationResponse))

      callEndpointDefaulting(controller)(journey = subscribeJourneyShort) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("Some URI")
      }
    }

    "redirect when email not verified (Long Journey)" in new TestFixture {
      when(mockSave4LaterService.fetchEmail(any[GroupId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = false))))
      when(
        mockEmailVerificationService
          .getVerificationStatus(any[String], any[String])(any[HeaderCarrier])
      ).thenReturn(EitherT(unverifiedEitherT))
      val startVerificationResponse: Future[Either[ResponseError, ResponseWithURI]] =
        Future.successful(Right(ResponseWithURI("Some URI")))
      when(
        mockEmailVerificationService
          .startVerificationJourney(any(), any(), any(), any())(any(), any())
      ).thenReturn(EitherT(startVerificationResponse))
      callEndpointDefaulting(controller)(journey = subscribeJourneyLong) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("Some URI")
      }
    }

    "redirect when email is locked" in new TestFixture {
      when(mockSave4LaterService.fetchEmail(any[GroupId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = false))))
      when(
        mockEmailVerificationService
          .getVerificationStatus(any[String], any[String])(any[HeaderCarrier])
      ).thenReturn(EitherT(lockedEitherT))
      callEndpointDefaulting(controller)(journey = subscribeJourneyLong) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/matching/locked-email")
      }
    }

    "do not update email when it's a Long Journey" in new TestFixture {
      callEndpointDefaulting(controller)(journey = subscribeJourneyLong) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/longjourney/email-confirmed")
      }

      verify(mockUpdateVerifiedEmailService, times(0)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])
    }

    "do not update email when it's a non CDS Short Journey" in new TestFixture {
      callEndpointDefaulting(controller)(journey = subscribeJourneyShort, service = atarService) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/autoenrolment/email-confirmed")
      }

      verify(mockUpdateVerifiedEmailService, times(0)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])
    }

    "do call update email when it's CDS Short Journey" in new TestFixture {
      callEndpointDefaulting(controller)(journey = subscribeJourneyShort, service = cdsService) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/cds/subscribe/autoenrolment/email-confirmed")
      }

      verify(mockUpdateVerifiedEmailService, times(1)).updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])
    }

    "do not save email when updating email fails" in new TestFixture {
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier])).thenReturn(
        Future.successful(Left(Error("Some status")))
      )
      the[IllegalArgumentException] thrownBy callEndpointDefaulting(controller)(
        journey = subscribeJourneyShort,
        service = cdsService
      ) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/cds/subscribe/autoenrolment/email-confirmed")
      } should have message "Update Verified Email failed with non-retriable error"

      verify(mockSave4LaterService, times(0)).saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier])
    }

    "do not save email when updating verified email with retriable failure and display error page" in new TestFixture {
      when(mockUpdateVerifiedEmailService.updateVerifiedEmail(any(), any(), any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Left(UpdateEmailError("Some status"))))

      when(mockSessionCache.eori(any[Request[AnyContent]]))
        .thenReturn(Future.successful(Some("GB123456789")))

      callEndpointDefaulting(controller)(journey = subscribeJourneyShort, service = cdsService) { result =>
        status(result) shouldBe OK
      }

      verify(mockSave4LaterService, times(0)).saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier])
    }

    "redirect when email verified" in new TestFixture {
      when(mockSave4LaterService.fetchEmail(any[GroupId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = true))))
      callEndpointDefaulting(controller)(journey = subscribeJourneyShort) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/autoenrolment/email-confirmed")
      }
    }

    "block when same service subscription is in progress for group" in new TestFixture {
      when(mockSave4LaterService.fetchCacheIds(any())(any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some(atarService.code)))))
      when(mockSave4LaterService.fetchProcessingService(any())(any(), any())).thenReturn(
        Future.successful(Some(atarService))
      )
      when(mockSubscriptionStatusService.getStatus(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(SubscriptionProcessing))

      callEndpointDefaulting(controller)(journey = subscribeJourneyShort) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Someone in your organisation has already applied")
      }
    }

    "block when different service subscription is in progress for group" in new TestFixture {
      when(mockSave4LaterService.fetchCacheIds(any())(any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some(otherService.code)))))
      when(mockSave4LaterService.fetchProcessingService(any())(any(), any())).thenReturn(
        Future.successful(Some(otherService))
      )
      when(mockSubscriptionStatusService.getStatus(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(SubscriptionProcessing))

      callEndpointDefaulting(controller)(journey = subscribeJourneyShort) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Someone in your organisation has already applied")
      }
    }

    "block when different subscription is in progress for user" in new TestFixture {
      when(mockSave4LaterService.fetchCacheIds(any())(any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId(defaultUserId), SafeId("safe-id"), Some("other")))))
      when(mockSubscriptionStatusService.getStatus(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(SubscriptionProcessing))

      callEndpointDefaulting(controller)(journey = subscribeJourneyShort) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(messages("cds.enrolment.pending.title.user.processingService"))
      }
    }

    "block when same subscription is in progress for user" in new TestFixture {
      when(mockSave4LaterService.fetchCacheIds(any())(any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId(defaultUserId), SafeId("safe-id"), Some("atar")))))
      when(mockSubscriptionStatusService.getStatus(any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(SubscriptionProcessing))

      callEndpointDefaulting(controller)(journey = subscribeJourneyShort) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(messages("cds.enrolment.pending.title.user.processingService"))
      }
    }
  }

  val atarGroupEnrolment: EnrolmentResponse =
    EnrolmentResponse("HMRC-ATAR-ORG", "Active", List(KeyValue("EORINumber", "GB1234567890")))

  val cdsGroupEnrolment: EnrolmentResponse =
    EnrolmentResponse("HMRC-CUS-ORG", "Active", List(KeyValue("EORINumber", "GB1234567890")))

  private def callEndpointDefaulting(
    controller: EmailController
  )(userId: String = defaultUserId, journey: SubscribeJourney, service: Service = atarService)(
    test: Future[Result] => Any
  ): Unit =
    callEndpoint(controller)(userId, journey, service)(test)

  private def callEndpoint(
    controller: EmailController
  )(userId: String, journey: SubscribeJourney, service: Service)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .form(service, journey)
        .apply(SessionBuilder.buildRequestWithSessionAndPath(s"/${service.code}", userId))
    )
  }

}
