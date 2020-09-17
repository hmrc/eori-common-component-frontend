/*
 * Copyright 2020 HM Revenue & Customs
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
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.UtrMatchModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.match_organisation_utr
import util.ViewSpec

class MatchOrganisationUtrSpec extends ViewSpec {
  val form: Form[UtrMatchModel]                      = utrForm
  val formWithNoSelectionError: Form[UtrMatchModel]  = utrForm.bind(Map.empty[String, String])
  val formWithNoUtrEnteredError: Form[UtrMatchModel] = utrForm.bind(Map("have-utr" -> "true", "utr" -> ""))
  val isInReviewMode                                 = false
  val previousPageUrl                                = "/"
  val nonSoleTraderType                              = "charity-public-body-not-for-profit"
  val soleTraderType                                 = "sole-trader"
  implicit val request                               = withFakeCSRF(FakeRequest())

  private val view = app.injector.instanceOf[match_organisation_utr]

  "Match UTR page in the non sole trader case" should {
    "display correct title" in {
      doc.title must startWith("Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR) number?")
    }
    "have the correct h1 text" in {
      doc.body
        .getElementsByTag("h1")
        .text() mustBe "Does your organisation have a Corporation Tax Unique Taxpayer Reference (UTR) number?"
    }
    "have the correct class on the h1" in {
      doc.body.getElementsByTag("h1").hasClass("heading-large") mustBe true
    }
    "have an input of type 'radio' for Yes I have a UTR" in {
      doc.body.getElementById("have-utr-yes").attr("type") mustBe "radio"
    }
    "have an input of type 'radio' for No I don't have a UTR" in {
      doc.body.getElementById("have-utr-no").attr("type") mustBe "radio"
    }
    "have an input of type 'text' for UTR" in {
      doc.body.getElementById("utr").attr("type") mustBe "text"
    }
    "display correct intro paragraph" in {
      doc.body
        .getElementById("intro")
        .text() mustBe "Your organisation will have a Corporation Tax UTR number if you pay corporation tax. It is on tax returns and other letters from HMRC."
    }
    "display correct progressive disclosure heading" in {
      doc.body.getElementsByTag("summary").text() mustBe "Can't find your Corporation Tax UTR number?"
    }
    "display correct progressive disclosure content" in {
      doc.body
        .getElementById("details-content-1")
        .text() mustBe "This can be found on HMRC letters to your organisation, such as: 'Notice to deliver a Company Tax Return' (CT603) 'Corporation Tax notice' (CT610) Your accountant or tax manager would normally have your UTR."
    }
  }

  "Match UTR page in the sole trader case" should {
    "have the correct h1 text" in {
      docAsSoleTraderIndividual.body
        .getElementsByTag("h1")
        .text mustBe "Do you have a Self Assessment Unique Taxpayer Reference (UTR) number issued in the UK?"
    }
    "not show the link for corporation tax UTR number, for sole traders" in {
      docAsSoleTraderIndividual.body.getElementsByTag("summary").text mustBe ""
    }

    "not have any content for sole trader" in {
      docAsSoleTraderIndividual.body.getElementById("details-content-1") mustBe null
    }

    "show correctly display the non sole trader field label" in {
      docAsSoleTraderIndividual.body.getElementsByClass("form-label-bold").text.trim mustBe "Self Assessment UTR number"
    }

  }

  "Match UTR page without selecting any radio button in the non sole trader case" should {
    "display a field level error message" in {
      docWithNoSelectionError.body
        .getElementById("have-utr-field")
        .getElementsByClass("error-message")
        .text mustBe "Tell us if you have a UTR number"
    }
    "display a page level error message" in {
      docWithNoSelectionError.body
        .getElementsByClass("error-summary-list")
        .text mustBe "Tell us if you have a UTR number"
    }
    "display the correct problem message at the top of the page" in {
      docWithNoSelectionError.body
        .getElementById("errors")
        .text mustBe "There is a problem. Tell us if you have a UTR number"
    }
  }

  "Match Organisation UTR page without selecting any radio button in the sole trader case" should {
    "display a field level error message" in {
      docWithNoSelectionErrorAsSoleTrader.body
        .getElementById("have-utr-field")
        .getElementsByClass("error-message")
        .text mustBe "Tell us if you have a UTR number"
    }
    "display a page level error message" in {
      docWithNoSelectionErrorAsSoleTrader.body
        .getElementsByClass("error-summary-list")
        .text mustBe "Tell us if you have a UTR number"
    }
    "display the correct problem message at the top of the page" in {
      docWithNoSelectionErrorAsSoleTrader.body
        .getElementById("errors")
        .text mustBe "There is a problem. Tell us if you have a UTR number"
    }
  }

  "Match UTR page without filling in the UTR field as a non sole trader" should {
    "display a field level error message" in {
      docWithNoUtrEnteredError.body
        .getElementById("utr-outer")
        .getElementsByClass("error-message")
        .text mustBe "Enter your UTR number"
    }
    "display a page level error message" in {
      docWithNoUtrEnteredError.body.getElementsByClass("error-summary-list").text mustBe "Enter your UTR number"
    }
  }

  "Match UTR page without filling in the UTR field as a sole trader" should {
    "display a field level error message" in {
      docWithNoUtrEnteredErrorAsSoleTrader.body
        .getElementById("utr-outer")
        .getElementsByClass("error-message")
        .text mustBe "Enter your UTR number"
    }
    "display a page level error message" in {
      docWithNoUtrEnteredErrorAsSoleTrader.body
        .getElementsByClass("error-summary-list")
        .text mustBe "Enter your UTR number"
    }
  }

  lazy val doc: Document = getDoc(form)

  private def getDoc(form: Form[UtrMatchModel]) = {
    val result = view(form, nonSoleTraderType, "", Service.ATaR, Journey.Register)
    val doc    = Jsoup.parse(contentAsString(result))
    doc
  }

  lazy val docWithNoSelectionError: Document = {
    val result = view(formWithNoSelectionError, nonSoleTraderType, "", Service.ATaR, Journey.Register)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithNoUtrEnteredError: Document = {
    val result = view(formWithNoUtrEnteredError, nonSoleTraderType, "", Service.ATaR, Journey.Register)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docAsSoleTraderIndividual: Document = {
    val result = view(form, soleTraderType, "", Service.ATaR, Journey.Register)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithNoSelectionErrorAsSoleTrader: Document = {
    val result = view(formWithNoSelectionError, soleTraderType, "", Service.ATaR, Journey.Register)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithNoUtrEnteredErrorAsSoleTrader: Document = {
    val result = view(formWithNoUtrEnteredError, soleTraderType, "", Service.ATaR, Journey.Register)
    Jsoup.parse(contentAsString(result))
  }

}
