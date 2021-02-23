/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.data.FormError
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.ContactDetailsForm

class ContactDetailsFormSpec extends UnitSpec {

  private val form = ContactDetailsForm.form()

  "Contact Details Form" should {

    "return errors" when {

      "full name and telephone is empty" in {

        val formData = Map("full-name" -> "", "telephone" -> "")

        val boundForm = form.bind(formData)

        val fullNameError  = FormError("full-name", "cds.subscription.contact-details.form-error.full-name")
        val telephoneError = FormError("telephone", "cds.contact-details.page-error.telephone.isEmpty")

        val expectedErrors = Seq(fullNameError, telephoneError)

        boundForm.errors shouldBe expectedErrors
      }

      "full name and telephone is too long" in {

        val fullName  = Seq.fill(71)("a").mkString("")
        val telephone = Seq.fill(25)(1).mkString("")
        val formData  = Map("full-name" -> fullName, "telephone" -> telephone)

        val boundForm = form.bind(formData)

        val fullNameError  = FormError("full-name", "cds.subscription.full-name.error.too-long")
        val telephoneError = FormError("telephone", "cds.contact-details.page-error.telephone.wrong-length.too-long")

        val expectedErrors = Seq(fullNameError, telephoneError)

        boundForm.errors shouldBe expectedErrors
      }

      "telephone don't match the regex" in {

        val formData = Map("full-name" -> "Full name", "telephone" -> "!@£$%^&")

        val boundForm = form.bind(formData)

        val telephoneError = FormError("telephone", "cds.contact-details.page-error.telephone.wrong-format")

        val expectedErrors = Seq(telephoneError)

        boundForm.errors shouldBe expectedErrors
      }
    }

    "return no errors" when {

      "full name and telephone is correct" in {

        val formData = Map("full-name" -> "Full name", "telephone" -> "01234123123")

        val boundForm = form.bind(formData)

        boundForm.errors shouldBe Seq.empty
      }
    }
  }
}
