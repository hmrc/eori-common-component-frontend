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

package unit.controllers.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers.*
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.WeNeedToMakeChecksController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.DataUnavailableException
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.we_need_to_make_checks
import util.ControllerSpec
import util.builders.AuthActionMock
import util.builders.AuthBuilder.withAuthorisedUser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class WeNeedToMakeChecksControllerSpec extends ControllerSpec with AuthActionMock with BeforeAndAfterEach {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val weNeedToMakeChecksView         = mock[we_need_to_make_checks]
  private val mockAppConfig                  = mock[AppConfig]

  private val view = instanceOf[we_need_to_make_checks]

  private val controller = new WeNeedToMakeChecksController(
    mockAuthAction,
    mcc,
    mockSubscriptionDetailsService,
    weNeedToMakeChecksView,
    mockAppConfig
  )

  private val email = "test@mail.com"

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(weNeedToMakeChecksView.apply(any(), any())(any(), any())).thenReturn(
      HtmlFormat.empty
    )
    when(mockSubscriptionDetailsService.cachedEmail(any())).thenReturn(Future.successful(Some(email)))
  }

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockSubscriptionDetailsService)

    super.afterEach()
  }

  "WeNeedToMakeChecksController" should {
    "display page" in {
      val result = controller.displayPage(atarService)(getRequest)

      status(result) shouldBe OK

      val captor: ArgumentCaptor[String] =
        ArgumentCaptor.forClass(classOf[String])

      verify(weNeedToMakeChecksView).apply(captor.capture(), any())(
        any(),
        any()
      )
      captor.getValue shouldBe email
    }

    "throw exception if email to display is not available" in {
      reset(mockSubscriptionDetailsService)
      when(mockSubscriptionDetailsService.cachedEmail(any())).thenReturn(Future.successful(None))

      val caught = intercept[DataUnavailableException] {
        val future = controller.displayPage(atarService)(getRequest)
        Await.result(future, Duration.Inf)
      }
      caught.message shouldBe "Email is not cached"
    }
  }

  "we_need_to_make_checks " should {

    def doc(service: Service = atarService): Document =
      Jsoup.parse(contentAsString(view(email, service)(getRequest, messages)))

    "have the correct title " in {
      doc().title() should startWith(messages("ecc.subscription.we-need-to-make-checks.heading"))
    }

    "display correct heading" in {
      doc().body.getElementById("heading").text() should startWith(
        messages("ecc.subscription.we-need-to-make-checks.heading")
      )
    }
    "have the correct class on the h1" in {
      doc().body.getElementsByTag("h1").hasClass("govuk-heading-l") shouldBe true
    }

    "have the correct content" in {
      val content = doc().body.text()
      content should include(email)
      content should include(messages("ecc.subscription.we-need-to-make-checks.p1"))
      content should include(messages("ecc.subscription.we-need-to-make-checks.h2"))
      content should include(messages("ecc.subscription.we-need-to-make-checks.p2a", email))
      content should include(messages("ecc.subscription.we-need-to-make-checks.p2b"))
      content should include(messages("ecc.subscription.we-need-to-make-checks.h3"))
      content should include(messages("ecc.subscription.we-need-to-make-checks.p3a"))
      content should include(messages("ecc.subscription.we-need-to-make-checks.p3b"))
    }

  }

}
