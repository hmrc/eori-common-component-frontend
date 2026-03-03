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
import play.api.mvc.{AnyContent, Request}
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.{ContactAddressForm, EuEoriRegisteredAddressForm}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Countries
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.{contact_address, eu_eori_registered_address}
import util.ViewSpec

class EuEoriRegisteredAddressSpec extends ViewSpec {

  private val view = instanceOf[eu_eori_registered_address]

  implicit val request: Request[AnyContent] = withFakeCSRF(fakeAtarSubscribeRequest)

  private val form = EuEoriRegisteredAddressForm.euEoriRegisteredAddressCreateForm()

  val (countries, picker) = Countries.getCountryParametersForAllCountries()

  private val formWithError = form.bind(Map("line-1" -> "", "line-3" -> "", "countryCode" -> ""))

  private val doc: Document =
    Jsoup.parse(contentAsString(view(form, countries, picker, isInReviewMode = false, atarService)))

  private val docWithErrorSummary: Document =
    Jsoup.parse(contentAsString(view(formWithError, countries, picker, isInReviewMode = false, atarService)))

  "Eu Eori registered address view" should {

    "display correct title" in {

      doc.title() must startWith("What is your organisation's registered address?")
    }
    "display hint" in {

      doc.body().getElementById(
        "eu-euro-registered-address-hint"
      ).text() mustBe "Enter the address you used when you applied for an EORI number."
    }
    "display Address Line1 input" in {

      doc.body().getElementsByClass("line-1").text() mustBe "Street and number"
    }
    "display Town or city input" in {

      doc.body().getElementsByClass("line-3").text() mustBe "Town or city"
    }
    "display Postcode input" in {

      doc.body().getElementsByClass("postCode").text() mustBe "Postal code (optional)"
    }

    "display continue button" in {

      doc.body().getElementById("continue-button").text() mustBe "Continue"

    }

    "display error summary" in {

      val errorSummaryDiv = docWithErrorSummary.getElementsByClass("govuk-error-summary__list").first
      val errorList       = errorSummaryDiv.getElementsByTag("li")

      docWithErrorSummary.getElementsByClass("govuk-error-summary__title").text() mustBe "There is a problem"
      errorList.get(0).text() mustBe "Enter a street and number"
      errorList.get(1).text() mustBe "Enter a town or city"
      errorList.get(2).text() mustBe messages("eu.eori.registered.address.country.error.empty")
    }

  }
}
