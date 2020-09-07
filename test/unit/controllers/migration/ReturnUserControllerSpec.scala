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

package unit.controllers.migration

import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.controllers.migration.ReturnUserController
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.return_user
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReturnUserControllerSpec extends ControllerSpec {
  private val mockAuthConnector = mock[AuthConnector]

  private val returnUserView = app.injector.instanceOf[return_user]

  private val controller =
    new ReturnUserController(app, mockAuthConnector, returnUserView, mcc)

  "ReturnUser Controller" should {
    "return Ok 200 when page method is requested" in {
      show() { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should startWith(messages("cds.checking-status-page.title-and-heading"))
      }
    }

  }

  private def show()(test: Future[Result] => Any) = {
    await(test(controller.show().apply(SessionBuilder.buildRequestWithSessionNoUser)))
  }
}