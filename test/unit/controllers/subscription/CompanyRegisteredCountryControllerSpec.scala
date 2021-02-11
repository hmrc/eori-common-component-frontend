/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, verifyZeroInteractions, when}
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.CompanyRegisteredCountryController
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.CompanyRegisteredCountry
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.country
import util.ControllerSpec
import util.builders.AuthActionMock
import util.builders.AuthBuilder.withAuthorisedUser

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class CompanyRegisteredCountryControllerSpec extends ControllerSpec with AuthActionMock with BeforeAndAfterEach {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val countryPage                    = mock[country]

  private val controller =
    new CompanyRegisteredCountryController(mockAuthAction, mockSubscriptionDetailsService, mcc, countryPage)(global)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(mockSubscriptionDetailsService.cachedRegisteredCountry()(any())).thenReturn(
      Future.successful(Some(CompanyRegisteredCountry("United Kingdom")))
    )
    when(mockSubscriptionDetailsService.cacheRegisteredCountry(any())(any())).thenReturn(Future.successful((): Unit))
    when(countryPage.apply(any(), any(), any(), any(), any())(any(), any())).thenReturn(HtmlFormat.empty)
  }

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector, mockSubscriptionDetailsService, countryPage)

    super.afterEach()
  }

  "Company Registerted Country Controller" should {

    "display page" in {

      val result = controller.displayPage(atarService)(FakeRequest("GET", ""))

      status(result) shouldBe OK
      verify(countryPage).apply(any(), any(), any(), any(), ArgumentMatchers.eq(false))(any(), any())
    }

    "display review page" in {

      val result = controller.reviewPage(atarService)(FakeRequest("GET", ""))

      status(result) shouldBe OK
      verify(countryPage).apply(any(), any(), any(), any(), ArgumentMatchers.eq(true))(any(), any())
    }

    "return 400 (BAD_REQUEST))" when {

      "user didn't choose value" in {

        val result =
          controller.submit(atarService, false)(FakeRequest("POST", "").withFormUrlEncodedBody("countryCode" -> ""))

        status(result) shouldBe BAD_REQUEST
        verify(countryPage).apply(any(), any(), any(), any(), any())(any(), any())
      }
    }

    "return 303 (SEE_OTHER)" when {

      "user provide correct country and is in review mode" in {

        val result = controller.submit(atarService, true)(
          FakeRequest("POST", "").withFormUrlEncodedBody("countryCode" -> "Poland")
        )

        status(result) shouldBe SEE_OTHER
        verifyZeroInteractions(countryPage)
      }

      "user provide correct country and is not in review mode" in {

        val result = controller.submit(atarService, false)(
          FakeRequest("POST", "").withFormUrlEncodedBody("countryCode" -> "United Kingdom")
        )

        status(result) shouldBe SEE_OTHER
        verifyZeroInteractions(countryPage)
      }
    }
  }
}
