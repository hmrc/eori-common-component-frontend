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

package unit.views.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.data.Form
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.Partnership
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.CompanyShortNameViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm.subscriptionCompanyShortNameForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.business_short_name
import util.ViewSpec

class BusinessShortNameSpec extends ViewSpec {
  val form: Form[CompanyShortNameViewModel] = subscriptionCompanyShortNameForm

  implicit val request = withFakeCSRF(FakeRequest())

  private val view = instanceOf[business_short_name]

  "Business Short Name view" should {
    "display correct title" in {
      doc.title must startWith("Does your partnership use a shortened name?")
    }
    "have hint" in {
      doc.body
        .getElementById("use-short-name-hint")
        .text() must include("For example, Her Majesty's Revenue and Customs is known as HMRC")

    }
    "have aria-described-by on the fieldset" in {
      doc.body
        .getElementById("use-short-name-fieldset")
        .attr("aria-describedby") mustBe "use-short-name-hint"

    }
  }

  lazy val doc: Document = getDoc(form)

  private def getDoc(form: Form[CompanyShortNameViewModel]) = {
    val result = view(form, false, Partnership, atarService, Journey.Register)
    val doc    = Jsoup.parse(contentAsString(result))
    doc
  }

}
