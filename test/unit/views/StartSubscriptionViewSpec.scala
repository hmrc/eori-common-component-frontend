/*
 * Copyright 2022 HM Revenue & Customs
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
import org.jsoup.nodes.Document
import play.api.mvc.Request
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.start_subscribe
import util.ViewSpec

class StartSubscriptionViewSpec extends ViewSpec {

  private val view = instanceOf[start_subscribe]

  implicit val request: Request[Any] = withFakeCSRF(fakeAtarSubscribeRequest)

  private val doc: Document = Jsoup.parse(contentAsString(view(atarService)))

  "Start subscription page" should {

    "display title" in {

      doc.title() must startWith("You must subscribe to use")
    }

    "display header" in {

      doc.body().getElementsByTag("h1").text() mustBe "You must subscribe to use Advance Tariff Rulings"
    }

    "display information about doing subscription once" in {

      doc.body().getElementById("information-only-once").text() mustBe "This only needs to be done once."
    }

    "display 'What you will need' paragraph" in {

      doc.body().getElementById("what-you-will-need").text() mustBe "What you will need"
      doc.body().getElementById(
        "what-you-will-need-text"
      ).text() mustBe "In order to subscribe you to Advance Tariff Rulings, we need some information from you. Ensure you have all the correct details with you before you start otherwise your application may be delayed."
    }

    "display `GB Eori` paragraph with all content" in {

      doc.body().getElementById("gb-eori").text() mustBe "An EORI number starting with GB"
      doc.body().getElementById(
        "gb-eori-text"
      ).text() startsWith "You must get an EORI number (opens in new window or tab) before you can apply for Advance Tariff Rulings, once you have applied for an EORI number it usually takes around 30 minutes, but may take up to 48 hours before you can proceed with your Advance Tariff Rulings application."

      val warning     = doc.body().getElementsByClass("govuk-warning-text").get(0)
      val warningMark = warning.getElementsByClass("govuk-warning-text__icon").get(0)
      val warningText = warning.getElementsByClass("govuk-warning-text__text").get(0)

      warningMark.attr("aria-hidden") mustBe "true"
      warningMark.text() mustBe "!"

      warningText.text() mustBe "Warning The EORI number starting with GB must be the one linked to the Government Gateway user ID you used when you got an EORI number."

    }

    "display organisation information" in {

      doc.body().getElementById("organisation").text() mustBe "Company or other organisation details"
      doc.body().getElementById(
        "organisation-text"
      ).text() mustBe "If you are a limited company, partnership or charity, you will need:"

      val bulletList = doc.body().getElementsByClass("govuk-list--bullet").get(0)

      bulletList.getElementsByTag("li").get(
        0
      ).text() mustBe "Corporation Tax Unique Taxpayer Reference (UTR) if you pay Corporation Tax in the UK. You can find a UTR number (opens in new tab)."
      bulletList.getElementsByTag("li").get(1).text() mustBe "Registered company name"
      bulletList.getElementsByTag("li").get(2).text() mustBe "Registered company address"
      bulletList.getElementsByTag("li").get(3).text() mustBe "Date of establishment"
    }

    "display individual information" in {

      doc.body().getElementById("individual").text() mustBe "Sole trader or individual details"
      doc.body().getElementById(
        "individual-text"
      ).text() mustBe "If you have worked in the UK or registered for self-assessment, you will need one of the following:"

      val bulletList = doc.body().getElementsByClass("govuk-list--bullet").get(1)

      bulletList.getElementsByTag("li").get(0).text() mustBe "National Insurance number"
      bulletList.getElementsByTag("li").get(
        1
      ).text() mustBe "Self Assessment Unique Taxpayer Reference (UTR). You can find a lost UTR number (opens in new tab)."
    }

    "display 2 hours message" in {

      doc.body().getElementById(
        "email-confirmation"
      ).text() mustBe "We’ll process your application and email you with the result within 2 hours."
    }

    "display 'Continue' button which links to check-user url" in {

      val continueButton = doc.body().getElementsByClass("govuk-button")

      continueButton.text() mustBe "Continue"
      continueButton.attr("href") mustBe "/customs-enrolment-services/atar/subscribe/check-user"
    }
  }
}
