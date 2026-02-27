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
        "line-1"   -> text.verifying(validLine1),
        "line-3"   -> text.verifying(validLine3),
        "postcode" -> optional(text.verifying(validEuEoriRegisteredAddressPostcode)),
        "countryCode" -> Mappings.mandatoryString("eu.eori.registered.address.country.error.empty")(s =>
          s.length == Length2
        )
      )(EuEoriRegisteredAddressModel.apply)(EuEoriRegisteredAddressModel =>
        Some(Tuple.fromProductTyped(EuEoriRegisteredAddressModel))
      )
    )

  private def validLine1: Constraint[String] =
    Constraint {
      case s if s.trim.isEmpty => Invalid(ValidationError("eu.eori.registered.address.line-1.error.empty"))
      case s if s.trim.length > 70 =>
        Invalid(ValidationError("eu.eori.registered.address.line-1.error.too-long"))
      case s if !s.matches(validCharsRegex) =>
        Invalid(ValidationError("eu.eori.registered.address.line-1.error.invalid-chars"))
      case _ => Valid
    }

  private def validLine3: Constraint[String] =
    Constraint {
      case s if s.trim.isEmpty => Invalid(ValidationError("eu.eori.registered.address.line-3.error.empty"))
      case s if s.trim.length > 35 =>
        Invalid(ValidationError("eu.eori.registered.address.line-3.error.too-long"))
      case s if !s.matches(validCharsRegex) =>
        Invalid(ValidationError("eu.eori.registered.address.line-3.error.invalid-chars"))
      case _ => Valid
    }

  private def validEuEoriRegisteredAddressPostcode: Constraint[String] =
    Constraint {
      case s if s.trim.length > 35 =>
        Invalid(ValidationError("eu.eori.registered.address.postcode.error.too-long"))
      case s if !s.replaceAll(" ", "").matches(euEoriPostcodeRegex.regex) =>
        Invalid(ValidationError("eu.eori.registered.address.postcode.error.invalid-chars"))
      case _ => Valid
    }

  private val euEoriPostcodeRegex: Regex = "^[A-Za-z0-9' .&-]*$".r
}
