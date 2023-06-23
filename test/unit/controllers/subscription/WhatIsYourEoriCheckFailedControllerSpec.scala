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

package unit.controllers.subscription

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.WhatIsYourEoriCheckFailedController
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.DataUnavailableException
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.what_is_your_eori_check_failed
import util.ControllerSpec
import util.builders.AuthActionMock
import util.builders.AuthBuilder.withAuthorisedUser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class WhatIsYourEoriCheckFailedControllerSpec extends ControllerSpec with AuthActionMock with BeforeAndAfterEach {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val whatIsYourEoriCheckFailedView  = mock[what_is_your_eori_check_failed]

  private val controller = new WhatIsYourEoriCheckFailedController(
    mockAuthAction,
    mcc,
    mockSubscriptionDetailsService,
    whatIsYourEoriCheckFailedView
  )

  private val eori = "GB123456789012"

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(whatIsYourEoriCheckFailedView.apply(any(), any())(any(), any())).thenReturn(HtmlFormat.empty)
    when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(Some(eori)))
  }

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockSubscriptionDetailsService)

    super.afterEach()
  }

  "What Is Your Eori Check Failed Controller" should {
    "display what_is_your_eori_check_failed page" in {
      val result = controller.displayPage(atarService)(getRequest)

      status(result) shouldBe OK

      val eoriCaptor: ArgumentCaptor[String] =
        ArgumentCaptor.forClass(classOf[String])

      verify(whatIsYourEoriCheckFailedView).apply(eoriCaptor.capture(), any())(any(), any())
      eoriCaptor.getValue shouldBe eori
    }

    "throw exception if eori to display is not available" in {
      reset(mockSubscriptionDetailsService)
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val caught = intercept[DataUnavailableException] {
        val future = controller.displayPage(atarService)(getRequest)
        Await.result(future, Duration.Inf)
      }
      caught.message shouldBe "Eori is not cached"
    }
  }
}
