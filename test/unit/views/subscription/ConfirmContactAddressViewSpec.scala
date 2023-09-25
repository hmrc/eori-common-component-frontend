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
import play.api.data.Form
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.YesNo
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailForm
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.confirm_contact_address
import util.ViewSpec

class ConfirmContactAddressViewSpec extends ViewSpec {

  private val address = ContactAddressViewModel(
    lineOne = "Line 1",
    lineTwo = Some("Line 2"),
    lineThree = "Town",
    lineFour = Some("Region"),
    postcode = Some("SE28 1AA"),
    country = "GB"
  )

  private val form: Form[YesNo]                                 = EmailForm.confirmContactAddressYesNoAnswerForm()
  private implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(FakeRequest())

  private val view = instanceOf[confirm_contact_address]

  "Confirm Contact Address" should {
    "display correct title" in {
      doc.title() must startWith("Is this your contact address")
    }
    "have the correct h1 text" in {
      doc
        .body()
        .getElementsByTag("h1")
        .text() mustBe "Is this your contact address?"
    }
    "have the correct class on the h1" in {
      doc.body().getElementsByTag("h1").hasClass("govuk-heading-l") mustBe true
    }
    "have the address" in {
      doc.body().getElementById("address").text() mustBe "Line 1 Line 2 Town Region SE28 1AA United Kingdom"
    }
    "have the right heading before YesNo" in {
      doc
        .body()
        .getElementsByTag("legend")
        .text() mustBe "Is this address correct?"
    }
    "have an input of type 'radio' for Yes option" in {
      doc.body().getElementById("yes-no-answer-true").attr("type") mustBe "radio"
    }
    "have the right text on the Yes option" in {
      doc
        .body()
        .getElementsByAttributeValue("for", "yes-no-answer-true")
        .text() mustBe "Yes, this address is correct"
    }
    "have an input of type 'radio' for No option" in {
      doc.body().getElementById("yes-no-answer-false").attr("type") mustBe "radio"
    }
    "have the right text on the No option" in {
      doc
        .body()
        .getElementsByAttributeValue("for", "yes-no-answer-false")
        .text() mustBe "No, I want to change the address"
    }
  }

  private lazy val doc: Document = {
    val result = view(form, atarService, address)
    Jsoup.parse(contentAsString(result))
  }

}
