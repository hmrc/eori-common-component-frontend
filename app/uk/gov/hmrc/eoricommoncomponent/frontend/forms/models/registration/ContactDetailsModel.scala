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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.ContactInformation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ContactDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel.trim

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
    trim(street).getOrElse(""),
    trim(city).getOrElse(""),
    trim(postcode),
    countryCode.getOrElse("")
  )

  def toRowContactInformation(): ContactInformation = ContactInformation(
    personOfContact = Some(fullName),
    sepCorrAddrIndicator = Some(false),
    streetAndNumber = None,
    city = None,
    postalCode = None,
    countryCode = None,
    telephoneNumber = Some(telephone),
    faxNumber = None,
    emailAddress = Some(emailAddress)
  )

}

object ContactDetailsModel {
  implicit val jsonFormat: OFormat[ContactDetailsModel] = Json.format[ContactDetailsModel]

  def trim(value: Option[String]): Option[String] = value.map(_.trim)
}
