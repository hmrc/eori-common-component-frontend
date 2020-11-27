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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.Save4LaterConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.EnrolmentPendingController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{CacheIds, InternalId, SafeId}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{
  enrolment_pending_against_group_id,
  enrolment_pending_for_user
}
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class EnrolmentPendingControllerSpec extends ControllerSpec with AuthActionMock {

  private val mockAuthConnector                  = mock[AuthConnector]
  private val mockAuthAction                     = authAction(mockAuthConnector)
  private val mockSessionCache                   = mock[SessionCache]
  private val mockSave4LaterConnector            = mock[Save4LaterConnector]
  private val enrolmentPendingAgainstGroupIdView = instanceOf[enrolment_pending_against_group_id]
  private val enrolmentPendingForUserView        = instanceOf[enrolment_pending_for_user]

  private val infoXpath = "//*[@id='info']"

  private val controller = new EnrolmentPendingController(
    mockAuthAction,
    mcc,
    mockSessionCache,
    mockSave4LaterConnector,
    enrolmentPendingForUserView,
    enrolmentPendingAgainstGroupIdView
  )(global)

  "Enrolment Pending Controller" should {
    "return OK and redirect to the enrolment pending against groupId page" in {
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(mockSave4LaterConnector.get[CacheIds](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some("atar")))))

      displayGroupPage(Journey.Subscribe) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("There is a problem")
        page.getElementText(infoXpath) should include(
          "We are currently processing a subscription request to Advance Tariff Rulings from someone in your organisation"
        )
      }
    }
    "error for enrolment pending against groupId when CacheIds missing" in {
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(mockSave4LaterConnector.get[CacheIds](any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      intercept[IllegalStateException] {
        displayGroupPage(Journey.Subscribe) { result =>
          await(result)
        }
      }.getMessage shouldBe "No details stored in cache for this group"
    }

    "return OK and redirect to the enrolment pending for user page" in {
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(mockSave4LaterConnector.get[CacheIds](any(), any())(any(), any()))
        .thenReturn(Future.successful(Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some("atar")))))

      displayUserPage(Journey.Subscribe) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("There is a problem")
        page.getElementText(infoXpath) should include(
          "We are currently processing your subscription request to Advance Tariff Rulings"
        )
      }
    }
    "error for enrolment pending for user when CacheIds missing" in {
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(mockSave4LaterConnector.get[CacheIds](any(), any())(any(), any()))
        .thenReturn(Future.successful(None))

      intercept[IllegalStateException] {
        displayUserPage(Journey.Subscribe) { result =>
          await(result)
        }
      }.getMessage shouldBe "No details stored in cache for this group"
    }
  }

  private def displayGroupPage(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(
      test(controller.pendingGroup(atarService, journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
    )
  }

  private def displayUserPage(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(
      test(controller.pendingUser(atarService, journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
    )
  }

}
