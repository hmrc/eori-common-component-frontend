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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.matching

import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging._

case class Organisation(organisationName: String, organisationType: String)

object Organisation {
  implicit val formats: OFormat[Organisation] = Json.format[Organisation]
}

case class RequestDetail(
  IDType: String,
  IDNumber: String,
  requiresNameMatch: Boolean,
  isAnAgent: Boolean,
  organisation: Option[Organisation] = None,
  individual: Option[Individual] = None
)

object RequestDetail {
  implicit val formats: OFormat[RequestDetail] = Json.format[RequestDetail]
}

case class MatchingRequest(requestCommon: RequestCommon, requestDetail: RequestDetail)

object MatchingRequest {
  implicit val formats: OFormat[MatchingRequest] = Json.format[MatchingRequest]
}

case class MatchingRequestHolder(registerWithIDRequest: MatchingRequest)

object MatchingRequestHolder {
  implicit val formats: OFormat[MatchingRequestHolder] = Json.format[MatchingRequestHolder]
}

case class IndividualResponse(
  firstName: String,
  middleName: Option[String],
  lastName: String,
  dateOfBirth: Option[String]
) extends IndividualName

object IndividualResponse {
  implicit val formats: OFormat[IndividualResponse] = Json.format[IndividualResponse]
}

case class OrganisationResponse(
  organisationName: String,
  code: Option[String],
  isAGroup: Option[Boolean],
  organisationType: Option[String]
)

object OrganisationResponse {
  implicit val formats: OFormat[OrganisationResponse] = Json.format[OrganisationResponse]
}

case class ContactResponse(
  phoneNumber: Option[String] = None,
  mobileNumber: Option[String] = None,
  faxNumber: Option[String] = None,
  emailAddress: Option[String] = None
)

object ContactResponse {
  implicit val jsonFormat: OFormat[ContactResponse] = Json.format[ContactResponse]
}

case class ResponseDetail(
  SAFEID: String,
  ARN: Option[String] = None,
  isEditable: Boolean,
  isAnAgent: Boolean,
  isAnIndividual: Boolean,
  individual: Option[IndividualResponse] = None,
  organisation: Option[OrganisationResponse] = None,
  address: Address,
  contactDetails: ContactResponse
)

object ResponseDetail {
  implicit val formats: OFormat[ResponseDetail] = Json.format[ResponseDetail]
}

case class RegisterWithIDResponse(responseCommon: ResponseCommon, responseDetail: Option[ResponseDetail])
    extends Logging {

  def getResponseDetail: ResponseDetail = responseDetail match {
    case Some(detail) => detail
    case None =>
      val error = "RegisterWithIDResponse did not include expected ResponseDetail object"
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throw new IllegalArgumentException(error)
  }

}

object RegisterWithIDResponse {
  implicit val formats: OFormat[RegisterWithIDResponse] = Json.format[RegisterWithIDResponse]
}

case class MatchingResponse(registerWithIDResponse: RegisterWithIDResponse)

object MatchingResponse {
  implicit val formats: OFormat[MatchingResponse] = Json.format[MatchingResponse]
}
