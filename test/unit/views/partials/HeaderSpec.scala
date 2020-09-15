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

package unit.views.partials

import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.ApplicationController
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.migration_start
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{accessibility_statement, start}
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.{AuthBuilder, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global

class HeaderSpec extends ControllerSpec {

  private val mockAuthConnector    = mock[AuthConnector]
  private val mockCdsFrontendCache = mock[SessionCache]

  private val viewStart                  = app.injector.instanceOf[start]
  private val migrationStart             = app.injector.instanceOf[migration_start]
  private val accessibilityStatementView = app.injector.instanceOf[accessibility_statement]

  private val controller = new ApplicationController(
    app,
    mockAuthConnector,
    mcc,
    viewStart,
    migrationStart,
    accessibilityStatementView,
    mockCdsFrontendCache,
    appConfig
  )

  "Header Sign in link" should {

    "be present when the user is logged in" in {
      AuthBuilder.withAuthorisedUser("user-1236213", mockAuthConnector)

      val result = controller.start().apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      val page = CdsPage(bodyOf(result))
      page.elementIsPresent("//a[@id='sign-out']") shouldBe true
    }

    "not be present when a user isn't logged in" in {
      val result = controller.start().apply(FakeRequest())

      val page = CdsPage(bodyOf(result))
      page.elementIsPresent("//a[@id='sign-out']") shouldBe false
    }
  }

  "Feedback URL" should {
    "be present with service param equal to CDS" in {
      val result = controller
        .start()
        .apply(
          SessionBuilder.buildRequestWithSessionAndPathNoUser(
            method = "GET",
            path = "/customs-enrolment-services/register/"
          )
        )

      val page = CdsPage(bodyOf(result))

      page.getElementAttribute(
        "//a[@id='feedback-link']",
        "href"
      ) shouldBe "https://www.tax.service.gov.uk/contact/beta-feedback-unauthenticated?service=CDS"
    }
  }
}
