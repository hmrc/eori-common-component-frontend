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
import play.api.mvc.Request
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{AddressLookupParams, AddressResultsForm}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.address.AddressLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.address_lookup_results
import util.ViewSpec

class AddressLookupResultsViewSpec extends ViewSpec {

  private val view = instanceOf[address_lookup_results]

  implicit val request: Request[Any] = withFakeCSRF(fakeAtarSubscribeRequest)

  private val params = AddressLookupParams("AA11 1AA", Some("Flat 1"))

  private val allowedAddress =
    Seq(AddressLookup("Line 1", "City", "BB11 1BB", "GB"), AddressLookup("Line 1", "City2", "BB11 1BC", "GB"))

  private val form = AddressResultsForm.form(allowedAddress.map(_.dropDownView))

  private val formWithError =
    AddressResultsForm.form(allowedAddress.map(_.dropDownView)).bind(Map("address" -> "invalid"))

  private def doc(selectedOrganisationType: CdsOrganisationType = Company): Document =
    Jsoup.parse(contentAsString(view(form, params, allowedAddress, false, selectedOrganisationType, atarService)))

  private val reviewDoc: Document =
    Jsoup.parse(contentAsString(view(form, params, allowedAddress, true, Company, atarService)))

  private val docWithErrorSummary =
    Jsoup.parse(contentAsString(view(formWithError, params, allowedAddress, false, Company, atarService)))

  "Address Lookup Postcode page" should {

    "display title for company" in {

      doc().title() must startWith("Select your address")
    }

    "display header for company" in {

      doc().body().getElementsByTag("h1").text() mustBe "Select your address"
    }

    "display title for individual" in {

      doc(SoleTrader).title() must startWith("Select your address")
    }

    "display header for individual" in {

      doc(SoleTrader).body().getElementsByTag("h1").text() mustBe "Select your address"
    }

    "display title for partnership" in {

      doc(Partnership).title() must startWith("Select your address")
    }

    "display header for partnership" in {

      doc(Partnership).body().getElementsByTag("h1").text() mustBe "Select your address"
    }

    "display title for charity" in {

      doc(CharityPublicBodyNotForProfit).title() must startWith("Select your address")
    }

    "display header for charity" in {

      doc(CharityPublicBodyNotForProfit).body().getElementsByTag("h1").text() mustBe "Select your address"
    }

    "display address selection list" in {
      val document = doc().body()

      document.getElementById("addressId").`val`() mustBe "Line 1, City, BB11 1BB"
      document.getElementById("addressId-1").`val`() mustBe "Line 1, City2, BB11 1BC"
    }

    "display manual address link" in {

      val manualAddressLink = doc().body().getElementById("cannot-find-address")

      manualAddressLink.text() mustBe "Enter your address manually"
      manualAddressLink.attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address"
    }

    "display review manual address link" in {

      val manualAddressLink = reviewDoc.body().getElementById("cannot-find-address")

      manualAddressLink.text() mustBe "Enter your address manually"
      manualAddressLink.attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"
    }

    "display Continue button" in {

      doc().body().getElementById("continue-button").text() mustBe "Continue"
    }

    "display error summary" in {

      docWithErrorSummary.getElementsByClass("govuk-error-summary__title").text() mustBe "There is a problem"
      docWithErrorSummary.getElementsByClass(
        "govuk-error-summary__list"
      ).text() mustBe "Please select address from the list"
    }
  }
}
