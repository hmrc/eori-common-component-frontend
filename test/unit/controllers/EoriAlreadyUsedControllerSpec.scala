/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.http.Status.OK
import play.api.mvc.Request
import play.api.test.Helpers.{await, defaultAwaitTimeout, session, status}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.EoriAlreadyUsedController
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.eori_already_used
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EoriAlreadyUsedControllerSpec extends ControllerSpec with AuthActionMock {
  private val mockAuthConnector   = mock[AuthConnector]
  private val mockSessionCache    = mock[SessionCache]
  private val eoriAlreadyUsedView = instanceOf[eori_already_used]

  private val mockAuthAction = authAction(mockAuthConnector)

  private val controller =
    new EoriAlreadyUsedController(mockAuthAction, mockSessionCache, mcc, eoriAlreadyUsedView)

  "EoriAlreadyUsedController" should {

    "return OK (200) and display eori already used page" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)

      val result =
        controller.displayPage(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe OK

    }

    "logout an authenticated user for subscribe" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSessionCache.remove(any[Request[_]])).thenReturn(Future.successful(true))

      val result =
        controller.signInToAnotherAccount(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      session(result).isEmpty shouldBe true
      await(result).header.headers("Location") should endWith("/customs-enrolment-services/atar/subscribe")
    }
  }

}
