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

package util.builders

import uk.gov.hmrc.eoricommoncomponent.frontend.domain.EstablishmentAddress
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.{MessagingServiceParam, ResponseCommon}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ContactDetails

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

object SubscriptionInfoBuilder {

  val eori: Option[String]        = Some("12345")
  val CDSOrgName                  = "orgName"
  val orgStreetName               = "Line 1"
  val orgCity                     = "line 2"
  val orgPostalCode: Some[String] = Some("SE28 1AA")
  val orgCountryCode              = "ZZ"

  val contactName                  = "John Doe"
  val contactStreet                = "Line 1"
  val contactCity                  = "city name"
  val contactPostalCode            = "SE28 1AA"
  val contactCountry               = "ZZ"
  val telephoneNumber              = "01632961234"
  val faxNumber                    = "01632961235"
  val emailAddress                 = "john.doe@example.com"
  val dateOfEstablishmentFormatted = "31 December 2015"

  val dateOfEstablishment: LocalDate =
    LocalDate.parse(dateOfEstablishmentFormatted, DateTimeFormatter.ofPattern("d MMMM yyyy"))

  val VATIdNoList: List[String]      = List("VAT-1", "VAT-2", "VAT-3", "VAT-4", "VAT-5")
  val VATIdCountryList: List[String] = List("GB", "FR", "ES", "PT", "IN")

  val vatIDList: IndexedSeq[SubscriptionInfoVatId] = for {
    index <- VATIdNoList.indices
    vatCountry = VATIdCountryList lift index
    vatID      = VATIdNoList lift index
    vatList    = SubscriptionInfoVatId(vatCountry, vatID)
  } yield vatList

  val shortName                                = "ltd"
  val legalEntityValue                         = "0001"
  val typeOfPerson                             = "1"
  val principalEconomicActivity                = "100"
  val consentToDisclosureOfPersonalDataGranted = "1"
  val consentToDisclosureOfPersonalDataDenied  = "0"

  private def thirdCountryUniqueIdentificationNumber(index: Int) = s"000$index"

  val establishmentInTheCustomsTerritoryOfTheUnion = "1"

  val fullyPopulatedEstablishmentAddress: EstablishmentAddress =
    EstablishmentAddress(orgStreetName, orgCity, orgPostalCode, orgCountryCode)

  val fullyPopulatedContactInformation: ContactInformation = ContactInformation(
    personOfContact = Some(contactName),
    sepCorrAddrIndicator = Some(true),
    streetAndNumber = Some(contactStreet),
    city = Some(contactCity),
    postalCode = Some(contactPostalCode),
    countryCode = Some(contactCountry),
    telephoneNumber = Some(telephoneNumber),
    faxNumber = Some(faxNumber),
    emailAddress = Some(emailAddress)
  )

  val unpopulatedContactInformation: ContactInformation = ContactInformation(
    personOfContact = None,
    sepCorrAddrIndicator = None,
    streetAndNumber = None,
    city = None,
    postalCode = None,
    countryCode = None,
    telephoneNumber = None,
    faxNumber = None,
    emailAddress = None
  )

  val partiallyPopulatedContactInformation: ContactInformation = ContactInformation(
    personOfContact = None,
    sepCorrAddrIndicator = None,
    streetAndNumber = Some(contactStreet),
    city = None,
    postalCode = Some(contactPostalCode),
    countryCode = Some(contactCountry),
    telephoneNumber = None,
    faxNumber = None,
    emailAddress = None
  )

  val onlyMandatoryPopulatedResponseDetail: SubscriptionDisplayResponseDetail = SubscriptionDisplayResponseDetail(
    EORINo = eori,
    CDSFullName = CDSOrgName,
    CDSEstablishmentAddress = fullyPopulatedEstablishmentAddress,
    establishmentInTheCustomsTerritoryOfTheUnion = None,
    typeOfLegalEntity = None,
    contactInformation = None,
    VATIDs = None,
    thirdCountryUniqueIdentificationNumber = None,
    consentToDisclosureOfPersonalData = None,
    shortName = None,
    dateOfEstablishment = Some(dateOfEstablishment),
    typeOfPerson = None,
    principalEconomicActivity = None
  )

  val fullyPopulatedResponseDetail: SubscriptionDisplayResponseDetail = SubscriptionDisplayResponseDetail(
    EORINo = eori,
    CDSFullName = CDSOrgName,
    CDSEstablishmentAddress = fullyPopulatedEstablishmentAddress,
    establishmentInTheCustomsTerritoryOfTheUnion = Some(establishmentInTheCustomsTerritoryOfTheUnion),
    typeOfLegalEntity = Some(legalEntityValue),
    contactInformation = Some(fullyPopulatedContactInformation),
    VATIDs = Some(vatIDList.toList),
    thirdCountryUniqueIdentificationNumber = Some(
      List(
        thirdCountryUniqueIdentificationNumber(1),
        thirdCountryUniqueIdentificationNumber(2),
        thirdCountryUniqueIdentificationNumber(3),
        thirdCountryUniqueIdentificationNumber(4),
        thirdCountryUniqueIdentificationNumber(5)
      )
    ),
    consentToDisclosureOfPersonalData = Some(consentToDisclosureOfPersonalDataGranted),
    shortName = Some(shortName),
    dateOfEstablishment = Some(dateOfEstablishment),
    typeOfPerson = Some(typeOfPerson),
    principalEconomicActivity = Some(principalEconomicActivity)
  )

