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

package unit.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import play.api.mvc.Request
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.start_subscribe
import util.ViewSpec

class StartSubscriptionViewSpec extends ViewSpec {

  private val view                   = instanceOf[start_subscribe]
  implicit val request: Request[Any] = withFakeCSRF(fakeAtarSubscribeRequest)

  "start_subscribe view" should {
    "display the correct page when EuEori is enabled" in {
      elementOfStartSubscriptionView(
        Service.cds,
        Service.cds.friendlyName,
        true,
        "what-you-will-need-cds"
      ) mustBe defined
      elementOfStartSubscriptionView(
        Service.cds,
        Service.cds.friendlyName,
        true,
        "what-you-will-need-uk"
      ) mustBe defined
      elementOfStartSubscriptionView(
        Service.cds,
        Service.cds.friendlyName,
        true,
        "what-you-will-need-non-uk"
      ) mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, true, "approval-message-cds") mustBe defined
    }
    "display the correct page when EuEori is not enabled" in {
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "gb-eori") mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "what-you-will-need") mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "organisation") mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "organisation-text") mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "individual") mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "individual-text") mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "approval-message") mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "average-time") mustBe defined
      elementOfStartSubscriptionView(Service.cds, Service.cds.friendlyName, false, "find-utr-link") mustBe defined
      elementOfStartSubscriptionView(
        Service.cds,
        Service.cds.friendlyName,
        false,
        "find-utr-link-individual"
      ) mustBe defined
    }
  }

  private def elementOfStartSubscriptionView(
    service: Service,
    heading: String,
    isEuEoriEnabled: Boolean,
    element: String
  ): Option[Element] =
    Option(Jsoup.parse(contentAsString(view(service, heading, isEuEoriEnabled))).getElementById(element))

}
