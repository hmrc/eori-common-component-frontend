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

package unit.views.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.sub01_outcome_rejected
import util.ViewSpec

class Sub01OutcomeRejectedSpec extends ViewSpec {

  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(fakeAtarSubscribeRequest)

  val orgName       = "Test Organisation Name"
  val processedDate = "01 Jan 2019"

  private val view = instanceOf[sub01_outcome_rejected]

  "'Sub01 Outcome Rejected' Page with name" should {

    "display correct title" in {
      docWithName().title() must startWith(messages("cds.sub01.outcome.rejected.subscribe.title"))
    }
    "display correct heading" in {
      docWithName().body.getElementsByTag("h1").text() must startWith(
        messages("cds.sub01.outcome.rejected.subscribe.title")
      )
    }
    "have the correct class on the h1" in {
      docWithName().body.getElementsByTag("h1").hasClass("govuk-heading-l") mustBe true
    }

    "have a no feedback 'continue' button when config missing" in {
      val link = docWithName(atarService.copy(feedbackUrl = None)).body.getElementById("feedback-continue")
      link mustBe null
    }

  }

  "'Sub01 Outcome Rejected' Page without name" should {

    "display correct heading" in {
      docWithoutName().body.getElementsByTag("h1").text() must startWith(
        messages("cds.sub01.outcome.rejected.subscribe.title")
      )
    }
    "have the correct class on the h1" in {
      docWithoutName().body.getElementsByTag("h1").hasClass("govuk-heading-l") mustBe true
    }

    "have a no feedback 'continue' button when config missing" in {
      val link = docWithoutName(atarService.copy(feedbackUrl = None)).body.getElementById("feedback-continue")
      link mustBe null
    }

  }

  def docWithName(service: Service = atarService): Document =
    Jsoup.parse(contentAsString(view(Some(orgName), processedDate, service)))

  def docWithoutName(service: Service = atarService): Document =
    Jsoup.parse(contentAsString(view(None, processedDate, service)))

}
