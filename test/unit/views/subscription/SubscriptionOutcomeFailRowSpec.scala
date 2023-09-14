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

package unit.views.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription._
import util.ViewSpec

class SubscriptionOutcomeFailRowSpec extends ViewSpec {

  implicit val request = withFakeCSRF(fakeAtarSubscribeRequest)

  private val view = instanceOf[subscription_outcome_fail_row]

  "'Subscription Fail' Page" should {

    "display correct heading for corporation" in {
      doc().body.getElementsByTag("h1").text() must startWith(
        messages("cds.subscription.outcomes.rejected.title.without.utr.org")
      )
    }
    "display correct heading for sole and Individual" in {
      doc(isOrganisation = false).body.getElementsByTag("h1").text() must startWith(
        messages("cds.subscription.outcomes.rejected.title.without.utr")
      )
    }
    "have the correct class on the h1" in {
      doc().body.getElementsByTag("h1").hasClass("govuk-heading-xl") mustBe true
    }
    "have correct contact us heading" in {
      doc().body.getElementById("contact-us-heading").text must startWith(
        messages("cds.subscription.outcomes.rejected.heading4")
      )
    }
    "have a contact us  link" in {
      val link = doc().body.getElementById("contact-us-link")
      link.text mustBe messages("cds.navigation.contact-us")
      link.attr(
        "href"
      ) mustBe "https://www.gov.uk/government/organisations/hm-revenue-customs/contact/customs-international-trade-and-excise-enquiries"
    }
    "display continue button when callBack URL exists" in {
      doc().body.getElementById("continue-button").text must startWith("Try Again")
    }

  }

  def doc(service: Service = atarService, isOrganisation: Boolean = true): Document =
    Jsoup.parse(contentAsString(view(service, isOrganisation)))

}
