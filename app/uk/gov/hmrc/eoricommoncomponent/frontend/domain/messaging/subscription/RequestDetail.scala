/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.LocalDate
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CaseClassAuditHelper

case class RequestDetail(
  SAFE: String,
  EORINo: Option[String],
  CDSFullName: String,
  CDSEstablishmentAddress: EstablishmentAddress,
  establishmentInTheCustomsTerritoryOfTheUnion: Option[String],
  typeOfLegalEntity: Option[String],
  contactInformation: Option[ContactInformation],
  vatIDs: Option[List[VatId]],
  consentToDisclosureOfPersonalData: Option[String],
  shortName: Option[String],
  dateOfEstablishment: Option[LocalDate],
  typeOfPerson: Option[String],
  principalEconomicActivity: Option[String]
) extends CaseClassAuditHelper {
  require(dateOfEstablishment.isDefined)
  val ignoredFields = List("CDSEstablishmentAddress", "contactInformation", "vatIDs")

  def keyValueMap(): Map[String, String] = {
    val m = toMap(this, ignoredFields = ignoredFields)
    val am = prefixMapKey("address.", CDSEstablishmentAddress.toMap())
    val rd = prefixMapKey("contactInformation.", contactInformation.fold(Map.empty[String, String])(_.toMap()))
    val vm = prefixMapKey("vatIDs.", vatIDs.fold(Map.empty[String, String])(_.flatMap(_.toMap()).toMap))
    m ++ am ++ rd ++ vm
  }
}

object RequestDetail {
  import play.api.libs.json.JodaWrites._
  import play.api.libs.json.JodaReads._

  implicit val jsonFormat = Json.format[RequestDetail]
}
