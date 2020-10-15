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
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.InternalId
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionStatusService
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

  private val mockUserGroupIdSubscriptionStatusCheckService =
    new UserGroupIdSubscriptionStatusCheckService(mockSubscriptionStatusService, mockSave4LaterConnector)

  private val controller = new EmailController(
    mockAuthAction,
    mockEmailVerificationService,
    mockSessionCache,
    mcc,
    mockSave4LaterService,
    mockUserGroupIdSubscriptionStatusCheckService,
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
  }

  "Viewing the form on Migration" should {

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
        await(result).header.headers("Location") should endWith("what-is-your-email")
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
        await(result).header.headers("Location") should endWith("verify-your-email")
      }
    }

    "redirect when email verified" in {
      when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(emailStatus.copy(isVerified = true))))
      showFormSubscription() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("email-confirmed")
      }
    }
  }

  private def showFormSubscription(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .form(atarService, Journey.Subscribe)
        .apply(SessionBuilder.buildRequestWithSession(userId))
    )
  }

}
