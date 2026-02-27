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

import play.api.libs.json.{Json, OFormat}

case class EuEoriRegisteredAddressModel(
  lineOne: String,
  lineThree: String,
  postcode: Option[String],
  country: String
) {

  def toEuEoriRegisteredAddressViewModel: EuEoriRegisteredAddressViewModel =
    EuEoriRegisteredAddressViewModel(lineOne, lineThree, postcode, country)

}

object EuEoriRegisteredAddressModel {
  implicit val jsonFormat: OFormat[EuEoriRegisteredAddressModel] = Json.format[EuEoriRegisteredAddressModel]

  def apply(
    lineOne: String,
    lineThree: String,
    postcode: Option[String],
    country: String
  ): EuEoriRegisteredAddressModel = new EuEoriRegisteredAddressModel(
    lineOne.trim,
    lineThree.trim,
    postcode.map(_.trim),
    country
  )

  def trim(value: Option[String]): Option[String] = value.map(_.trim)

}

case class EuEoriRegisteredAddressViewModel(
  lineOne: String,
  lineThree: String,
  postcode: Option[String],
  country: String
)

object EuEoriRegisteredAddressViewModel {
  implicit val jsonFormat: OFormat[EuEoriRegisteredAddressViewModel] = Json.format[EuEoriRegisteredAddressViewModel]
}
