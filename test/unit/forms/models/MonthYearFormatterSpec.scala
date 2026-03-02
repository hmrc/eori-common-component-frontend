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

package unit.forms.models

import base.UnitSpec
import play.api.data.FormError
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.MonthYearFormatter

import java.time.LocalDate

class MonthYearFormatterSpec extends UnitSpec {

  private val emptyKey   = "wei.error.empty-date"
  private val invalidKey = "wei.error.invalid-date"
  private val fieldKey   = "when-eori-issued"

  private val formatter = new MonthYearFormatter(emptyKey, invalidKey)

  "MonthYearFormatter" should {

    "bind valid month and year to a LocalDate using day 1" in {
      val data = Map(
        s"$fieldKey.month" -> "3",
        s"$fieldKey.year"  -> "2017"
      )

      formatter.bind(fieldKey, data) shouldBe Right(LocalDate.of(2017, 3, 1))
    }

    "trim whitespace before binding values" in {
      val data = Map(
        s"$fieldKey.month" -> "  11  ",
        s"$fieldKey.year"  -> "  2020 "
      )

      formatter.bind(fieldKey, data) shouldBe Right(LocalDate.of(2020, 11, 1))
    }

    "return an empty-date error when month and year are both missing" in {
      formatter.bind(fieldKey, Map.empty) shouldBe Left(Seq(FormError(fieldKey, emptyKey, Seq.empty)))
    }

    "return a year-empty error when month is present and year is missing" in {
      val data = Map(s"$fieldKey.month" -> "5")

      formatter.bind(fieldKey, data) shouldBe Left(
        Seq(FormError(s"$fieldKey.year", s"$fieldKey.year.empty", Seq.empty))
      )
    }

    "return a month-empty error when year is present and month is missing" in {
      val data = Map(s"$fieldKey.year" -> "2020")

      formatter.bind(fieldKey, data) shouldBe Left(
        Seq(FormError(s"$fieldKey.month", s"$fieldKey.month.empty", Seq.empty))
      )
    }

    "return invalid-date for non-numeric month values" in {
      val data = Map(
        s"$fieldKey.month" -> "aa",
        s"$fieldKey.year"  -> "2020"
      )

      formatter.bind(fieldKey, data) shouldBe Left(Seq(FormError(s"$fieldKey.month", invalidKey, Seq.empty)))
    }

    "return invalid-date for non-numeric year values" in {
      val data = Map(
        s"$fieldKey.month" -> "2",
        s"$fieldKey.year"  -> "twenty"
      )

      formatter.bind(fieldKey, data) shouldBe Left(Seq(FormError(s"$fieldKey.year", invalidKey, Seq.empty)))
    }

    "return month error when month is greater than 12" in {
      val data = Map(
        s"$fieldKey.month" -> "13",
        s"$fieldKey.year"  -> "2020"
      )

      formatter.bind(fieldKey, data) shouldBe Left(Seq(FormError(s"$fieldKey.month", "date.month.error", Seq.empty)))
    }

    "return year-too-short error when year has fewer than 4 digits" in {
      val data = Map(
        s"$fieldKey.month" -> "12",
        s"$fieldKey.year"  -> "999"
      )

      formatter.bind(fieldKey, data) shouldBe Left(
        Seq(FormError(s"$fieldKey.year", "date-invalid-year-too-short", Seq.empty))
      )
    }

    "return both month and year validation errors when both inputs are out of bounds" in {
      val data = Map(
        s"$fieldKey.month" -> "13",
        s"$fieldKey.year"  -> "999"
      )

      formatter.bind(fieldKey, data) shouldBe Left(
        Seq(
          FormError(s"$fieldKey.month", "date.month.error", Seq.empty),
          FormError(s"$fieldKey.year", "date-invalid-year-too-short", Seq.empty)
        )
      )
    }

    "return invalid-date when month is zero" in {
      val data = Map(
        s"$fieldKey.month" -> "0",
        s"$fieldKey.year"  -> "2020"
      )

      formatter.bind(fieldKey, data) shouldBe Left(Seq(FormError(fieldKey, invalidKey, Seq.empty)))
    }

    "unbind a LocalDate into month/year fields" in {
      formatter.unbind(fieldKey, LocalDate.of(2018, 9, 12)) shouldBe Map(
        s"$fieldKey.month" -> "9",
        s"$fieldKey.year"  -> "2018"
      )
    }
  }
}
