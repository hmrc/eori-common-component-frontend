/*
 * Copyright 2024 HM Revenue & Customs
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
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.mvc.Results.{NoContent, Ok}
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{routes, HasExistingEoriController}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.ExistingEoriService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{eori_enrol_success, has_existing_eori}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.Future

class HasExistingEoriControllerSpec extends ControllerSpec with AuthActionMock with BeforeAndAfterEach {
  private val mockAuthConnector       = mock[AuthConnector]
  private val mockExistingEoriService = mock[ExistingEoriService]
  private val hasExistingEoriView     = instanceOf[has_existing_eori]
  private val enrolSuccessView        = instanceOf[eori_enrol_success]
  private val mockAuthAction          = authAction(mockAuthConnector)

  private val controller = new HasExistingEoriController(mockAuthAction, mcc, mockExistingEoriService)

  override def beforeEach(): Unit = {
    reset(mockExistingEoriService)
    super.beforeEach()
  }

  "EoriAlreadyUsedController" should {

    "return OK (200) and display has Existing Eori page" in {
      val request = SessionBuilder.buildRequestWithSession(defaultUserId)
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockExistingEoriService.onDisplay(any())(any(), any(), any())).thenReturn(
        Future.successful(
          Ok(
            hasExistingEoriView(atarService, "eori", routes.HasExistingEoriController.enrol(atarService))(
              request,
              messages
            )
          )
        )
      )

      val result =
        controller.displayPage(atarService).apply(request)

      status(result) shouldBe OK
    }

    "return result when enrolment is successful" in {
      val request = SessionBuilder.buildRequestWithSession(defaultUserId)
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockExistingEoriService.onEnrol(any())(any(), any(), any())).thenReturn(Future.successful(NoContent))

      val result =
        controller.enrol(atarService).apply(request)

      status(result) shouldBe NO_CONTENT
    }
  }
}
