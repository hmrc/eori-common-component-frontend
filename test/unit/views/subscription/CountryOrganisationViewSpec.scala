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

package unit.views.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.Request
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.CompanyRegisteredCountry
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Countries
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.country_organisation
import util.ViewSpec

class CountryOrganisationViewSpec extends ViewSpec {

  private val view = instanceOf[country_organisation]

  implicit val request: Request[Any] = withFakeCSRF(fakeAtarSubscribeRequest)

  private val form = CompanyRegisteredCountry.form("ecc.registered-company-country.organisation.error")

  private val formWithError = form.bind(Map("countryCode" -> ""))

  val (countries, picker) = Countries.getCountryParametersForAllCountries()

  private val doc: Document = Jsoup.parse(contentAsString(view(form, countries, picker, atarService, false)))

  private val docWithErrorSummary: Document =
    Jsoup.parse(contentAsString(view(formWithError, countries, picker, atarService, false)))

  "Country view" should {

    "display correct title" in {

      doc.title() must startWith("In which country is your organisation registered?")
    }

    "display correct header" in {

      doc.body().getElementsByTag("h1").text() mustBe "In which country is your organisation registered?"
    }

    "display input with Country label" in {

      doc.body().getElementsByTag("label").text() must startWith("Country")
    }

    "display continue button" in {

      doc.body().getElementsByClass("govuk-button").text() mustBe "Continue"
    }

    "display error summary" in {

      docWithErrorSummary.getElementById("error-summary-title").text() mustBe "There is a problem"
      docWithErrorSummary.getElementsByClass(
        "govuk-error-summary__list"
      ).text() mustBe "Enter the country in which your organisation is registered"
    }
  }
}
