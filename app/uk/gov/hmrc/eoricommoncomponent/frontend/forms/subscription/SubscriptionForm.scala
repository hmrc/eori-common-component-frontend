/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.LocalDate
import play.api.data.Forms._
import play.api.data.validation._
import play.api.data.{Form, Forms}
import uk.gov.hmrc.eoricommoncomponent.frontend.DateConverter
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.FormUtils._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription._

object SubscriptionForm {

  val subscriptionDateOfEstablishmentForm: Form[LocalDate] = Form(
    "date-of-establishment" -> mandatoryDateTodayOrBefore(
      onEmptyError = "doe.error.empty-date",
      onInvalidDateError = "doe.error.invalid-date",
      onDateInFutureError = "doe.error.future-date",
      minYear = DateConverter.earliestYearDateOfEstablishment
    )
  )

  def validEoriWithOrWithoutGB: Constraint[String] =
    Constraint({
      case e if formatInput(e).isEmpty =>
        Invalid(ValidationError("ecc.matching-error.eori.isEmpty"))
      case e if formatInput(e).forall(_.isDigit) && formatInput(e).length < 12 =>
        Invalid(ValidationError("ecc.matching-error.eori.wrong-length.too-short"))
      case e if formatInput(e).startsWith("GB") && formatInput(e).length < 14 =>
        Invalid(ValidationError("ecc.matching-error.gbeori.wrong-length.too-short"))
      case e if formatInput(e).forall(_.isDigit) && formatInput(e).length > 15 =>
        Invalid(ValidationError("ecc.matching-error.eori.wrong-length.too-long"))
      case e if formatInput(e).startsWith("GB") && formatInput(e).length > 17 =>
        Invalid(ValidationError("ecc.matching-error.gbeori.wrong-length.too-long"))
      case e if formatInput(e).take(2).forall(_.isLetter) && !formatInput(e).startsWith("GB") =>
        Invalid(ValidationError("ecc.matching-error.eori.not-gb"))
      case e if !formatInput(e).matches("^GB[0-9]{12,15}$") && !formatInput(e).matches("[0-9]{12,15}") =>
        Invalid(ValidationError("ecc.matching-error.eori"))
      case _ => Valid
    })

  val eoriNumberForm = Form(
    Forms.mapping("eori-number" -> text.verifying(validEoriWithOrWithoutGB))(EoriNumberViewModel.apply)(
      EoriNumberViewModel.unapply
    )
  )

}
