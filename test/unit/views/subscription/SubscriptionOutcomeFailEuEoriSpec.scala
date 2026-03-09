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
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.*
import util.ViewSpec

class SubscriptionOutcomeFailEuEoriSpec extends ViewSpec {

  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(fakeCDSSubscribeRequest)

  private val view = instanceOf[subscription_outcome_fail_eu_eori]

  "'Subscription Fail' Page" should {

    "display correct heading" in {
      doc().body.getElementsByTag("h1").text() must startWith(
        messages("cds.eu-eori.outcome.unsuccessful.h1")
      )
    }

    "have the correct class on the h1" in {
      doc().body.getElementsByTag("h1").hasClass("govuk-heading-l") mustBe true
    }

    "start-again must have the correct text" in {
      val link = doc().body.getElementById("start-again")
      link.text mustBe s"${messages("cds.eu-eori.outcome.unsuccessful.p4", messages("cds.eu-eori.outcome.unsuccessful.p4-link"))}"
    }

    "have a no feedback 'continue' button when config missing" in {
      val link = doc(cdsService.copy(feedbackUrl = None)).body.getElementById("feedback-continue")
      link mustBe null
    }

  }

  def doc(service: Service = cdsService): Document =
    Jsoup.parse(contentAsString(view(service)))

}
