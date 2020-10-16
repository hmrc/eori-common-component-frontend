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

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.RegisterRedirectController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import util.ControllerSpec
import util.builders.SessionBuilder

import scala.concurrent.Future

class RegisterRedirectControllerSpec extends ControllerSpec with BeforeAndAfterEach {
  private val mockAppConfig = mock[AppConfig]

  private val controller =
    new RegisterRedirectController(mcc, mockAppConfig)

  override def beforeEach: Unit =
    reset(mockAppConfig)

  "Register Redirect Controller" should {

    "redirect to Get EORI" when {

      "url is configured" in {
        when(mockAppConfig.externalGetEORILink).thenReturn(Some("/some-get-eori"))
        getEori { result =>
          status(result) shouldBe SEE_OTHER
          await(result).header.headers("Location") should endWith("some-get-eori")
        }
      }

      "url not configured" in {
        when(mockAppConfig.externalGetEORILink).thenReturn(None)
        getEori { result =>
          status(result) shouldBe SEE_OTHER
          await(result).header.headers("Location") should endWith("/customs-enrolment-services/atar/register")
        }
      }

    }

  }

  private def getEori(test: Future[Result] => Any) =
    await(test(controller.getEori(atarService, Journey.Subscribe).apply(SessionBuilder.buildRequestWithSessionNoUser)))

}
