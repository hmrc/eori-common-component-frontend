/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.{Clock, LocalDate, LocalDateTime, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.UUID

import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.EstablishmentAddress.createEstablishmentAddress
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.ContactInformation.createContactInformation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.{RequestCommon, RequestParameter}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Countries
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.{
  CdsToEtmpOrganisationType,
  OrganisationTypeConfiguration
}

case class SubscriptionCreateRequest(requestCommon: RequestCommon, requestDetail: RequestDetail)

object SubscriptionCreateRequest {

  implicit val jsonFormat = Json.format[SubscriptionCreateRequest]
  private val logger      = Logger(this.getClass)

  // ROW without UTR apply - REG01
  def apply(
    registration: RegistrationDetails,
    subscription: SubscriptionDetails,
    email: Option[String],
    service: Option[Service]
  ): SubscriptionCreateRequest =
    registration match {
      case RegistrationDetailsIndividual(Some(Eori(eori)), _, safeId, name, _, dob) =>
        mandatoryFieldsReq(
          eori,
          safeId,
          name,
          createRowAddress(subscription, registration),
          dob,
          CdsToEtmpOrganisationType(registration),
          subscription,
          service
        )

      case RegistrationDetailsOrganisation(Some(Eori(eori)), _, safeId, name, _, Some(dateOfEstablishment), _) =>
        mandatoryFieldsReq(
          eori,
          safeId,
          name,
          createRowAddress(subscription, registration),
          dateOfEstablishment,
          CdsToEtmpOrganisationType(registration),
          subscription,
          service
        )

      case _ =>
        throw new IllegalArgumentException("Invalid Registration Details. Unable to create SubscriptionCreateRequest.")
    }

  private def createRowAddress(
    subscription: SubscriptionDetails,
    registration: RegistrationDetails
  ): EstablishmentAddress = {

    val address =
      if (Countries.all.map(_.countryCode).contains(registration.address.countryCode)) registration.address
      else {
        val subscriptionCountry =
          subscription.registeredCompany.getOrElse(throw new Exception("Registered company is not in cache"))

        registration.address.copy(countryCode = subscriptionCountry.country)
      }

    val establishmentAddress = createEstablishmentAddress(address)

    establishmentAddress.copy(city = dashForEmpty(establishmentAddress.city))
  }

  private def mandatoryFieldsReq(
    eori: String,
    safeId: SafeId,
    fullName: String,
    establishmentAddress: EstablishmentAddress,
    dateOfEstablishment: LocalDate,
    etmpTypeOfPerson: Option[OrganisationTypeConfiguration],
    sub: SubscriptionDetails,
    service: Option[Service]
  ) =
    SubscriptionCreateRequest(
      generateWithOriginatingSystem(),
      RequestDetail(
        SAFE = safeId.id,
        EORINo = Some(eori),
        CDSFullName = fullName,
        CDSEstablishmentAddress = establishmentAddress,
        establishmentInTheCustomsTerritoryOfTheUnion = None,
        typeOfLegalEntity = etmpTypeOfPerson.map(_.legalStatus),
        contactInformation = sub.contactDetails.map(_.toRowContactInformation()),
        vatIDs = None,
        consentToDisclosureOfPersonalData = None,
        shortName = None,
        dateOfEstablishment = Some(dateOfEstablishment),
        typeOfPerson = etmpTypeOfPerson.map(_.typeOfPerson),
        principalEconomicActivity = None,
        serviceName = service.map(_.enrolmentKey)
      )
    )

  // ROW with customs ID or UK journey
  def apply(
    data: ResponseData,
    subscription: SubscriptionDetails,
    email: String,
    service: Option[Service]
  ): SubscriptionCreateRequest = {

    val isReg06CountryValid: Boolean = Countries.all.map(_.countryCode).contains(data.establishmentAddress.countryCode)

    val ea =
      if (isReg06CountryValid) data.establishmentAddress
      else
        subscription.addressDetails.map { address =>
          data.establishmentAddress.updateCountryFromAddress(address)
        }.getOrElse(throw new IllegalStateException("Reg06 EstablishmentAddress cannot be empty"))

    SubscriptionCreateRequest(
      generateWithOriginatingSystem(),
      RequestDetail(
        SAFE = data.SAFEID,
        EORINo = subscription.eoriNumber,
        CDSFullName = data.trader.fullName,
        CDSEstablishmentAddress = ea,
        establishmentInTheCustomsTerritoryOfTheUnion =
          data.hasEstablishmentInCustomsTerritory.map(bool => if (bool) "1" else "0"),
        typeOfLegalEntity = data.legalStatus,
        contactInformation = data.contactDetail.map(cd => createContactInformation(cd).withEmail(email)),
        vatIDs =
          data.VATIDs.map(_.map(vs => VatId(countryCode = Some(vs.countryCode), vatID = Some(vs.vatNumber))).toList),
        consentToDisclosureOfPersonalData = None,
        shortName = Some(data.trader.shortName),
        dateOfEstablishment = handleEmptyDate(data.dateOfEstablishmentBirth),
        typeOfPerson = data.personType.map(_.toString),
        principalEconomicActivity = data.principalEconomicActivity,
        serviceName = service.map(_.enrolmentKey)
      )
    )
  }

  private def dashForEmpty(s: String): String =
    if (s.isEmpty) "-" else s

  private def generateWithOriginatingSystem(requestParameters: Option[Seq[RequestParameter]] = None): RequestCommon =
    RequestCommon(
      regime = "CDS",
      receiptDate = LocalDateTime.ofInstant(Clock.systemUTC().instant, ZoneId.of("Europe/London")),
      acknowledgementReference = UUID.randomUUID().toString.replace("-", ""),
      originatingSystem = Some("MDTP"),
      requestParameters = requestParameters
    )

  private def handleEmptyDate(date: Option[String]): Option[LocalDate] = date match {
    case Some(d) => Some(LocalDate.parse(d, DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    case None =>
      logger.warn("No establishment date returned from REG06")
      None
  }

}
