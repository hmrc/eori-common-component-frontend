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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.EnrolmentPendingAgainstGroupIdController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.enrolment_pending_against_group_id
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class EnrolmentPendingAgainstGroupIdControllerSpec extends ControllerSpec with AuthActionMock {

  private val mockAuthConnector                  = mock[AuthConnector]
  private val mockAuthAction                     = authAction(mockAuthConnector)
  private val mockSessionCache                   = mock[SessionCache]
  private val enrolmentPendingAgainstGroupIdView = instanceOf[enrolment_pending_against_group_id]

  private val controller = new EnrolmentPendingAgainstGroupIdController(
    mockAuthAction,
    mcc,
    mockSessionCache,
    enrolmentPendingAgainstGroupIdView
  )(global)

  "Enrolment Pending Against GroupId Controller" should {
    "return OK and redirect to the enrolment pending against groupId page" in {
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))
      displayPage(Journey.Subscribe) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("There is a problem")
      }
    }
  }

  private def displayPage(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.show(atarService, journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

}
