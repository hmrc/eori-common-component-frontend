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
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.data.{Form, FormError}
import play.api.i18n.Lang.defaultLang
import play.api.i18n.{I18nSupport, Lang, Messages, MessagesApi, MessagesImpl}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.AddressDetailsForm

class AddressDetailsFormSpec extends UnitSpec with MockitoSugar with I18nSupport {

  val mockMessagesApi: MessagesApi = mock[MessagesApi]
  when(mockMessagesApi.preferred(Seq.empty)).thenReturn(MessagesImpl(Lang.defaultLang, mockMessagesApi))

  override def messagesApi: MessagesApi = mockMessagesApi
  implicit val messages: Messages       = MessagesImpl(defaultLang, messagesApi)

  "Address Details Form" should {
    "fail street validation" when {
      "street is empty" in {
        val formData                    = Map("street" -> "", "city" -> "London", "postcode" -> "SW3 5DA", "countryCode" -> "GB")
        val res: Form[AddressViewModel] = AddressDetailsForm.addressDetailsCreateForm().bind(formData)
        res.errors shouldBe Seq(FormError("street", "cds.subscription.address-details.street.empty.error"))
      }

      "street is longer than 70 characters" in {
        val formData = Map(
          "street"      -> "ofdwbagfpdisjafbddshfgdlsjgfdsaiuwpafdbsldgfsfjdofdwbagfpdisjafbddshfgdlsjgfdsaiuwpafdbsldgfsfjd",
          "city"        -> "London",
          "postcode"    -> "SW3 5DA",
          "countryCode" -> "GB"
        )
        val res: Form[AddressViewModel] = AddressDetailsForm.addressDetailsCreateForm().bind(formData)
        res.errors shouldBe Seq(FormError("street", "cds.subscription.address-details.street.too-long.error"))
      }

      "street contains invalid characters" in {
        val formData                    = Map("street" -> "^[^<>]+$", "city" -> "London", "postcode" -> "SW3 5DA", "countryCode" -> "GB")
        val res: Form[AddressViewModel] = AddressDetailsForm.addressDetailsCreateForm().bind(formData)
        res.errors shouldBe Seq(FormError("street", "cds.subscription.address-details.street.error.invalid-chars"))
      }

      "street contains more invalid characters" in {
        val formData                    = Map("street" -> "#1", "city" -> "London", "postcode" -> "SW3 5DA", "countryCode" -> "GB")
        val res: Form[AddressViewModel] = AddressDetailsForm.addressDetailsCreateForm().bind(formData)
        res.errors shouldBe Seq(FormError("street", "cds.subscription.address-details.street.error.invalid-chars"))
      }
    }

    "fail city validation" when {
      "city contains invalid characters" in {
        val formData                    = Map("street" -> "1", "city" -> "#London", "postcode" -> "SW3 5DA", "countryCode" -> "GB")
        val res: Form[AddressViewModel] = AddressDetailsForm.addressDetailsCreateForm().bind(formData)
        res.errors shouldBe Seq(FormError("city", "cds.subscription.address-details.page-error.city.invalid-chars"))
      }
    }

    "fail country validation" when {
      "country code is empty" in {
        val formData =
          Map("street" -> "Chambers Lane", "city" -> "London", "postcode" -> "SW3 5DA", "countryCode" -> "")
        val res: Form[AddressViewModel] = AddressDetailsForm.addressDetailsCreateForm().bind(formData)
        res.errors shouldBe Seq(FormError("countryCode", "cds.subscription.address-details.countryCode.error.label"))
      }
    }

    "fail postcode validation" when {
      "postcode is empty" in {
        val formData                    = Map("street" -> "33 Nine Elms Ln", "city" -> "London", "postcode" -> "", "countryCode" -> "GB")
        val res: Form[AddressViewModel] = AddressDetailsForm.addressDetailsCreateForm().bind(formData)
        res.errors shouldBe Seq(FormError("postcode", "cds.subscription.contact-details.error.postcode"))
      }

      "postcode is invalid" in {
        val formData =
          Map("street" -> "33 Nine Elms Ln", "city" -> "London", "postcode" -> "INVALID", "countryCode" -> "GB")
        val res: Form[AddressViewModel] = AddressDetailsForm.addressDetailsCreateForm().bind(formData)
        res.errors shouldBe Seq(FormError("postcode", "cds.subscription.contact-details.error.postcode"))
      }
    }
  }
}
