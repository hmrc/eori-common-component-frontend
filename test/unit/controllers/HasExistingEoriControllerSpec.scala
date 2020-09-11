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
import uk.gov.hmrc.customs.rosmfrontend.controllers.HasExistingEoriController
import uk.gov.hmrc.customs.rosmfrontend.models.Service
import uk.gov.hmrc.customs.rosmfrontend.views.html.has_existing_eori
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HasExistingEoriControllerSpec extends ControllerSpec {
  private val mockAuthConnector = mock[AuthConnector]

  private val hasExistingEoriView = app.injector.instanceOf[has_existing_eori]

  private val controller =
    new HasExistingEoriController(app, mockAuthConnector, hasExistingEoriView, mcc)

  "Has Existing EORI Controller" should {

    "throw exception when user does not have existing CDS enrolment" in {
      intercept[IllegalStateException](
        displayPage(Service.ATar) { result => status(result) }).getMessage should startWith("No EORI found in enrolments")
    }

    "return Ok 200 when displayPage method is requested" in {
      displayPage(Service.ATar, Some("GB123456463324")) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should startWith("Your Government Gateway user ID is linked to an EORI")
      }
    }
  }

  private def displayPage(service: Service.Value, cdsEnrolmentId: Option[String] = None)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector, cdsEnrolmentId = cdsEnrolmentId)
    await(test(controller.displayPage(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }
}
