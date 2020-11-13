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

import java.time.Clock
import java.util.UUID

import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.SubscriptionRequest.principalEconomicActivityLength
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.{Address, RequestCommon, RequestParameter}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{ContactDetails, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.{
  CdsToEtmpOrganisationType,
  OrganisationTypeConfiguration
}

case class SubscriptionCreateRequest(requestCommon: RequestCommon, requestDetail: RequestDetail)

object SubscriptionCreateRequest {

  implicit val jsonFormat = Json.format[SubscriptionCreateRequest]
  private val logger      = Logger(this.getClass)

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
          fourFieldAddress(subscription, registration),
          email,
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
          fourFieldAddress(subscription, registration),
          email,
          dateOfEstablishment,
          CdsToEtmpOrganisationType(registration),
          subscription,
          service
        )

      case _ =>
        throw new IllegalArgumentException("Invalid Registration Details. Unable to create SubscriptionCreateRequest.")
    }

  def apply(data: ResponseData, eori: Eori, email: String, service: Option[Service]): SubscriptionCreateRequest = {
    val ea = new EstablishmentAddress(
      data.establishmentAddress.streetAndNumber,
      data.establishmentAddress.city,
      data.establishmentAddress.postalCode.filterNot(p => p.isEmpty),
      data.establishmentAddress.countryCode
    )
    SubscriptionCreateRequest(
      generateWithOriginatingSystem(),
      RequestDetail(
        SAFE = data.SAFEID,
        EORINo = Some(eori.id),
        CDSFullName = data.trader.fullName,
        CDSEstablishmentAddress = ea,
        establishmentInTheCustomsTerritoryOfTheUnion =
          data.hasEstablishmentInCustomsTerritory.map(bool => if (bool) "1" else "0"),
        typeOfLegalEntity = data.legalStatus,
        contactInformation = contactInfoWithCapturedEmail(
          ContactInformation.getContactInformation(data.contactDetail),
          email
        ) map dashIfCityEmpty,
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

  def apply(
    reg: RegistrationDetails,
    sub: SubscriptionDetails,
    cdsOrgType: Option[CdsOrganisationType],
    dateEstablished: LocalDate,
    service: Option[Service]
  ): SubscriptionRequest = {
    val org                                = CdsToEtmpOrganisationType(cdsOrgType) orElse CdsToEtmpOrganisationType(reg)
    val ukVatId: Option[VatIdentification] = sub.ukVatDetails.map(vd => VatIdentification(Some("GB"), Some(vd.number)))
    val euVatIds                           = sub.vatIdentificationList

    SubscriptionRequest(
      SubscriptionCreateRequest(
        generateWithOriginatingSystem(),
        RequestDetail(
          SAFE = reg.safeId.id,
          EORINo = None,
          CDSFullName = reg.name,
          CDSEstablishmentAddress = fourFieldAddress(sub, reg),
          establishmentInTheCustomsTerritoryOfTheUnion = None,
          typeOfLegalEntity = org.map(_.legalStatus),
          contactInformation = Some(dashIfCityEmpty(createContactInformation(sub.contactDetails.get.contactDetails))),
          vatIDs = createVatIds(Some(ukVatId ++: euVatIds)),
          consentToDisclosureOfPersonalData = sub.personalDataDisclosureConsent.map(bool => if (bool) "1" else "0"),
          shortName = sub.businessShortName flatMap (_.shortName),
          dateOfEstablishment = Some(dateEstablished),
          typeOfPerson = org.map(_.typeOfPerson),
          principalEconomicActivity = sub.sicCode.map(_.take(principalEconomicActivityLength)),
          serviceName = service.map(_.enrolmentKey)
        )
      )
    )
  }

  private def mandatoryFieldsReq(
    eori: String,
    safeId: SafeId,
    fullName: String,
    establishmentAddress: EstablishmentAddress,
    contactEmail: Option[String],
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
        contactInformation = Some(dashIfCityEmpty(createContactInformation(sub.contactDetails.get.contactDetails))),
        vatIDs = None,
        consentToDisclosureOfPersonalData = None,
        shortName = None,
        dateOfEstablishment = Some(dateOfEstablishment),
        typeOfPerson = etmpTypeOfPerson.map(_.typeOfPerson),
        principalEconomicActivity = None,
        serviceName = service.map(_.enrolmentKey)
      )
    )

  private def fourFieldAddress(subscription: SubscriptionDetails, registration: RegistrationDetails) = {
    def dashForEmpty: String => String = {
      case s if s.isEmpty => "-"
      case nonEmpty       => nonEmpty
    }

    val address = subscription.addressDetails match {
      case Some(a) =>
        EstablishmentAddress(streetAndNumber = a.street, city = a.city, a.postcode.filter(_.nonEmpty), a.countryCode)
      case _ => createEstablishmentAddress(registration.address)
    }

    address.copy(city = dashForEmpty(address.city))
  }

  private def dashIfCityEmpty: ContactInformation => ContactInformation = {
    case c: ContactInformation if c.city.getOrElse("").isEmpty => c.copy(city = Some("-"))
    case nonEmptyCity                                          => nonEmptyCity
  }

  def createEstablishmentAddress(address: Address): EstablishmentAddress = {
    val fourLineAddress = AddressViewModel(address)
    new EstablishmentAddress(
      fourLineAddress.street,
      fourLineAddress.city,
      address.postalCode.filterNot(p => p.isEmpty),
      fourLineAddress.countryCode
    )
  }

  def generateWithOriginatingSystem(requestParameters: Option[Seq[RequestParameter]] = None): RequestCommon =
    RequestCommon(
      regime = "CDS",
      receiptDate = new DateTime(Clock.systemUTC().instant.toEpochMilli, DateTimeZone.UTC),
      acknowledgementReference = UUID.randomUUID().toString.replace("-", ""),
      originatingSystem = Some("MDTP"),
      requestParameters = requestParameters
    )

  private def handleEmptyDate(date: Option[String]): Option[LocalDate] = date match {
    case Some(d) => Some(LocalDate.parse(d, DateTimeFormat.forPattern("yyyy-MM-dd")))
    case None =>
      logger.warn("No establishment date returned from REG06")
      None
  }

  private def contactInfoWithCapturedEmail(contactInfoFromReg06: Option[ContactInformation], capturedEmail: String) =
    contactInfoFromReg06 match {
      case Some(c) => Some(c.copy(emailAddress = Some(capturedEmail)))
      case None    => Some(ContactInformation(emailAddress = Some(capturedEmail)))
    }

  private def createContactInformation(subscriptionContactDetails: ContactDetails): ContactInformation =
    ContactInformation(
      personOfContact = Some(subscriptionContactDetails.fullName),
      sepCorrAddrIndicator = Some(true),
      streetAndNumber = Some(subscriptionContactDetails.street),
      city = Some(subscriptionContactDetails.city),
      postalCode = subscriptionContactDetails.postcode.filter(_.nonEmpty),
      countryCode = Some(subscriptionContactDetails.countryCode),
      telephoneNumber = Some(subscriptionContactDetails.telephone),
      faxNumber = subscriptionContactDetails.fax,
      emailAddress = Some(subscriptionContactDetails.emailAddress)
    )

  private def createVatIds(vis: Option[List[VatIdentification]]): Option[List[VatId]] = {
    def removeEmpty: List[VatIdentification] => List[VatId] = _.flatMap {
      case VatIdentification(None, None) => None
      case VatIdentification(cc, n)      => Some(VatId(cc, n))
    }

    def removeEmptyList: List[VatId] => Option[List[VatId]] = {
      case Nil => None
      case vs  => Some(vs)
    }

    vis map removeEmpty flatMap removeEmptyList
  }

}
