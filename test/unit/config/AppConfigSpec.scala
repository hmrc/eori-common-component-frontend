/*
 * Copyright 2025 HM Revenue & Customs
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

package unit.config

import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.scalatest.BeforeAndAfterEach
import play.api.Configuration
import play.api.i18n.{Lang, Messages, MessagesImpl}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.ControllerSpec

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class AppConfigSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockConfig: Configuration = spy(config)
  private val mockServiceConfig         = mock[ServicesConfig]

  private val services = Seq(atarService.code, gvmsService.code, otherService.code, cdsService.code)

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockConfig)
    Mockito.reset(mockServiceConfig)
  }

  "AppConfig" should {

    "have ttl defined" in {
      appConfig.ttl shouldBe Duration(40, TimeUnit.MINUTES)
    }

    "check welsh" in {
      val msg: Messages = MessagesImpl(Lang("cy"), messagesApi).messages
      appConfig.findLostUtr()(msg) shouldBe "https://www.gov.uk/dod-o-hyd-i-utr-sydd-ar-goll"
    }

    "have allowlistReferrers defined" in {
      appConfig.allowlistReferrers shouldBe Array.empty
    }

    "have emailVerificationBaseUrl defined" in {
      appConfig.emailVerificationBaseUrl shouldBe "http://localhost:9891"
    }

    "have emailVerificationServiceContext defined" in {
      appConfig.emailVerificationServiceContext shouldBe "email-verification"
    }

    "have verifyEmailAddress defined" in {
      appConfig.emailVerificationTemplateId shouldBe "verifyEmailAddress"
    }

    "have emailVerificationLinkExpiryDuration defined" in {
      appConfig.emailVerificationLinkExpiryDuration shouldBe "P1D"
    }

    "have handleSubscriptionBaseUrl defined" in {
      appConfig.handleSubscriptionBaseUrl shouldBe "http://localhost:6752"
    }

    "have handleSubscriptionServiceContext defined" in {
      appConfig.handleSubscriptionServiceContext shouldBe "handle-subscription"
    }

    "have taxEnrolmentsBaseUrl defined" in {
      appConfig.taxEnrolmentsBaseUrl shouldBe "http://localhost:6754"
    }

    "have taxEnrolmentsServiceContext defined" in {
      appConfig.taxEnrolmentsServiceContext shouldBe "tax-enrolments"
    }

    "have enrolmentStoreProxyBaseUrl defined" in {
      appConfig.enrolmentStoreProxyBaseUrl shouldBe "http://localhost:6754"
    }

    "have enrolmentStoreProxyServiceContext defined" in {
      appConfig.enrolmentStoreProxyServiceContext shouldBe "enrolment-store-proxy"
    }

    "have feedbackLink defined for subscribe" in {
      val basGatewayUrl: String = "http://localhost:9553/bas-gateway/sign-out-without-state"
      appConfig.feedbackUrl(
        atarService
      ) shouldBe s"$basGatewayUrl?continue=http://localhost:9514/feedback/eori-common-component-subscribe-atar"
    }

    "have reportAProblemPartialUrl defined for subscribe" in {
      appConfig.reportAProblemPartialUrlSubscribe(
        atarService
      ) shouldBe "http://localhost:9250/contact/problem_reports_ajax?service=eori-common-component-subscribe-atar"
    }

    "have reportAProblemNonJSUrl defined for subscribe" in {
      appConfig.reportAProblemNonJSUrlSubscribe(
        atarService
      ) shouldBe "http://localhost:9250/contact/problem_reports_nonjs?service=eori-common-component-subscribe-atar"
    }
  }

  "using getServiceUrl" should {
    "return service url for register-with-id" in {
      appConfig.getServiceUrl("register-with-id") shouldBe "http://localhost:6753/register-with-id"
    }
    "return service url for register-without-id" in {
      appConfig.getServiceUrl("register-without-id") shouldBe "http://localhost:6753/register-without-id"
    }
    "return service url for register-with-eori-and-id" in {
      appConfig.getServiceUrl("register-with-eori-and-id") shouldBe "http://localhost:6753/register-with-eori-and-id"
    }
    "return service url for subscription-status" in {
      appConfig.getServiceUrl("subscription-status") shouldBe "http://localhost:6753/subscription-status"
    }
    "return service url for subscription-display" in {
      appConfig.getServiceUrl("subscription-display") shouldBe "http://localhost:6753/subscription-display"
    }
    "return service url for registration-display" in {
      appConfig.getServiceUrl("registration-display") shouldBe "http://localhost:6753/registration-display"
    }
    "return service url for subscribe" in {
      appConfig.getServiceUrl("subscribe") shouldBe "http://localhost:6753/subscribe"
    }
    "return service url for vat-known-facts-control-list" in {
      appConfig.getServiceUrl(
        "vat-known-facts-control-list"
      ) shouldBe "http://localhost:6753/vat-known-facts-control-list"
    }

    "register for an EORI link takes user to ECC" in {
      services.foreach(
        serviceCode =>
          appConfig.eoriCommonComponentRegistrationFrontend(
            serviceCode
          ) shouldBe s"http://localhost:6751/customs-registration-services/$serviceCode/register"
      )
    }

    "return address lookup url" in {

      appConfig.addressLookup shouldBe "http://localhost:6754/lookup"
    }
  }
}
