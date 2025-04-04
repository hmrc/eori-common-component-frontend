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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging

import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel

import java.time.format.DateTimeFormatter
import java.time._

case class Address(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  addressLine4: Option[String],
  postalCode: Option[String],
  countryCode: String
)

object Address {
  implicit val jsonFormat: OFormat[Address] = Json.format[Address]

  def apply(
    addressLine1: String,
    addressLine2: Option[String],
    addressLine3: Option[String],
    addressLine4: Option[String],
    postalCode: Option[String],
    countryCode: String
  ): Address =
    new Address(
      addressLine1,
      addressLine2,
      addressLine3,
      addressLine4,
      postalCode.filter(_.nonEmpty),
      countryCode.toUpperCase()
    ) {}

  def apply(address: AddressViewModel): Address =
    new Address(address.street, None, Some(address.city), None, address.postcode, address.countryCode) {}

}

trait IndividualName {
  def firstName: String

  def middleName: Option[String]

  def lastName: String

  final def fullName: String = s"$firstName ${middleName.getOrElse("")} $lastName"
}

case class Individual(firstName: String, middleName: Option[String], lastName: String, dateOfBirth: String)
    extends IndividualName

object Individual {

  def noMiddle(firstName: String, lastName: String, dateOfBirth: String): Individual =
    Individual(firstName, middleName = None, lastName, dateOfBirth)

  def withLocalDate(firstName: String, lastName: String, dateOfBirth: LocalDate): Individual =
    withLocalDate(firstName, middleName = None, lastName, dateOfBirth)

  def withLocalDate(
    firstName: String,
    middleName: Option[String],
    lastName: String,
    dateOfBirth: LocalDate
  ): Individual =
    Individual(firstName, middleName, lastName, dateOfBirth.toString)

  implicit val formats: OFormat[Individual] = Json.format[Individual]
}

trait CommonHeader extends Logging {

  private def dateTimeWritesIsoUtc: Writes[LocalDateTime] = new Writes[LocalDateTime] {

    def writes(d: LocalDateTime): JsValue =
      JsString(
        ZonedDateTime.of(d, ZoneId.of("Europe/London")).withNano(0).withZoneSameInstant(ZoneOffset.UTC).format(
          DateTimeFormatter.ISO_DATE_TIME
        )
      )

  }

  private def dateTimeReadsIso: Reads[LocalDateTime] = new Reads[LocalDateTime] {

    def reads(value: JsValue): JsResult[LocalDateTime] =
      try JsSuccess(
        ZonedDateTime.parse(value.as[String], DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(
          ZoneId.of("Europe/London")
        ).toLocalDateTime
      )
      catch {
        case e: Exception =>
          val error = s"Could not parse '${value.toString()}' as an ISO date. Reason: $e"
          // $COVERAGE-OFF$Loggers
          logger.error(error)
          // $COVERAGE-ON
          JsError(error)
      }

  }

  implicit val dateTimeReads: Reads[LocalDateTime]   = dateTimeReadsIso
  implicit val dateTimeWrites: Writes[LocalDateTime] = dateTimeWritesIsoUtc
}

case class MessagingServiceParam(paramName: String, paramValue: String)

object MessagingServiceParam {
  implicit val formats: OFormat[MessagingServiceParam] = Json.format[MessagingServiceParam]

  val positionParamName = "POSITION"
  val Generate          = "GENERATE"
  val Link              = "LINK"
  val Pending           = "WORKLIST"
  val Fail              = "FAIL"

  val formBundleIdParamName = "ETMPFORMBUNDLENUMBER"
}

case class RequestParameter(paramName: String, paramValue: String)

object RequestParameter {
  implicit val formats: OFormat[RequestParameter] = Json.format[RequestParameter]
}

case class RequestCommon(
  regime: String,
  receiptDate: LocalDateTime,
  acknowledgementReference: String,
  originatingSystem: Option[String] = None,
  requestParameters: Option[Seq[RequestParameter]] = None
)

object RequestCommon extends CommonHeader {
  implicit val requestParamFormat: OFormat[RequestParameter] = Json.format[RequestParameter]
  implicit val formats: OFormat[RequestCommon]               = Json.format[RequestCommon]
}

case class ResponseCommon(
  status: String,
  statusText: Option[String] = None,
  processingDate: LocalDateTime,
  returnParameters: Option[List[MessagingServiceParam]] = None
)

object ResponseCommon extends CommonHeader {
  val StatusOK                                  = "OK"
  val StatusNotOK                               = "NOT_OK"
  implicit val formats: OFormat[ResponseCommon] = Json.format[ResponseCommon]
}
