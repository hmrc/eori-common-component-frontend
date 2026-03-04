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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription

import play.api.data.Form
import play.api.data.Forms.*
import play.api.data.validation.*
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.FormValidation.*
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.Mappings
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EuEoriRegisteredAddressModel

import scala.util.matching.Regex

object EuEoriRegisteredAddressForm {
  private val Length2 = 2

  def euEoriRegisteredAddressCreateForm(): Form[EuEoriRegisteredAddressModel] =
    Form(
      mapping(
        "line-1"   -> text.verifying(addressLine("line-1", 70)),
        "line-3"   -> text.verifying(addressLine("line-3", 35)),
        "postcode" -> optional(text.verifying(addressLine("postcode", 35))),
        "countryCode" -> Mappings.mandatoryString("eu.eori.registered.address.country.error.empty")(s =>
          s.length == Length2
        )
      )(EuEoriRegisteredAddressModel.apply)(EuEoriRegisteredAddressModel =>
        Some(Tuple.fromProductTyped(EuEoriRegisteredAddressModel))
      )
    )

  private def addressLine(fieldName: String, maxLength: Int): Constraint[String] =
    Constraint { s =>
      val trimmed = s.trim
      val prefix  = s"eu.eori.registered.address.$fieldName.error"

      trimmed match {
        case t if t.isEmpty                                                        => Invalid(s"$prefix.empty")
        case t if t.length > maxLength                                             => Invalid(s"$prefix.too-long")
        case t if fieldName == "postcode" && !t.matches(euEoriPostcodeRegex.regex) => Invalid(s"$prefix.invalid-chars")
        case t if fieldName.startsWith("line") && !t.matches(validCharsRegex)      => Invalid(s"$prefix.invalid-chars")
        case _                                                                     => Valid
      }
    }

  private val euEoriPostcodeRegex: Regex = "^[A-Za-z0-9' .&-]*$".r
}
