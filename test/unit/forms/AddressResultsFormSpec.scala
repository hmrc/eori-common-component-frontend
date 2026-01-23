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

package unit.forms

import base.UnitSpec
import play.api.data.{Form, FormError}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressResultsForm

class AddressResultsFormSpec extends UnitSpec {

  val listOfValidAddresses: Seq[String] = Seq("house1", "house2", "house3")

  def form: Form[AddressResultsForm] = AddressResultsForm.form(listOfValidAddresses)

  "address result form " should {

    "returns an error if no address populated " in {
      val data = Map.empty[String, String]
      val res  = form.bind(data)
      res.errors shouldBe Seq(FormError("address", "ecc.address-lookup.postcode.address.error"))

    }
    "returns an error if wrong  address populated " in {
      val data = Map("address" -> "house4")
      val res  = form.bind(data)
      res.errors shouldBe Seq(FormError("address", "ecc.address-lookup.postcode.address.error"))

    }

    "bind the object if valid address is passed " in {
      val data = Map("address" -> "house1")
      val res  = form.bind(data)
      res.errors shouldBe Seq.empty
      res.value shouldBe Some(AddressResultsForm("house1"))

    }

  }

}
