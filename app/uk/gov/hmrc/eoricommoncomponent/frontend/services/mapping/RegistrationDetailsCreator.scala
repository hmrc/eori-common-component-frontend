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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping

import play.api.Logging
import uk.gov.hmrc.eoricommoncomponent.frontend.DateConverter._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.matching.{
  IndividualResponse,
  OrganisationResponse,
  RegisterWithIDResponse
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.registration.RegistrationDisplayResponse

import java.time.LocalDate
import javax.inject.Singleton

@Singleton
class RegistrationDetailsCreator extends Logging {

  def registrationDetails(
    response: RegisterWithIDResponse,
    customsId: CustomsId,
    capturedDate: Option[LocalDate]
  ): RegistrationDetails = {
    val responseDetail = response.getResponseDetail
    val sapNumber      = extractSapNumber(response.responseCommon.returnParameters)
    if (responseDetail.isAnIndividual)
      convertIndividualMatchingResponse(
        responseDetail.individual.get,
        Some(customsId),
        sapNumber,
        SafeId(responseDetail.SAFEID),
        responseDetail.address,
        dateOfBirth = capturedDate
      )
    else
      convertOrganisationMatchingResponse(
        responseDetail.organisation.get,
        Some(customsId),
        sapNumber,
        SafeId(responseDetail.SAFEID),
        responseDetail.address,
        dateOfEstablishment = capturedDate
      )
  }

  def extractSapNumber(returnParameters: Option[List[MessagingServiceParam]]): String =
    returnParameters
      .getOrElse(List.empty)
      .find(_.paramName == "SAP_NUMBER")
      .fold {
        val error = "Invalid Response. SAP Number not returned by Messaging."
        // $COVERAGE-OFF$Loggers
        logger.warn(error)
        // $COVERAGE-ON
        throw new IllegalArgumentException(error)
      }(_.paramValue)

  private def convertIndividualMatchingResponse(
    individualResponse: IndividualResponse,
    customsId: Option[CustomsId],
    sapNumber: String,
    safeId: SafeId,
    address: Address,
    dateOfBirth: Option[LocalDate]
  ): RegistrationDetailsIndividual = {
    val name = individualResponse.fullName
    val dob =
      individualResponse.dateOfBirth.flatMap(toLocalDate).orElse(dateOfBirth)
    dob.fold(ifEmpty = {
      val error = "Date of Birth is neither provided in registration response nor captured in the application page"
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throw new IllegalArgumentException(error)
    })(
      dateOfBirth =>
        RegistrationDetails
          .individual(sapNumber, safeId, name, address, dateOfBirth, customsId)
    )
  }

  private def convertOrganisationMatchingResponse(
    organisationResponse: OrganisationResponse,
    customsId: Option[CustomsId],
    sapNumber: String,
    safeId: SafeId,
    address: Address,
    dateOfEstablishment: Option[LocalDate]
  ): RegistrationDetailsOrganisation = {
    val name = organisationResponse.organisationName
    val etmpOrganisationType =
      organisationResponse.organisationType.map(EtmpOrganisationType.apply)
    RegistrationDetails.organisation(
      sapNumber,
      safeId,
      name,
      address,
      customsId,
      dateOfEstablishment,
      etmpOrganisationType
    )
  }

  def registrationDetails(
    response: RegisterWithoutIDResponse,
    orgName: String,
    orgAddress: SixLineAddressMatchModel
  ): RegistrationDetailsOrganisation = {
    val sapNumber = extractSapNumber(response.responseCommon.returnParameters)
    val address = Address(
      orgAddress.lineOne,
      orgAddress.lineTwo,
      Some(orgAddress.lineThree),
      orgAddress.lineFour,
      orgAddress.postcode,
      orgAddress.country
    )

    RegistrationDetails.organisation(
      sapNumber,
      SafeId(
        response.responseDetail
          .getOrElse {
            val error = "Organisation registrationDetails: No responseDetail"
            // $COVERAGE-OFF$Loggers
            logger.warn(error)
            // $COVERAGE-ON
            throw new IllegalStateException(error)
          }
          .SAFEID
      ),
      orgName,
      address,
      customsId = None,
      dateEstablished = None,
      etmpOrganisationType = None
    )
  }

  def registrationDetails(
    response: RegisterWithoutIDResponse,
    ind: IndividualNameAndDateOfBirth,
    add: SixLineAddressMatchModel
  ): RegistrationDetailsIndividual = {
    val sapNumber = extractSapNumber(response.responseCommon.returnParameters)
    val address   = Address(add.lineOne, add.lineTwo, Some(add.lineThree), add.lineFour, add.postcode, add.country)
    val name      = ind.fullName

    RegistrationDetails.individual(
      sapNumber,
      SafeId(
        response.responseDetail
          .getOrElse {
            val error = "Individual RegistrationDetails: No responseDetail"
            // $COVERAGE-OFF$Loggers
            logger.warn(error)
            // $COVERAGE-ON
            throw new IllegalStateException("No responseDetail")
          }
          .SAFEID
      ),
      name,
      address,
      ind.dateOfBirth,
      customsId = None
    )
  }

  def registrationDetails(response: RegistrationDisplayResponse): RegistrationDetails = {
    val responseDetail = response.getResponseDetail
    (responseDetail.individual, responseDetail.organisation, response.responseCommon.taxPayerID) match {
      case (Some(individual), None, Some(taxPayerId)) =>
        convertIndividualMatchingResponse(
          individual,
          None,
          taxPayerId,
          SafeId(responseDetail.SAFEID),
          responseDetail.address,
          dateOfBirth = None
        )
      case (None, Some(org), Some(taxPayerId)) =>
        convertOrganisationMatchingResponse(
          org,
          None,
          taxPayerId,
          SafeId(responseDetail.SAFEID),
          responseDetail.address,
          dateOfEstablishment = None
        )
      case _ =>
        val error = "RegistrationDetails: Unexpected Response or Missing Key Information"
        // $COVERAGE-OFF$Loggers
        logger.warn(error)
        // $COVERAGE-ON
        throw new IllegalStateException(error)
    }
  }

}