  val responseDetailWithoutEmail: SubscriptionDisplayResponseDetail =
    fullyPopulatedResponseDetail.copy(contactInformation = Some(partiallyPopulatedContactInformation))

  val responseDetailWithUnverifiedEmail: SubscriptionDisplayResponseDetail =
    fullyPopulatedResponseDetail.copy(contactInformation =
      Some(fullyPopulatedContactInformation.copy(emailVerificationTimestamp = None))
    )

  val responseDetailWithoutPersonOfContact: SubscriptionDisplayResponseDetail =
    fullyPopulatedResponseDetail.copy(contactInformation =
      Some(partiallyPopulatedContactInformation.copy(emailAddress = Some(emailAddress)))
    )

  val sampleResponseCommon: ResponseCommon = ResponseCommon(
    "OK",
    Some("Status text"),
    LocalDateTime.now(),
    Some(
      List(
        MessagingServiceParam("POSITION", "GENERATE"),
        MessagingServiceParam("ETMPFORMBUNDLENUMBER", "0771123680108")
      )
    )
  )

  val sampleResponseCommonWithoutFormBundleNumber: ResponseCommon = ResponseCommon(
    "OK",
    Some("Status text"),
    LocalDateTime.now(),
    Some(List(MessagingServiceParam("POSITION", "GENERATE")))
  )

  val sampleResponseCommonWithBlankReturnParameters: ResponseCommon = sampleResponseCommon.copy(returnParameters = None)

  val sampleResponseCommonWithNoETMPFORMBUNDLENUMBER: ResponseCommon =
    sampleResponseCommon.copy(returnParameters = Some(List(MessagingServiceParam("POSITION", "GENERATE"))))

  val fullyPopulatedResponse: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommon, fullyPopulatedResponseDetail)

  val fullyPopulatedResponseWithoutFormBundle: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommonWithoutFormBundleNumber, fullyPopulatedResponseDetail)

  val onlyMandatoryPopulatedResponse: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommon, onlyMandatoryPopulatedResponseDetail)

  def mandatoryResponseWithConsentPopulated(consent: String): SubscriptionDisplayResponse = {
    val responseDetail = onlyMandatoryPopulatedResponseDetail.copy(consentToDisclosureOfPersonalData = Some(consent))

    onlyMandatoryPopulatedResponse.copy(responseDetail = responseDetail)
  }

  def fullyPopulatedResponseWithEmptyVATIDsList: SubscriptionDisplayResponse = {
    val responseDetail = fullyPopulatedResponseDetail.copy(VATIDs = Some(List.empty))

    fullyPopulatedResponse.copy(responseDetail = responseDetail)
  }

  def fullyPopulatedResponseWithDateOfEstablishment(dateOfEstablishment: LocalDate): SubscriptionDisplayResponse = {
    val responseDetail = fullyPopulatedResponseDetail.copy(dateOfEstablishment = Some(dateOfEstablishment))

    fullyPopulatedResponse.copy(responseDetail = responseDetail)
  }

  val fullyPopulatedResponseWithBlankReturnParameters: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommonWithBlankReturnParameters, fullyPopulatedResponseDetail)

  val fullyPopulatedResponseWithNoETMPFORMBUNDLENUMBER: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommonWithNoETMPFORMBUNDLENUMBER, fullyPopulatedResponseDetail)

  val responseWithoutContactDetails: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommon, onlyMandatoryPopulatedResponseDetail)

  val responseWithoutEmailAddress: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommon, responseDetailWithoutEmail)

  val responseWithUnverifiedEmailAddress: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommon, responseDetailWithUnverifiedEmail)

  val responseWithoutPersonOfContact: SubscriptionDisplayResponse =
    SubscriptionDisplayResponse(sampleResponseCommon, responseDetailWithoutPersonOfContact)

  val responseDetailsMissingDateOfEstablishment =
    SubscriptionDisplayResponse(sampleResponseCommon, fullyPopulatedResponseDetail.copy(dateOfEstablishment = None))

  def fullyPopulatedContactDetails: ContactDetails =
    ContactDetails(
      contactName,
      emailAddress,
      telephoneNumber,
      Some(faxNumber),
      contactStreet,
      contactCity,
      Some(contactPostalCode),
      orgCountryCode
    )

}
