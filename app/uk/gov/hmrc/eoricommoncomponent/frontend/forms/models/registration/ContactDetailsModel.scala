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
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionTimeOutException

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

  def toContactDetailsViewModel: ContactDetailsViewModel = ContactDetailsViewModel(
    fullName,
    Some(emailAddress),
    telephone,
    fax,
    useAddressFromRegistrationDetails,
    trim(street),
    trim(city),
    trim(postcode),
    countryCode
  )

  def toRowContactInformation(): ContactInformation = ContactInformation(
    personOfContact = Some(fullName),
    sepCorrAddrIndicator = Some(false),
    streetAndNumber = clearEmptyOptions(street),
    city = clearEmptyOptions(city),
    postalCode = clearEmptyOptions(postcode),
    countryCode = clearEmptyOptions(countryCode),
    telephoneNumber = Some(telephone),
    faxNumber = clearEmptyOptions(fax),
    emailAddress = Some(emailAddress)
  )

  // TODO Investigate why ContactInformation model has Some("") instead of None
  // This is a temporary solution to be able to build correct request
  // Somehow during model transformation instead of having None there is Some("")
  private def clearEmptyOptions(input: Option[String]): Option[String] =
    if (input.exists(_.isEmpty)) None else input

}

object ContactDetailsModel {
  implicit val jsonFormat: OFormat[ContactDetailsModel] = Json.format[ContactDetailsModel]

  def trim(value: Option[String]): Option[String] = value.map(_.trim)
}

//TODO remove email address read from cache and populate the contact details
case class ContactDetailsViewModel(
  fullName: String,
  emailAddress: Option[String],
  telephone: String,
  fax: Option[String],
  useAddressFromRegistrationDetails: Boolean = true,
  street: Option[String],
  city: Option[String],
  postcode: Option[String],
  countryCode: Option[String]
) {

  def toContactDetailsModel: ContactDetailsModel = ContactDetailsModel(
    fullName,
    emailAddress.getOrElse(throw SessionTimeOutException("Email is required")),
    telephone,
    fax,
    useAddressFromRegistrationDetails,
    trim(street),
    trim(city),
    trim(postcode),
    countryCode
  )

}

object ContactDetailsViewModel {
  implicit val jsonFormat: OFormat[ContactDetailsViewModel] = Json.format[ContactDetailsViewModel]
}
