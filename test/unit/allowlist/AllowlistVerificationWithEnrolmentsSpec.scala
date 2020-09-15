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

package unit.allowlist

import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.YouNeedADifferentServiceController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.you_need_different_service
import util.ControllerSpec
import util.builders.{AuthBuilder, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global

class AllowlistVerificationWithEnrolmentsSpec extends ControllerSpec {
  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .disable[com.kenshoo.play.metrics.PlayModule]
    .configure("metrics.enabled" -> false)
    .configure(Map("allowlistEnabled" -> true, "allowlist" -> "mister_allow@example.com,bob@example.com"))
    .build()

  private val auth = mock[AuthConnector]

  private val youNeedDifferentServiceView = app.injector.instanceOf[you_need_different_service]

  private val controller = new YouNeedADifferentServiceController(app, auth, youNeedDifferentServiceView, mcc)

  "Allowlist verification" should {

    "return Unauthorized (401) when a non-allowlisted user attempts to access a route" in {
      AuthBuilder.withAuthorisedUser(defaultUserId, auth, userEmail = Some("not@example.com"))

      val result = controller
        .form(Journey.Subscribe)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs-enrolment-services/subscribe/", defaultUserId))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/customs-enrolment-services/subscribe/unauthorised")
    }

    "return Unauthorized (401) when a user attempts to access a route and they do not have an email address" in {
      AuthBuilder.withAuthorisedUser(defaultUserId, auth, userEmail = None)

      val result = controller
        .form(Journey.Subscribe)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs-enrolment-services/subscribe/", defaultUserId))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/customs-enrolment-services/subscribe/unauthorised")
    }

    "return OK (200) when a allowlisted user attempts to access a route" in {
      AuthBuilder.withAuthorisedUser(defaultUserId, auth, userEmail = Some("mister_allow@example.com"))

      val result = controller
        .form(Journey.Subscribe)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs-enrolment-services/subscribe/", defaultUserId))

      status(result) shouldBe OK
    }

    "not apply to Get Your EORI journey" in {
      AuthBuilder.withAuthorisedUser(defaultUserId, auth, userEmail = Some("not@example.com"))

      val result = controller
        .form(Journey.Register)
        .apply(SessionBuilder.buildRequestWithSessionAndPath("/customs-enrolment-services/register/", defaultUserId))

      status(result) shouldBe OK
    }
  }
}
