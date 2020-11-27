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

package unit.controllers

import common.pages.matching.AddressPageFactoring
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.Save4LaterConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.EmailController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.GroupEnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionProcessing,
  SubscriptionStatusService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.{Save4LaterService, UserGroupIdSubscriptionStatusCheckService}
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailControllerSpec
    extends ControllerSpec with AddressPageFactoring with MockitoSugar with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector             = mock[AuthConnector]
  private val mockAuthAction                = authAction(mockAuthConnector)
  private val mockEmailVerificationService  = mock[EmailVerificationService]
  private val mockSave4LaterService         = mock[Save4LaterService]
  private val mockSessionCache              = mock[SessionCache]
  private val mockSave4LaterConnector       = mock[Save4LaterConnector]
  private val mockSubscriptionStatusService = mock[SubscriptionStatusService]
  private val groupEnrolmentExtractor       = mock[GroupEnrolmentExtractor]

  private val userGroupIdSubscriptionStatusCheckService =
    new UserGroupIdSubscriptionStatusCheckService(mockSubscriptionStatusService, mockSave4LaterConnector)

  private val controller = new EmailController(
    mockAuthAction,
    mockEmailVerificationService,
    mockSessionCache,
    mcc,
    mockSave4LaterService,
    userGroupIdSubscriptionStatusCheckService,
    groupEnrolmentExtractor
  )

  private val emailStatus = EmailStatus("test@example.com")

  override def beforeEach: Unit = {
    when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(emailStatus)))
    when(
      mockEmailVerificationService
        .isEmailVerified(any[String])(any[HeaderCarrier])
    ).thenReturn(Future.successful(Some(true)))
    when(mockSave4LaterService.saveEmail(any(), any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
    when(mockSessionCache.saveEmail(any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
    when(mockSave4LaterConnector.get(any(), any())(any(), any()))
      .thenReturn(Future.successful(None))
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any()))
      .thenReturn(Future.successful(false))
    when(groupEnrolmentExtractor.groupIdEnrolments(any())(any()))
      .thenReturn(Future.successful(List.empty))
  }

  "Viewing the form on Subscribe" should {

    "display the form with no errors" in {
      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        val page = CdsPage(contentAsString(result))
        page.getElementsText(PageLevelErrorSummaryListXPath) shouldBe empty
      }
    }

    "redirect when cache has no email status" in {
      when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/matching/what-is-your-email")
      }
    }

    "redirect when email not verified" in {
      when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = false))))
      when(
        mockEmailVerificationService
          .isEmailVerified(any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(false)))
      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/matching/verify-your-email")
      }
    }

    "redirect when email verified" in {
      when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = true))))
      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/email-confirmed")
      }
    }

    "block when subscription is in progress for group" in {
      when(mockSave4LaterConnector.get[CacheIds](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some("atar")))))
      when(mockSubscriptionStatusService.getStatus(any(), any())(any()))
        .thenReturn(Future.successful(SubscriptionProcessing))

      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/enrolment-pending-against-groupId")
      }
    }

    "block when different subscription is in progress for user" in {
      when(mockSave4LaterConnector.get[CacheIds](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId(defaultUserId), SafeId("safe-id"), Some("other")))))
      when(mockSubscriptionStatusService.getStatus(any(), any())(any()))
        .thenReturn(Future.successful(SubscriptionProcessing))

      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/enrolment-pending")
      }
    }

    "continue when same subscription is in progress for user" in {
      when(mockSave4LaterConnector.get[CacheIds](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId(defaultUserId), SafeId("safe-id"), Some("atar")))))
      when(mockSubscriptionStatusService.getStatus(any(), any())(any()))
        .thenReturn(Future.successful(SubscriptionProcessing))

      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/email-confirmed")
      }
    }
  }

  val atarGroupEnrolment: EnrolmentResponse =
    EnrolmentResponse("HMRC-ATAR-ORG", "Active", List(KeyValue("EORINumber", "GB1234567890")))

  val cdsGroupEnrolment: EnrolmentResponse =
    EnrolmentResponse("HMRC-CUS-ORG", "Active", List(KeyValue("EORINumber", "GB1234567890")))

  "Viewing the form on Register" should {

    "display the form with no errors" in {
      showFormRegister() { result =>
        status(result) shouldBe SEE_OTHER
        val page = CdsPage(contentAsString(result))
        page.getElementsText(PageLevelErrorSummaryListXPath) shouldBe empty
      }
    }

    "redirect when cache has no email status" in {
      when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      showFormRegister() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/register/matching/what-is-your-email")
      }
    }

    "redirect when email not verified" in {
      when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = false))))
      when(
        mockEmailVerificationService
          .isEmailVerified(any[String])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Some(false)))
      showFormRegister() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/register/matching/verify-your-email")
      }
    }

    "redirect when email verified" in {
      when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = true))))
      showFormRegister() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/register/email-confirmed")
      }
    }

    "redirect when subscription is in progress" in {
      when(mockSave4LaterConnector.get[CacheIds](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some("atar")))))
      when(mockSubscriptionStatusService.getStatus(any(), any())(any()))
        .thenReturn(Future.successful(SubscriptionProcessing))

      showFormRegister() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/register/enrolment-pending-against-groupId")
      }
    }

    "redirect when group enrolled to service" in {
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any()))
        .thenReturn(Future.successful(List(atarGroupEnrolment)))

      showFormRegister() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/register/enrolment-already-exists-for-group")
      }
    }

    "redirect when user has existing EORI" in {
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any()))
        .thenReturn(Future.successful(List(cdsGroupEnrolment)))

      showFormRegister() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/register/you-already-have-an-eori")
      }
    }
  }

  private def showFormSubscription(userId: String = defaultUserId)(test: Future[Result] => Any): Unit =
    showForm(userId, Journey.Subscribe)(test)

  private def showFormRegister(userId: String = defaultUserId)(test: Future[Result] => Any): Unit =
    showForm(userId, Journey.Register)(test)

  private def showForm(userId: String = defaultUserId, journey: Journey.Value)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .form(atarService, journey)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/atar", userId))
    )
  }

}
