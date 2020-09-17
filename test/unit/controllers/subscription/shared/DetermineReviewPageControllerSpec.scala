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

package unit.controllers.subscription.shared

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import play.api.mvc._
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.DetermineReviewPageController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.global

class DetermineReviewPageControllerSpec extends ControllerSpec with BeforeAndAfter {

  private val mockAuthConnector      = mock[AuthConnector]
  private val mockRequestSessionData = mock[RequestSessionData]

  private val controller = new DetermineReviewPageController(app, mockAuthConnector, mcc)(global)

  before {
    reset(mockRequestSessionData)
  }

  "Determine Review controller" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      controller.determineRoute(Service.ATaR, Journey.Register)
    )

    "redirect to to correct page when session data is set with the key journeyType.Subscribe" in {
      determinRouteSubscription { result =>
        val awaitedResult = await(result)
        status(awaitedResult) shouldBe SEE_OTHER
        awaitedResult.header.headers.get("Location") shouldBe
          Some(
            uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.CheckYourDetailsController
              .reviewDetails(Service.ATaR, Journey.Subscribe)
              .url
          )
      }
    }

    "redirect to to correct page when session data is not set with JourneyType.Subscribe" in {

      determinRouteGetYourEORI { result =>
        val awaitedResult = await(result)
        status(awaitedResult) shouldBe SEE_OTHER
        awaitedResult.header.headers.get("Location") shouldBe
          Some(
            uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.CheckYourDetailsRegisterController
              .reviewDetails(Service.ATaR, Journey.Register)
              .url
          )
      }
    }
  }

  private def determinRouteGetYourEORI(test: Future[Result] => Any) {
    val aUserId = defaultUserId
    withAuthorisedUser(aUserId, mockAuthConnector)

    val result =
      controller.determineRoute(Service.ATaR, Journey.Register).apply(SessionBuilder.buildRequestWithSession(aUserId))
    test(result)
  }

  private def determinRouteSubscription(test: Future[Result] => Any) {
    val aUserId = defaultUserId
    withAuthorisedUser(aUserId, mockAuthConnector)

    val result =
      controller.determineRoute(Service.ATaR, Journey.Subscribe).apply(SessionBuilder.buildRequestWithSession(aUserId))
    test(result)
  }

}
