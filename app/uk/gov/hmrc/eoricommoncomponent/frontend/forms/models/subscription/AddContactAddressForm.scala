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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription

import play.api.data.Forms._
import play.api.data.validation._
import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.YesNo

object AddContactAddressForm {

  private def oneOf[T](validValues: Set[T]): T => Boolean = validValues.contains

  private val validYesNoAnswerOptions = Set("true", "false")

  def confirmAddContactAddressYesNoAnswerForm()(implicit messages: Messages): Form[YesNo] = yesNoAnswerForm()

  private def yesNoAnswerForm()(implicit messages: Messages): Form[YesNo] = Form(
    mapping(
      YesNo.yesNoAnswer -> optional(
        text.verifying(
          messages("cds.subscription.add-contact-address.page-error.yes-no-answer"),
          oneOf(validYesNoAnswerOptions)
        )
      ).verifying(messages("cds.subscription.add-contact-address.page-error.yes-no-answer"), _.isDefined)
        .transform[Boolean](str => str.get.toBoolean, bool => Option(String.valueOf(bool)))
    )(YesNo.apply)(yesNo => Some(yesNo.isYes))
  )

}
