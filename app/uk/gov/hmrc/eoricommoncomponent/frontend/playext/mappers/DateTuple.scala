/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.eoricommoncomponent.frontend.playext.mappers

import org.joda.time.LocalDate
import play.api.data.Forms.{text, tuple}
import play.api.data.Mapping
import uk.gov.hmrc.play.mappers.DateFields._
import uk.gov.voa.play.form.ConditionalMappings._
import uk.gov.voa.play.form._

import scala.util.{Success, Try}

object DateTuple {

  def dateTuple(
    dateField: String,
    invalidDateError: String = "cds.error.invalid.date.format"
  ): Mapping[Option[LocalDate]] = {
    def tuple2Date(tuple: (Option[String], Option[String], Option[String])) = tuple match {
      case (Some(day), Some(month), Some(year)) =>
        try Some(new LocalDate(year.trim.toInt, month.trim.toInt, day.trim.toInt))
        catch {
          case _: Throwable => None
        }

      case _ => None
    }

    def date2Tuple(maybeDate: Option[LocalDate]) = maybeDate match {
      case Some(d) => (Some(d.getDayOfMonth.toString), Some(d.getMonthOfYear.toString), Some(d.getYear.toString))
      case _       => (None, None, None)
    }

    dateTupleMapping(dateField)
      .verifying(
        invalidDateError,
        _ match {
          case (Some(day), Some(month), Some(year)) =>
            Try {
              new LocalDate(year.trim.toInt, month.trim.toInt, day.trim.toInt)
            }.isSuccess

          case _ => true
        }
      )
      .transform[Option[LocalDate]](tuple2Date, date2Tuple)
  }

  private val isInRange: (Int, Int) => String => Boolean = (min: Int, max: Int) =>
    (input: String) =>
      Try(input.trim.toInt) match {
        case Success(value) => value >= min && value <= max
        case _              => false
      }

  private def hasBothValuesFor(dateField: String, field1: String, field2: String): Condition =
    isNotEqual(s"$dateField.$field1", "") and isNotEqual(s"$dateField.$field2", "")

  private def dayMapping(dateField: String): Mapping[Option[String]] =
    mandatoryIf(hasBothValuesFor(dateField, month, year), text().verifying("date.day.error", isInRange(1, 31)))

  private def monthMapping(dateField: String): Mapping[Option[String]] =
    mandatoryIf(hasBothValuesFor(dateField, day, year), text().verifying("date.month.error", isInRange(1, 12)))

  private def yearMapping(dateField: String): Mapping[Option[String]] =
    mandatoryIf(hasBothValuesFor(dateField, day, month), text().verifying("date.year.error", isInRange(1000, 9999)))

  private def dateTupleMapping(dateField: String): Mapping[(Option[String], Option[String], Option[String])] =
    tuple(day -> dayMapping(dateField), month -> monthMapping(dateField), year -> yearMapping(dateField))

}
