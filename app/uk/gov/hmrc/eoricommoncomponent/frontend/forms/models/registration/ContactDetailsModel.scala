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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ContactDetails

case class ContactDetailsModel(
  fullName: String,
  emailAddress: String,
  telephone: String,
  fax: Option[String],
  useAddressFromRegistrationDetails: Boolean = true,
  street: Option[String],
  city: Option[String],
  postcode: Option[String],
  countryCode: Option[String]
) {

  def contactDetails: ContactDetails = ContactDetails(
    fullName,
    emailAddress,
    telephone,
    fax,
    street.map(_.trim).getOrElse(""),
    city.map(_.trim).getOrElse(""),
    postcode.map(_.trim),
    countryCode.getOrElse("")
  )

}

object ContactDetailsModel {
  implicit val jsonFormat: OFormat[ContactDetailsModel] = Json.format[ContactDetailsModel]
}
