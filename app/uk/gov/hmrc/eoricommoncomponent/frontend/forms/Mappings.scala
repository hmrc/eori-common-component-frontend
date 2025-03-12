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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms

import play.api.data.Forms.{optional, text}
import play.api.data.Mapping
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationResult}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.FormUtils.messageKeyMandatoryField

object Mappings {

  def mandatoryString(
    onEmptyError: String
  )(constraintFunction: String => Boolean, error: => String = onEmptyError): Mapping[String] = {
    val constraint = Constraint((s: String) => if (constraintFunction.apply(s)) Valid else Invalid(error))
    mandatoryString(onEmptyError, Seq(constraint))
  }

  def mandatoryString(onEmptyError: String, constraints: Seq[Constraint[String]]): Mapping[String] =
    optional(text.verifying(nonEmptyString(onEmptyError)).verifying(constraints: _*))
      .verifying(onEmptyError, _.isDefined)
      .transform[String](o => o.get, s => Some(s))

  def nonEmptyString(error: => String = messageKeyMandatoryField): Constraint[String] = Constraint { s =>
    Option(s).filter(_.trim.nonEmpty).fold[ValidationResult](ifEmpty = Invalid(error))(_ => Valid)
  }

}
