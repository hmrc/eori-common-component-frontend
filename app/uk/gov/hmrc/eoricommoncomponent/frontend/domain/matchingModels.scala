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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain

import org.joda.time.LocalDate
import play.api.libs.json._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.IndividualName

sealed trait CustomsId {
  def id: String
}

case class Utr(override val id: String) extends CustomsId

case class Eori(override val id: String) extends CustomsId

case class Nino(override val id: String) extends CustomsId

case class SafeId(override val id: String) extends CustomsId

case class TaxPayerId(override val id: String) extends CustomsId {
  private val MDGTaxPayerIdLength = 42
  val mdgTaxPayerId: String = id + "0" * (MDGTaxPayerIdLength - id.length)
}

object TaxPayerId {
  implicit val format = Json.format[TaxPayerId]
}

object SafeId {
  implicit val format = Json.format[SafeId]
  implicit def toJsonFormat(safeId: SafeId): JsValue = Json.toJson(safeId)
}
case class InternalId(id: String)
object InternalId {
  def apply(id: Option[String]): InternalId =
    new InternalId(id.getOrElse(throw new IllegalArgumentException("InternalId is missing")))
  implicit val format = Json.format[InternalId]
}

case class GroupId(id: String)
object GroupId {
  def apply(id: Option[String]): GroupId =
    new GroupId(id.getOrElse(throw new IllegalArgumentException("GroupId is missing")))
  implicit val format = Json.format[GroupId]
}

case class CacheIds(internalId: InternalId, safeId: SafeId)
object CacheIds {
  def apply(mayBeInternalId: Option[String], mayBeSafeId: Option[String]): CacheIds = {
    val internalId = InternalId(mayBeInternalId.getOrElse(throw new IllegalArgumentException("InternalId missing")))
    val safeId = SafeId(mayBeSafeId.getOrElse(throw new IllegalArgumentException("SafeId missing")))
    new CacheIds(internalId, safeId)
  }
  implicit val jsonFormat = Json.format[CacheIds]
  implicit def toJsonFormat(cacheIds: CacheIds): JsValue = Json.toJson(cacheIds)
}

object CustomsId {
  private val utr = "utr"
  private val eori = "eori"
  private val nino = "nino"
  private val safeId = "safeId"
  private val taxPayerId = "taxPayerId"

  private val idTypeMapping = Map[String, String => CustomsId](
    utr -> Utr,
    eori -> Eori,
    nino -> Nino,
    safeId -> (s => SafeId(s)),
    taxPayerId -> (s => TaxPayerId(s))
  )

  implicit val formats = Format[CustomsId](
    fjs = Reads { js =>
      idTypeMapping.view.flatMap {
        case (jsFieldName, idConstruct) =>
          for (id <- (js \ jsFieldName).asOpt[String]) yield idConstruct(id)
      }.headOption
        .fold[JsResult[CustomsId]](JsError("No matching id type and value found"))(customsId => JsSuccess(customsId))
    },
    tjs = Writes {
      case Utr(id)        => Json.obj(utr -> id)
      case Eori(id)       => Json.obj(eori -> id)
      case Nino(id)       => Json.obj(nino -> id)
      case SafeId(id)     => Json.obj(safeId -> id)
      case TaxPayerId(id) => Json.obj(taxPayerId -> id)
    }
  )
}

case class UserLocationDetails(location: Option[String])

case class JourneyTypeDetails(journeyType: String)

trait NameIdOrganisationMatch {
  def name: String
  def id: String
}

trait NameOrganisationMatch {
  def name: String
}

case class NameIdOrganisationMatchModel(name: String, id: String) extends NameIdOrganisationMatch

object NameIdOrganisationMatchModel {
  implicit val jsonFormat = Json.format[NameIdOrganisationMatchModel]
}

case class NameOrganisationMatchModel(name: String) extends NameOrganisationMatch

object NameOrganisationMatchModel {
  implicit val jsonFormat = Json.format[NameOrganisationMatchModel]
}

case class EuNameIdOrganisationMatchModel(name: String, id: String, dateEstablished: LocalDate)
    extends NameIdOrganisationMatch

case class EuIndividualMatch(
  firstName: String,
  middleName: Option[String],
  lastName: String,
  dateOfBirth: LocalDate,
  matchingId: String
)

case class YesNo(isYes: Boolean) {
  def isNo: Boolean = !isYes
}

case class NinoMatch(firstName: String, lastName: String, dateOfBirth: LocalDate, nino: String)

trait NameDobMatch {
  def firstName: String

  def middleName: Option[String]

  def lastName: String

  def dateOfBirth: LocalDate
}

case class NameDobMatchModel(firstName: String, middleName: Option[String], lastName: String, dateOfBirth: LocalDate)
    extends NameDobMatch {
  def name: String = s"$firstName ${middleName.getOrElse("")} $lastName"
}

object NameDobMatchModel {
  implicit val jsonFormat = Json.format[NameDobMatchModel]
}

case class NinoOrUtr(nino: Option[String], utr: Option[String], ninoOrUtrRadio: Option[String])

case class SixLineAddressMatchModel(
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
}

case class IndividualNameAndDateOfBirth(
  firstName: String,
  middleName: Option[String],
  lastName: String,
  dateOfBirth: LocalDate
) extends IndividualName

case class EoriAndIdNameAndAddress(fullName: String, address: EstablishmentAddress)

trait IdMatch {
  def id: String
}

case class IdMatchModel(id: String) extends IdMatch

object IdMatchModel {
  implicit val jsonFormat = Json.format[IdMatchModel]
}

case class UtrMatchModel(haveUtr: Option[Boolean], id: Option[String])

object UtrMatchModel {
  implicit val jsonFormat = Json.format[UtrMatchModel]
}

trait NameMatch {
  def name: String
}

case class NameMatchModel(name: String) extends NameMatch

object NameMatchModel {
  implicit val jsonFormat = Json.format[NameMatchModel]
}

case class NinoMatchModel(haveNino: Option[Boolean], nino: Option[String])

object NinoMatchModel {
  implicit val jsonFormat = Json.format[NinoMatchModel]
}
