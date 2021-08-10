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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms

import java.time.LocalDate
import play.api.data.Mapping
import play.api.data.validation._
import uk.gov.hmrc.eoricommoncomponent.frontend.playext.mappers.DateTuple._

object FormUtils {

  val messageKeyMandatoryField    = "cds.error.mandatory.field"
  val messageKeyInvalidDateFormat = "cds.error.invalid.date.format"
  val messageKeyFutureDate        = "cds.error.future-date"

  def formatInput(value: String): String                      = value.replaceAll(" ", "").toUpperCase
  def formatInput(maybeValue: Option[String]): Option[String] = maybeValue.map(value => formatInput(value))

  def mandatoryDate(
    onEmptyError: String = messageKeyMandatoryField,
    onInvalidDateError: String = messageKeyInvalidDateFormat,
    minYear: Int
  ): Mapping[LocalDate] =
    dateTuple(onInvalidDateError, minYear)
      .verifying(onEmptyError, d => d.isDefined)
      .transform(_.get, Option(_))

  def mandatoryDateTodayOrBefore(
    onEmptyError: String = messageKeyMandatoryField,
    onInvalidDateError: String = messageKeyInvalidDateFormat,
    onDateInFutureError: String = messageKeyFutureDate,
    minYear: Int
  ): Mapping[LocalDate] =
    mandatoryDate(onEmptyError, onInvalidDateError, minYear)
      .verifying(
        onDateInFutureError,
        d => {
          val today = LocalDate.now()
          d.isEqual(today) || d.isBefore(today)
        }
      )

  def oneOf[T](validValues: Set[T]): T => Boolean = validValues.contains

  def lift[T](c: Constraint[T]): Constraint[Option[T]] =
    Constraint(c.name, c.args) {
      case Some(value) => c(value)
      case None        => Valid
    }

}
