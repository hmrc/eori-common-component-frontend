/*
 * Copyright 2026 HM Revenue & Customs
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
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.YouCannotUseServiceController
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionBusinessService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.unable_to_use_id
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{unauthorized, you_cant_use_service}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class YouCannotUseServiceControllerSpec extends ControllerSpec with AuthActionMock with BeforeAndAfterEach {
  private val mockAuthConnector = mock[AuthConnector]

  private val youCantUseServiceView = instanceOf[you_cant_use_service]
  private val unauthorisedView      = instanceOf[unauthorized]
  private val unableToUseIdPage     = mock[unable_to_use_id]

  private val mockAuthAction   = authAction(mockAuthConnector)
  private val mockSessionCache = mock[SubscriptionBusinessService]

  private val controller =
    new YouCannotUseServiceController(
      configuration,
      environment,
      mockAuthConnector,
      mockAuthAction,
      mockSessionCache,
      youCantUseServiceView,
      unauthorisedView,
      unableToUseIdPage,
      mcc
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(unableToUseIdPage.apply(any())(any(), any())).thenReturn(HtmlFormat.empty)
    when(mockSessionCache.cachedEoriNumber(any())).thenReturn(Future.successful(Some("GB123456789123")))
  }

  override protected def afterEach(): Unit = {
    reset(unableToUseIdPage)
    reset(mockSessionCache)

    super.afterEach()
  }

  "YouCannotUseService Controller" should {
    "return Unauthorised 401 when page method is requested" in {
      page() { result =>
        status(result) shouldBe UNAUTHORIZED
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(messages("cds.you-cant-use-service.heading"))
      }
    }

    "return Unauthorised 401 when unauthorisedPage method is requested" in {
      unauthorisedPage() { result =>
        status(result) shouldBe UNAUTHORIZED
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(messages("cds.server-errors.401.heading"))
      }
    }

    "return OK (200) and display unable to use Id page" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)

      val result =
        controller.unableToUseIdPage(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe OK

      verify(unableToUseIdPage).apply(any())(any(), any())
    }

    "redirect to What is Your email page " in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSessionCache.cachedEoriNumber(any())).thenReturn(Future.successful(None))
      val result =
        controller.unableToUseIdPage(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori")

    }
  }

  private def page()(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(controller.page(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def unauthorisedPage()(test: Future[Result] => Any) =
    test(controller.unauthorisedPage(atarService).apply(SessionBuilder.buildRequestWithSessionNoUser))

}
