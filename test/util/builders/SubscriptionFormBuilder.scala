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

package util.builders

import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel

import java.time.LocalDate

object SubscriptionFormBuilder {

  val LegalStatus                = "corporate-body-uk"
  val ShortName                  = "Short Name"
  val sic                        = "9999"
  val DateEstablished: LocalDate = LocalDate.of(1900, 11, 11)

  private val contactDetailsModel = ContactDetailsModel(
    fullName = "John Doe",
    emailAddress = "john.doe@example.com",
    telephone = "01632961234",
    fax = None,
    street = Some("Line 1"),
    city = Some("line 2"),
    postcode = Some("SE28 1AA"),
    countryCode = Some("ZZ")
  )

  val orgSubscriptionMandatoryMap: Map[String, String] = Map(
    "legal-status"           -> LegalStatus,
    "short-name"             -> ShortName,
    "date-established.day"   -> DateEstablished.getDayOfMonth.toString,
    "date-established.month" -> DateEstablished.getMonthValue.toString,
    "date-established.year"  -> DateEstablished.getYear.toString,
    "sic"                    -> sic
  )

  val vatSubscriptionMandatorySeq: Seq[(String, String)] = Seq(
    "vat-gb-id"                -> "true",
    "vat-gb-number[]"          -> "123456781",
    "vat-gb-number[]"          -> "123456782",
    "vat-eu-id"                -> "true",
    "eu-vats.vat-eu-number[]"  -> "113456781",
    "eu-vats.vat-eu-number[]"  -> "223456782",
    "eu-vats.vat-eu-country[]" -> "IN",
    "eu-vats.vat-eu-country[]" -> "CH"
  )

  val vatSubscriptionInvalidSeq: Seq[(String, String)] = Seq(
    "vat-gb-id"                -> "true",
    "vat-gb-number[]"          -> "",
    "vat-eu-id"                -> "true",
    "eu-vats.vat-eu-number[]"  -> "",
    "eu-vats.vat-eu-country[]" -> ""
  )

  val vatSubscriptionMandatoryRequestMap: Map[String, Seq[String]] =
    vatSubscriptionMandatorySeq.groupMap(_._1)(_._2)

  val vatSubscriptionOptionalInvalidMap: Map[String, Seq[String]] =
    vatSubscriptionInvalidSeq.groupMap(_._1)(_._2)

  val detailsHolderWithAllFields: SubscriptionDetails =
    SubscriptionDetails(contactDetails = Some(contactDetailsModel), dateEstablished = Some(DateEstablished))

}
