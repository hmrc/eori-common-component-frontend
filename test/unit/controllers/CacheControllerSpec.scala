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

import org.junit.Ignore
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.{AnyContent, Request, Result, Session}
import play.mvc.Http.Status.SEE_OTHER
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.CacheController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class CacheControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSessionCache = mock[SessionCache]
  private val requestSessionData = new RequestSessionData()
  private val userId: String = "someUserId"
  private implicit val mockRequest = mock[Request[AnyContent]]

  val controller = new CacheController(app, mockAuthConnector, mockSessionCache, mcc, requestSessionData)

  "Cache controller" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.clearCache(Journey.Subscribe))

    "clear cache for subscription holder for subscription journey" in {
      withAuthorisedUser(userId, mockAuthConnector)
      when(mockSessionCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))
      val result: Result =
        await(controller.clearCache(Journey.Subscribe).apply(SessionBuilder.buildRequestWithSession(userId)))

      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") should be(ApplicationController.startSubscription(Service.ATaR).url)
      assertSessionDoesNotContainKeys(result.session)
    }

    // TODO - remove test or add service support to get EORI (register)
    "clear cache for subscription holder for get an eori journey" ignore  {
      withAuthorisedUser(userId, mockAuthConnector)
      when(mockSessionCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))
      val result: Result =
        await(controller.clearCache(Journey.Register).apply(SessionBuilder.buildRequestWithSession(userId)))
      status(result) shouldBe SEE_OTHER
      result.header.headers("Location") should be(ApplicationController.start().url)
      assertSessionDoesNotContainKeys(result.session)
    }
  }

  private def assertSessionDoesNotContainKeys(session: Session): Unit =
    session.data should contain noneOf (
      key("selected-organisation-type"),
      key("subscription-flow"),
      key("uri-before-subscription-flow")
    )
}
