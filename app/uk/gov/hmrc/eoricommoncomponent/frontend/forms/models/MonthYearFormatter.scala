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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.models

import play.api.data.FormError
import play.api.data.format.Formatter

import java.time.LocalDate
import scala.util.{Failure, Success, Try}

/**
 * Formatter that binds month+year inputs to a LocalDate with day fixed to 1.
 * Expected form fields: s"$key.month", s"$key.year".
 */
class MonthYearFormatter(emptyKey: String, invalidKey: String, args: Seq[String] = Seq.empty)
    extends Formatter[LocalDate] with Formatters {

  private val monthKey = "month"
  private val yearKey  = "year"

  private def toDate(key: String, month: Int, year: Int): Either[Seq[FormError], LocalDate] =
    Try(LocalDate.of(year, month, 1)) match {
      case Success(date) => Right(date)
      case Failure(_)     => Left(Seq(FormError(key, invalidKey, args)))
    }

  private def formatDate(key: String, data: Map[String, String]): Either[Seq[FormError], LocalDate] = {

    val int = intFormatter(requiredKey = invalidKey, wholeNumberKey = invalidKey, nonNumericKey = invalidKey, args)

    for {
      month <- int.bind(s"$key.month", data.map(m => m._1 -> m._2.trim))
      year  <- int.bind(s"$key.year", data.map(y => y._1 -> y._2.trim))
      _     <- validateFields(key, month, year)
      date  <- toDate(key, month, year)
    } yield date
  }

  private def validateFields(key: String, month: Int, year: Int): Either[Seq[FormError], Unit] = {

    val errors: List[FormError] = List(
      if (month > 12) Some(FormError(s"$key.$monthKey", "date.month.error", args)) else None,
      if (year < 1000) Some(FormError(s"$key.$yearKey", "date-invalid-year-too-short", args)) else None,
    ).flatten

    errors match {
      case Nil => Right(())
      case _   => Left(errors)
    }
  }

  override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], LocalDate] = {

    val monthOpt = data.get(s"$key.month").filter(_.nonEmpty)
    val yearOpt  = data.get(s"$key.year").filter(_.nonEmpty)

    (monthOpt, yearOpt) match {
      case (Some(_), Some(_)) => formatDate(key, data).left.map(leftValue => leftValue.map(fe => fe))
      case (Some(_), None)    => Left(List(FormError(s"$key.$yearKey", s"$key.$yearKey.empty", args)))
      case (None, Some(_))    => Left(List(FormError(s"$key.$monthKey", s"$key.$monthKey.empty", args)))
      case _                  => Left(List(FormError(key, emptyKey, args)))
    }
  }

  override def unbind(key: String, value: LocalDate): Map[String, String] =
    Map(
      s"$key.month" -> value.getMonthValue.toString,
      s"$key.year"  -> value.getYear.toString
    )

}

