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

package unit.forms

import base.UnitSpec
import play.api.data.{Form, FormError}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.ContactAddressForm

class ContactAddressFormSpec extends UnitSpec {

  val form: Form[ContactAddressModel] = ContactAddressForm.contactAddressCreateForm()

  "Contact Address Form" should {
    "accept valid address" in {
      val formData = Map(
        "line-1"      -> "33 Nine Elms Ln",
        "line-2"      -> "Nine Elms",
        "line-3"      -> "Battersea",
        "line-4"      -> "London",
        "postcode"    -> "SW11 7US",
        "countryCode" -> "GB"
      )
      val res: Form[ContactAddressModel] = form.bind(formData)
      res.errors shouldBe empty
    }

    "fail address validation" when {
      "line 1 is empty" in {
        val formData = Map(
          "line-1"      -> "",
          "line-2"      -> "Nine Elms",
          "line-3"      -> "Battersea",
          "line-4"      -> "London",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-1", "cds.matching.organisation-address.line-1.error.empty"))
      }

      "line 1 is longer than 35 characters" in {
        val formData = Map(
          "line-1" -> "ofdwbagfpdisjafbddshfgdlsjgfdsaiuwpafdbsldgfsfjdofdwbagfpdisjafbddshfgdlsjgfdsaiuwpafdbsldgfsfjd",
          "line-2"      -> "Battersea",
          "line-3"      -> "London",
          "postcode"    -> "SW3 5DA",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-1", "cds.matching.organisation-address.line-1.error.too-long"))
      }

      "line 1 contains invalid characters" in {
        val formData = Map(
          "line-1"      -> "#1",
          "line-2"      -> "Battersea",
          "line-3"      -> "London",
          "postcode"    -> "SW3 5DA",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-1", "cds.matching.organisation-address.line-1.error.invalid-chars"))
      }

      "line 2 is longer than 34 characters" in {
        val formData = Map(
          "line-1" -> "line 1",
          "line-2" -> "ofdwbagfpdisjafbddshfgdlsjgfdsaiuwpafdbsldgfsfjdofdwbagfpdisjafbddshfgdlsjgfdsaiuwpafdbsldgfsfjd",
          "line-3"      -> "London",
          "postcode"    -> "SW3 5DA",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-2", "cds.matching.organisation-address.line-2.error.too-long"))
      }

      "line 2 contains invalid characters" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-2"      -> "#Nine Elms",
          "line-3"      -> "Battersea",
          "line-4"      -> "London",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-2", "cds.matching.organisation-address.line-2.error.invalid-chars"))
      }

      "line 3 is empty" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-2"      -> "Nine Elms",
          "line-3"      -> "",
          "line-4"      -> "London",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-3", "cds.matching.organisation-address.line-3.error.empty"))
      }

      "line 3 contains invalid characters" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-2"      -> "Nine Elms",
          "line-3"      -> "#Battersea",
          "line-4"      -> "London",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-3", "cds.matching.organisation-address.line-3.error.invalid-chars"))
      }

      "line 4 contains invalid characters" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-2"      -> "Nine Elms",
          "line-3"      -> "Battersea",
          "line-4"      -> "#London",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-4", "cds.matching.organisation-address.line-4.error.invalid-chars"))
      }

      "country code is empty" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-2"      -> "Nine Elms",
          "line-3"      -> "Battersea",
          "line-4"      -> "London",
          "postcode"    -> "SW11 7US",
          "countryCode" -> ""
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("countryCode", "cds.matching-error.country.invalid"))
      }

      "country code is longer than 2 characters" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-2"      -> "Nine Elms",
          "line-3"      -> "Battersea",
          "line-4"      -> "London",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "GBR"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("countryCode", "cds.matching-error.country.invalid"))
      }

      "postcode is invalid" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-2"      -> "Nine Elms",
          "line-3"      -> "Battersea",
          "line-4"      -> "London",
          "postcode"    -> "INVALID",
          "countryCode" -> "GB"
        )
        val res: Form[ContactAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("postcode", "cds.subscription.contact-details.error.postcode"))
      }
    }
  }
}
