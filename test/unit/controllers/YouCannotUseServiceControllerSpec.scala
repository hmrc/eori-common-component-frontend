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

import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.YouCannotUseServiceController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{unauthorized, you_cant_use_service}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class YouCannotUseServiceControllerSpec extends ControllerSpec with AuthActionMock {
  private val mockAuthConnector = mock[AuthConnector]

  private val youCantUseServiceView = instanceOf[you_cant_use_service]
  private val unauthorisedView      = instanceOf[unauthorized]

  private val controller =
    new YouCannotUseServiceController(
      configuration,
      environment,
      mockAuthConnector,
      youCantUseServiceView,
      unauthorisedView,
      mcc
    )

  "YouCannotUseService Controller" should {
    "return Unauthorised 401 when page method is requested" in {
      page(Journey.Register) { result =>
        status(result) shouldBe UNAUTHORIZED
        val page = CdsPage(contentAsString(result))
        page.title should startWith(messages("cds.you-cant-use-service.heading"))
      }
    }

    "return Unauthorised 401 when unauthorisedPage method is requested" in {
      unauthorisedPage() { result =>
        status(result) shouldBe UNAUTHORIZED
        val page = CdsPage(contentAsString(result))
        page.title should startWith(messages("cds.server-errors.401.heading"))
      }
    }

  }

  private def page(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.page(Service.ATaR, journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def unauthorisedPage()(test: Future[Result] => Any) =
    await(test(controller.unauthorisedPage().apply(SessionBuilder.buildRequestWithSessionNoUser)))

}
