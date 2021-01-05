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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription

import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CaseClassAuditHelper
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Address
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel

case class EstablishmentAddress(streetAndNumber: String, city: String, postalCode: Option[String], countryCode: String)
    extends CaseClassAuditHelper

object EstablishmentAddress {
  implicit val jsonFormat = Json.format[EstablishmentAddress]

  def createEstablishmentAddress(address: Address): EstablishmentAddress = {
    val fourLineAddress = AddressViewModel(address)
    new EstablishmentAddress(
      fourLineAddress.street,
      fourLineAddress.city,
      address.postalCode.filterNot(p => p.isEmpty),
      fourLineAddress.countryCode
    )
  }

}
