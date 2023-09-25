/*
 * Copyright 2023 HM Revenue & Customs
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

package unit.controllers.registration

import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}
import util.ControllerSpec

class RosmRedirectControllerSpec extends ControllerSpec {

  def application: Application = new GuiceApplicationBuilder()
    .overrides(bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser])
    .build()

  Seq(
    "/customs/register-for-cds",
    "/customs/register-for-cds/are-you-based-in-uk",
    "/customs/subscribe-for-cds",
    "/customs/subscribe-for-cds/are-you-based-in-uk"
  ).foreach { url =>
    s"$url" should {
      "redirect the user to our start page" in {
        val appToTest = application
        running(appToTest) {
          val request = FakeRequest(GET, url)
          val result  = route(appToTest, request).get

          status(result) shouldEqual SEE_OTHER
          redirectLocation(result).get shouldEqual "/customs-enrolment-services/cds/subscribe"
        }
      }
    }

  }

}
