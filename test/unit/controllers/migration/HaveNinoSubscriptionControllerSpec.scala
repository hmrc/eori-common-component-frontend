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

import common.pages.matching.SubscriptionNinoPage
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.HaveNinoSubscriptionController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CustomsId
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{SubscriptionFlowInfo, SubscriptionPage}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.match_nino_subscription
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HaveNinoSubscriptionControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSubscriptionFlowManager = mock[SubscriptionFlowManager]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockSubscriptionFlowInfo = mock[SubscriptionFlowInfo]
  private val mockSubscriptionPage = mock[SubscriptionPage]

  private val matchNinoSubscriptionView = app.injector.instanceOf[match_nino_subscription]

  private val ValidNinoRequest = Map("have-nino" -> "true", "nino" -> "AB123456C")
  private val ValidNinoNoRequest = Map("have-nino" -> "false", "nino" -> "")

  private val nextPageFlowUrl = "/customs-enrolment-services/subscribe/address"

  override protected def beforeEach: Unit = reset(mockSubscriptionDetailsService)

  val controller = new HaveNinoSubscriptionController(
    app,
    mockAuthConnector,
    mockSubscriptionFlowManager,
    mcc,
    matchNinoSubscriptionView,
    mockSubscriptionDetailsService
  )

  "HaveNinoSubscriptionController createForm" should {
    "return OK and display correct page" in {
      createForm(Journey.Subscribe) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should include(SubscriptionNinoPage.title)
      }
    }
  }

  "HaveNinoSubscriptionController submit" should {
    "return BadRequest when no option selected" in {
      submit(Journey.Subscribe, Map.empty[String, String]) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }

    "return BadRequest when invalidUtr provided" in {
      val invalidNino = "01234567890123"
      submit(Journey.Subscribe, Map("have-nino" -> "true", "nino" -> invalidNino)) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }

    "cache NINO and redirect to Address Page of the flow" in {
      when(mockSubscriptionDetailsService.cacheCustomsId(any[CustomsId])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
      mockSubscriptionFlow(nextPageFlowUrl)
      submit(Journey.Subscribe, ValidNinoRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs-enrolment-services/subscribe/address"
      }
    }

    "cache None for CustomsId and redirect to Address Page of the flow" in {
      when(mockSubscriptionDetailsService.clearCachedCustomsId(any[HeaderCarrier])).thenReturn(Future.successful(()))
      mockSubscriptionFlow(nextPageFlowUrl)
      submit(Journey.Subscribe, ValidNinoNoRequest) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers(LOCATION) shouldBe "/customs-enrolment-services/subscribe/address"
      }
      verify(mockSubscriptionDetailsService).clearCachedCustomsId(any[HeaderCarrier])
    }
  }

  private def createForm(journey: Journey.Value)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(test(controller.createForm(journey).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def submit(journey: Journey.Value, form: Map[String, String])(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    await(
      test(controller.submit(journey).apply(SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, form)))
    )
  }

  private def mockSubscriptionFlow(url: String) = {
    when(mockSubscriptionFlowManager.stepInformation(any())(any[HeaderCarrier], any[Request[AnyContent]]))
      .thenReturn(mockSubscriptionFlowInfo)
    when(mockSubscriptionFlowInfo.nextPage).thenReturn(mockSubscriptionPage)
    when(mockSubscriptionPage.url).thenReturn(url)
  }
}
