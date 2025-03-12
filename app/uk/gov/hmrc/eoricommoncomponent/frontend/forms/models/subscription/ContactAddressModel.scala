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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription

import play.api.libs.json.{Json, OFormat}

case class ContactAddressModel(
  lineOne: String,
  lineTwo: Option[String],
  lineThree: String,
  lineFour: Option[String],
  postcode: Option[String],
  country: String
) {
  require(
    if (postCodeMandatoryForCountryCode) postcode.fold(false)(_.trim.nonEmpty) else true,
    s"Postcode required for country code: $country"
  )

  private def postCodeMandatoryForCountryCode = List("GG", "JE").contains(country)

  def toContactAddressViewModel: ContactAddressViewModel =
    ContactAddressViewModel(lineOne, lineTwo, lineThree, lineFour, postcode, country)

}

object ContactAddressModel {
  implicit val jsonFormat: OFormat[ContactAddressModel] = Json.format[ContactAddressModel]

  def apply(
    lineOne: String,
    lineTwo: Option[String],
    lineThree: String,
    lineFour: Option[String],
    postcode: Option[String],
    country: String
  ): ContactAddressModel = new ContactAddressModel(
    lineOne.trim,
    lineTwo.map(_.trim),
    lineThree.trim,
    lineFour.map(_.trim),
    postcode.map(_.trim),
    country
  )

  def toAddressViewModel(contactAddress: ContactAddressModel): AddressViewModel = AddressViewModel(contactAddress)

  def trim(value: Option[String]): Option[String] = value.map(_.trim)

}

case class ContactAddressViewModel(
  lineOne: String,
  lineTwo: Option[String],
  lineThree: String,
  lineFour: Option[String],
  postcode: Option[String],
  country: String
)

object ContactAddressViewModel {
  implicit val jsonFormat: OFormat[ContactAddressViewModel] = Json.format[ContactAddressViewModel]
}
