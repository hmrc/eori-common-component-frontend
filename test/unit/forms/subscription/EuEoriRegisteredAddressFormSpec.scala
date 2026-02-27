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

package unit.forms.subscription

import base.UnitSpec
import play.api.data.{Form, FormError}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EuEoriRegisteredAddressModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.EuEoriRegisteredAddressForm

class EuEoriRegisteredAddressFormSpec extends UnitSpec {

  val form: Form[EuEoriRegisteredAddressModel] = EuEoriRegisteredAddressForm.euEoriRegisteredAddressCreateForm()

  "Eu Eori Registered Address Form" should {
    "accept valid address" in {
      val formData = Map(
        "line-1"      -> "33 Nine Elms Ln",
        "line-3"      -> "Battersea",
        "postcode"    -> "SW11 7US",
        "countryCode" -> "FR"
      )
      val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
      res.errors shouldBe empty
    }

    "fail address validation" when {
      "line 1 is empty" in {
        val formData = Map(
          "line-1"      -> "",
          "line-3"      -> "Battersea",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "FR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-1", "eu.eori.registered.address.line-1.error.empty"))
      }

      "line 1 is longer than 35 characters" in {
        val formData = Map(
          "line-1" -> "ofdwbagfpdisjafbddshfgdlsjgfdsaiuwpafdbsldgfsfjdofdwbagfpdisjafbddshfgdlsjgfdsaiuwpafdbsldgfsfjd",
          "line-3"      -> "London",
          "postcode"    -> "SW3 5DA",
          "countryCode" -> "FR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-1", "eu.eori.registered.address.line-1.error.too-long"))
      }

      "line 1 contains invalid characters" in {
        val formData = Map(
          "line-1"      -> "#1",
          "line-3"      -> "London",
          "postcode"    -> "SW3 5DA",
          "countryCode" -> "FR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-1", "eu.eori.registered.address.line-1.error.invalid-chars"))
      }

      "line 3 is empty" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-2"      -> "Nine Elms",
          "line-3"      -> "",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "FR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-3", "eu.eori.registered.address.line-3.error.empty"))
      }

      "line 3 contains invalid characters" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-3"      -> "#Battersea",
          "line-4"      -> "London",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "FR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("line-3", "eu.eori.registered.address.line-3.error.invalid-chars"))
      }

      "country code is empty" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-3"      -> "Battersea",
          "postcode"    -> "SW11 7US",
          "countryCode" -> ""
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("countryCode", "eu.eori.registered.address.country.error.empty"))
      }

      "country code is longer than 2 characters" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-3"      -> "Battersea",
          "postcode"    -> "SW11 7US",
          "countryCode" -> "FRR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("countryCode", "eu.eori.registered.address.country.error.empty"))
      }

      "postcode with 35 characters including space, apostrophe, full stop, ampersands and hyphens is valid" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-3"      -> "Battersea",
          "postcode"    -> "0123456789ABCdefghijklmnopqrst -&.'",
          "countryCode" -> "FR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe empty
      }

      "postcode is invalid" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-3"      -> "Battersea",
          "postcode"    -> "???",
          "countryCode" -> "FR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("postcode", "eu.eori.registered.address.postcode.error.invalid-chars"))
      }

      "postcode with 36 characters is too long" in {
        val formData = Map(
          "line-1"      -> "33 Nine Elms Ln",
          "line-3"      -> "Battersea",
          "postcode"    -> "0123456789abcdefghijklmnopqrstuvwxyz",
          "countryCode" -> "FR"
        )
        val res: Form[EuEoriRegisteredAddressModel] = form.bind(formData)
        res.errors shouldBe Seq(FormError("postcode", "eu.eori.registered.address.postcode.error.too-long"))
      }
    }
  }
}
