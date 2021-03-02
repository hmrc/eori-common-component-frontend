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

package uk.gov.hmrc.eoricommoncomponent.frontend.models.address

import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel

case class AddressLookup(addressLine: String, city: String, postcode: String, country: String) {

  def dropDownView: String = List(addressLine, city, postcode).mkString(", ")

  def toAddressViewModel: AddressViewModel = AddressViewModel(addressLine, city, Some(postcode), country)
}

object AddressLookup {

  def applyWithLines(lines: Seq[String], town: String, postcode: String, country: String): AddressLookup = {
    val addressLine = lines match {
      case Seq(line1, line2, _ @_*) => line1 + ", " + line2
      case Seq(line1)               => line1
      case Seq()                    => throw new IllegalStateException("Address line cannot be empty")
    }
    val countryCode = if (country == "UK") "GB" else country

    AddressLookup(addressLine, town, postcode, countryCode)
  }

  implicit val addressReads: Reads[AddressLookup] = (
    (JsPath \ "address" \ "lines").read[Seq[String]] and
      (JsPath \ "address" \ "town").read[String] and
      (JsPath \ "address" \ "postcode").read[String] and
      (JsPath \ "address" \ "country" \ "code").read[String]
  )(AddressLookup.applyWithLines _)

}

sealed trait AddressLookupResponse

case class AddressLookupSuccess(addresses: Seq[AddressLookup]) extends AddressLookupResponse

case object AddressLookupFailure extends AddressLookupResponse
