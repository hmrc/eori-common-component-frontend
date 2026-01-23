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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms

import play.api.data.FieldMapping
import play.api.data.Forms.of
import play.api.data.validation._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.LocalDateFormatter

import java.time.LocalDate

object FormUtils {

  val messageKeyMandatoryField = "cds.error.mandatory.field"

  def formatInput(value: String): String                      = value.replaceAll(" ", "").toUpperCase
  def formatInput(maybeValue: Option[String]): Option[String] = maybeValue.map(value => formatInput(value))

  def maxDate(maximum: LocalDate, errorKey: String, args: Any*): Constraint[LocalDate] =
    Constraint {
      case date if date.isAfter(maximum) =>
        Invalid(errorKey, args: _*)
      case _ =>
        Valid
    }

  def minDate(minimum: LocalDate, errorKey: String, args: Any*): Constraint[LocalDate] =
    Constraint {
      case date if date.isBefore(minimum) =>
        Invalid(errorKey, args: _*)
      case _ =>
        Valid
    }

  def localDateUserInput(emptyKey: String, invalidKey: String, args: Seq[String] = Seq.empty): FieldMapping[LocalDate] =
    of(new LocalDateFormatter(emptyKey, invalidKey, args))

  def oneOf[T](validValues: Set[T]): T => Boolean = validValues.contains

  def lift[T](c: Constraint[T]): Constraint[Option[T]] =
    Constraint(c.name, c.args) {
      case Some(value) => c(value)
      case None        => Valid
    }

}
