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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription

import play.api.data.Forms.{text, _}
import play.api.data.validation._
import play.api.data.Form
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.FormValidation._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.Mappings
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel

object ContactAddressForm {
  private val Length2 = 2

  def contactAddressCreateForm(): Form[ContactAddressModel] =
    Form(
      mapping(
        "line-1"      -> text.verifying(validLine1),
        "line-2"      -> optional(text.verifying(validLine2)),
        "line-3"      -> text.verifying(validLine3),
        "line-4"      -> optional(text.verifying(validLine4)),
        "postcode"    -> postcodeMapping,
        "countryCode" -> Mappings.mandatoryString("cds.matching-error.country.invalid")(s => s.length == Length2)
      )(ContactAddressModel.apply)(ContactAddressModel.unapply)
    )

  def validLine1: Constraint[String] =
    Constraint({
      case s if s.trim.isEmpty => Invalid(ValidationError("cds.matching.organisation-address.line-1.error.empty"))
      case s if s.trim.length > 35 =>
        Invalid(ValidationError("cds.matching.organisation-address.line-1.error.too-long"))
      case _ => Valid
    })

  def validLine2: Constraint[String] =
    Constraint({
      case s if s.trim.length > 34 =>
        Invalid(ValidationError("cds.matching.organisation-address.line-2.error.too-long"))
      case _ => Valid
    })

  def validLine3: Constraint[String] =
    Constraint({
      case s if s.trim.isEmpty => Invalid(ValidationError("cds.matching.organisation-address.line-3.error.empty"))
      case s if s.trim.length > 34 =>
        Invalid(ValidationError("cds.matching.organisation-address.line-3.error.too-long"))
      case _ => Valid
    })

  def validLine4: Constraint[String] =
    Constraint({
      case s if s.trim.length > 35 =>
        Invalid(ValidationError("cds.matching.organisation-address.line-4.error.too-long"))
      case _ => Valid
    })

}
