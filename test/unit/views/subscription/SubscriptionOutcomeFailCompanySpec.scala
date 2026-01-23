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

package unit.views.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription._
import util.ViewSpec

class SubscriptionOutcomeFailCompanySpec extends ViewSpec {

  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(fakeAtarSubscribeRequest)

  private val view = instanceOf[subscription_outcome_fail_company]

  "'Subscription Fail' Page" should {

    "have the correct title " in {
      doc().title() must startWith(messages("cds.subscription.outcomes.rejected.title.with.utr"))
    }

    "display correct heading" in {
      doc().body.getElementsByTag("h1").text() must startWith(
        messages("cds.subscription.outcomes.rejected.title.with.utr")
      )
    }
    "have the correct class on the h1" in {
      doc().body.getElementsByTag("h1").hasClass("govuk-heading-xl") mustBe true
    }

    "have correct first paragraph" in {
      doc().body().getElementById("rejected-para1").text must startWith(
        messages("cds.subscription.outcomes.rejected.para1")
      )
    }
    "have the correct h2 for company" in {
      doc().body.getElementById("orgType").text must startWith(
        messages("cds.subscription.outcomes.rejected.heading2.company")
      )
    }

    "have a companies house link" in {
      val link = doc().body.getElementById("companies-house")
      link.text mustBe messages("cds.subscription.outcomes.rejected.para2.link")
      link.attr("href") mustBe "https://beta.companieshouse.gov.uk/"
    }

    "have the correct h2 for corporation" in {
      doc().body.getElementById("corporation-heading").text must startWith(
        messages("cds.subscription.outcomes.rejected.heading3.corporation")
      )
    }
    "have the correct para 3 " in {
      doc().body.getElementById("rejected-para3").text must startWith(
        messages("cds.subscription.outcomes.rejected.para3")
      )
    }
    "have a find lost utr link" in {
      val link = doc().body.getElementById("find-lost-utr")
      link.text mustBe messages("cds.navigation.find-lost-utr")
      link.attr("href") mustBe "https://www.gov.uk/find-lost-utr-number"
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

    "have a feedback 'continue' button" in {
      val link = doc().body.getElementById("feedback-continue")
      link.text mustBe "More about Advance Tariff Rulings"
      link.attr("href") mustBe "/test-atar/feedback?status=Failed"
    }

    "have a no feedback 'continue' button when config missing" in {
      val link = doc(atarService.copy(feedbackUrl = None)).body.getElementById("feedback-continue")
      link mustBe null
    }

    "display continue button when callBack URL exists" in {
      doc().body.getElementById("continue-button").text must startWith("Try Again")
    }

  }

  def doc(service: Service = atarService): Document =
    Jsoup.parse(contentAsString(view(service)))

}
