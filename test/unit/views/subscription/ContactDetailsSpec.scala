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
import play.api.mvc.Request
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.ContactDetailsForm
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.contact_details
import util.ViewSpec

class ContactDetailsSpec extends ViewSpec {

  private val view = instanceOf[contact_details]

  implicit val request: Request[Any] = withFakeCSRF(fakeAtarSubscribeRequest)

  private val email = "john.doe@example.com"
  private val form  = ContactDetailsForm.form()

  private val formWithError = form.bind(Map("full-name" -> "", "telephone" -> ""))

  private val doc: Document = Jsoup.parse(contentAsString(view(form, email, false, atarService)))

  private val docWithErrorSummary: Document =
    Jsoup.parse(contentAsString(view(formWithError, email, false, atarService)))

  "Contact details view" should {

    "display correct title" in {

      doc.title() must startWith("Who can we contact?")
    }

    "display correct header" in {

      doc.body().getElementsByTag("h1").text() mustBe "Who can we contact?"
    }

    "display full name input" in {

      doc.body().getElementsByClass("full-name").text() mustBe "Full name"
    }

    "display continue button" in {

      doc.body().getElementById("continue-button").text() mustBe "Continue"

    }

    "display error summary" in {

      val errorSummaryDiv = docWithErrorSummary.getElementsByClass("govuk-error-summary__list").first
      val errorList       = errorSummaryDiv.getElementsByTag("li")

      docWithErrorSummary.getElementsByClass("govuk-error-summary__title").text() mustBe "There is a problem"
      errorList.get(0).text() mustBe "Enter your contact name"
    }
  }
}
