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
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._

case class RegistrationInfoRequest(regime: String = "CDS", idType: String, idValue: String)

object RegistrationInfoRequest extends Logging {
  implicit val jsonFormat: OFormat[RegistrationInfoRequest] = Json.format[RegistrationInfoRequest]

  val UTR  = "UTR"
  val EORI = "EORI"
  val NINO = "NINO"
  val SAFE = "SAFE"

  def forCustomsId(customsId: CustomsId): RegistrationInfoRequest = {
    val idType = customsId match {
      case _: Eori   => EORI
      case _: Utr    => UTR
      case _: Nino   => NINO
      case _: SafeId => SAFE
      case _: TaxPayerId =>
        val error = "TaxPayerId is not supported by RegistrationInfo service"
        // $COVERAGE-OFF$Loggers
        logger.warn(error)
        // $COVERAGE-ON
        throw new IllegalArgumentException(error)
    }
    RegistrationInfoRequest(idType = idType, idValue = customsId.id)
  }

}

case class NonUKIdentification(IDNumber: String, issuingInstitution: String, issuingCountryCode: String)

object NonUKIdentification {
  implicit val jsonFormat: OFormat[NonUKIdentification] = Json.format[NonUKIdentification]
}
