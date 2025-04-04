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

package unit.domain.messaging

import base.UnitSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Address
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.{
  ContactInformation,
  SubscriptionCreateRequest,
  VatId
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{
  AddressViewModel,
  CompanyRegisteredCountry,
  ContactAddressModel
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service

import java.time.{LocalDate, LocalDateTime}

class SubscriptionCreateRequestSpec extends UnitSpec {

  private val email   = "john.doe@example.com"
  private val service = Service.withName("atar")

  private val cachedStreet: String           = "Cached street"
  private val cachedCity: String             = "Cached city"
  private val cachedPostCode: Option[String] = Some("Cached post code")
  private val cachedCountry: String          = "FR"

  private val subscriptionDetails = SubscriptionDetails(addressDetails =
    Some(AddressViewModel(cachedStreet, cachedCity, cachedPostCode, cachedCountry))
  )

  private val reg06Street: String = "Reg06 street"
  private val reg06City: String   = "Reg06 city"

  private def responseData(postCode: Option[String], countryCode: String): ResponseData =
    ResponseData(
      "safeId",
      Trader("full name", "short name"),
      EstablishmentAddress(reg06Street, reg06City, postCode, countryCode),
      hasInternetPublication = false,
      dateOfEstablishmentBirth = Some(LocalDate.now().toString),
      startDate = "start date"
    )

  private val eori                         = Eori("GB123456789123")
  private val taxPayerId                   = TaxPayerId("taxPayerId")
  private val safeId                       = SafeId("safeId")
  private val fullName                     = "Full name"
  private val address                      = Address("addressLine1", None, Some("city"), None, Some("postcode"), "GB")
  private val invalidAddress               = Address("addressLine1", None, Some("city"), None, Some("postcode"), "OO")
  private val establishmentAddress         = EstablishmentAddress("addressLine1", "city", Some("postcode"), "GB")
  private val invalildEstablishmentAddress = EstablishmentAddress("addressLine1", "city", Some("postcode"), "OO")
  private val addressViewModel             = AddressViewModel(address)
  private val dateOfBirthOrEstablishment   = LocalDate.now()

  private val emailAddress = Some("john.doe@example.com")

  private val contactDetails = ContactDetailsModel(
    fullName = fullName,
    emailAddress = emailAddress.get,
    telephone = "01234123123",
    fax = None,
    street = None,
    city = None,
    postcode = None,
    countryCode = None
  )

  private val contactDetail = ContactDetail(
    address = establishmentAddress,
    contactName = contactDetails.fullName,
    phone = Some(contactDetails.telephone),
    fax = None,
    email = emailAddress
  )

  private def reg01ExpectedContactInformation(timeStamp: LocalDateTime) = ContactInformation(
    Some(fullName),
    Some(false),
    None,
    None,
    None,
    None,
    Some("01234123123"),
    None,
    Some(email),
    Some(timeStamp)
  )

  val contactAddress: ContactAddressModel =
    ContactAddressModel("flat 20", Some("street line 2"), "city", Some("region"), Some("HJ2 3HJ"), "FR")

  private def reg01ExpectedContactInformationWithAddress(timeStamp: LocalDateTime) =
    ContactInformation(
      Some(fullName),
      Some(true),
      Some("flat 20 street line 2"),
      Some("city"),
      Some("HJ2 3HJ"),
      Some("FR"),
      Some("01234123123"),
      None,
      Some(email),
      Some(timeStamp)
    )

  private def reg06ExpectedContactInformation(timeStamp: LocalDateTime) = ContactInformation(
    Some(fullName),
    Some(true),
    Some("addressLine1"),
    Some("city"),
    Some("postcode"),
    Some("GB"),
    Some("01234123123"),
    None,
    Some(email),
    Some(timeStamp)
  )

  "Subscription Create Request" should {

    "correctly build request for individual ROW without UTR user based on the REG01 response" in {

      val service = Some(atarService)
      val registrationDetails = RegistrationDetailsIndividual(
        customsId = Some(eori),
        sapNumber = taxPayerId,
        safeId = safeId,
        name = fullName,
        address = address,
        dateOfBirth = dateOfBirthOrEstablishment
      )
      val subscriptionDetails = SubscriptionDetails(contactDetails = Some(contactDetails))

      val request = SubscriptionCreateRequest(registrationDetails, subscriptionDetails, service)

      val requestCommon  = request.requestCommon
      val requestDetails = request.requestDetail

      requestCommon.regime shouldBe "CDS"
      requestDetails.SAFE shouldBe safeId.id
      requestDetails.EORINo shouldBe Some(eori.id)
      requestDetails.CDSFullName shouldBe fullName
      requestDetails.CDSEstablishmentAddress shouldBe establishmentAddress
      requestDetails.establishmentInTheCustomsTerritoryOfTheUnion shouldBe None
      requestDetails.typeOfLegalEntity shouldBe Some("Unincorporated Body")
      requestDetails.contactInformation shouldBe Some(
        reg01ExpectedContactInformation(requestDetails.contactInformation.get.emailVerificationTimestamp.get)
      )
      requestDetails.vatIDs shouldBe None
      requestDetails.consentToDisclosureOfPersonalData shouldBe None
      requestDetails.shortName shouldBe None
      requestDetails.dateOfEstablishment shouldBe Some(dateOfBirthOrEstablishment)
      requestDetails.typeOfPerson shouldBe Some("1")
      requestDetails.principalEconomicActivity shouldBe None
      requestDetails.serviceName shouldBe Some(atarService.enrolmentKey)
    }

    "correctly build request for organisation ROW without UTR user based on the REG01 response" in {

      val service = Some(atarService)
      val registrationDetails = RegistrationDetailsOrganisation(
        customsId = Some(eori),
        sapNumber = taxPayerId,
        safeId = safeId,
        name = fullName,
        address = address,
        dateOfEstablishment = Some(dateOfBirthOrEstablishment),
        etmpOrganisationType = Some(CorporateBody)
      )
      val subscriptionDetails = SubscriptionDetails(contactDetails = Some(contactDetails))

      val request = SubscriptionCreateRequest(registrationDetails, subscriptionDetails, service)

      val requestCommon  = request.requestCommon
      val requestDetails = request.requestDetail

      requestCommon.regime shouldBe "CDS"
      requestDetails.SAFE shouldBe safeId.id
      requestDetails.EORINo shouldBe Some(eori.id)
      requestDetails.CDSFullName shouldBe fullName
      requestDetails.CDSEstablishmentAddress shouldBe establishmentAddress
      requestDetails.establishmentInTheCustomsTerritoryOfTheUnion shouldBe None
      requestDetails.typeOfLegalEntity shouldBe Some("Corporate Body")
      requestDetails.contactInformation shouldBe Some(
        reg01ExpectedContactInformation(requestDetails.contactInformation.get.emailVerificationTimestamp.get)
      )
      requestDetails.vatIDs shouldBe None
      requestDetails.consentToDisclosureOfPersonalData shouldBe None
      requestDetails.shortName shouldBe None
      requestDetails.dateOfEstablishment shouldBe Some(dateOfBirthOrEstablishment)
      requestDetails.typeOfPerson shouldBe Some("2")
      requestDetails.principalEconomicActivity shouldBe None
      requestDetails.serviceName shouldBe Some(atarService.enrolmentKey)
    }

    "correctly build request for individual ROW with Contact Address for users  without UTR  based on the REG01 response" in {

      val registrationDetails = RegistrationDetailsIndividual(
        customsId = Some(eori),
        sapNumber = taxPayerId,
        safeId = safeId,
        name = fullName,
        address = address,
        dateOfBirth = dateOfBirthOrEstablishment
      )

      val service = Some(atarService)

      val subscriptionDetails =
        SubscriptionDetails(contactDetails = Some(contactDetails), contactAddress = Some(contactAddress))

      val request = SubscriptionCreateRequest(registrationDetails, subscriptionDetails, service)

      val requestCommon  = request.requestCommon
      val requestDetails = request.requestDetail

      requestCommon.regime shouldBe "CDS"
      requestDetails.SAFE shouldBe safeId.id
      requestDetails.EORINo shouldBe Some(eori.id)
      requestDetails.CDSFullName shouldBe fullName
      requestDetails.CDSEstablishmentAddress shouldBe establishmentAddress
      requestDetails.establishmentInTheCustomsTerritoryOfTheUnion shouldBe None
      requestDetails.typeOfLegalEntity shouldBe Some("Unincorporated Body")
      requestDetails.contactInformation shouldBe Some(
        reg01ExpectedContactInformationWithAddress(requestDetails.contactInformation.get.emailVerificationTimestamp.get)
      )
      requestDetails.vatIDs shouldBe None
      requestDetails.consentToDisclosureOfPersonalData shouldBe None
      requestDetails.shortName shouldBe None
      requestDetails.dateOfEstablishment shouldBe Some(dateOfBirthOrEstablishment)
      requestDetails.typeOfPerson shouldBe Some("1")
      requestDetails.principalEconomicActivity shouldBe None
      requestDetails.serviceName shouldBe Some(atarService.enrolmentKey)
    }

    "correctly build request for organisation ROW with Contact Address for users  without UTR  based on the REG01 response" in {

      val service = Some(atarService)
      val registrationDetails = RegistrationDetailsOrganisation(
        customsId = Some(eori),
        sapNumber = taxPayerId,
        safeId = safeId,
        name = fullName,
        address = address,
        dateOfEstablishment = Some(dateOfBirthOrEstablishment),
        etmpOrganisationType = Some(CorporateBody)
      )
      val subscriptionDetails =
        SubscriptionDetails(contactDetails = Some(contactDetails), contactAddress = Some(contactAddress))

      val request = SubscriptionCreateRequest(registrationDetails, subscriptionDetails, service)

      val requestCommon  = request.requestCommon
      val requestDetails = request.requestDetail

      requestCommon.regime shouldBe "CDS"
      requestDetails.SAFE shouldBe safeId.id
      requestDetails.EORINo shouldBe Some(eori.id)
      requestDetails.CDSFullName shouldBe fullName
      requestDetails.CDSEstablishmentAddress shouldBe establishmentAddress
      requestDetails.establishmentInTheCustomsTerritoryOfTheUnion shouldBe None
      requestDetails.typeOfLegalEntity shouldBe Some("Corporate Body")
      requestDetails.contactInformation shouldBe Some(
        reg01ExpectedContactInformationWithAddress(requestDetails.contactInformation.get.emailVerificationTimestamp.get)
      )
      requestDetails.vatIDs shouldBe None
      requestDetails.consentToDisclosureOfPersonalData shouldBe None
      requestDetails.shortName shouldBe None
      requestDetails.dateOfEstablishment shouldBe Some(dateOfBirthOrEstablishment)
      requestDetails.typeOfPerson shouldBe Some("2")
      requestDetails.principalEconomicActivity shouldBe None
      requestDetails.serviceName shouldBe Some(atarService.enrolmentKey)
    }

    "correctly build request based on the REG06 response" in {

      val responseData = ResponseData(
        SAFEID = safeId.id,
        trader = Trader(fullName, "short name"),
        establishmentAddress = establishmentAddress,
        contactDetail = Some(contactDetail),
        VATIDs = Some(Seq(VatIds("GB", "123456"))),
        hasInternetPublication = false,
        principalEconomicActivity = Some("principal economic activity"),
        hasEstablishmentInCustomsTerritory = Some(false),
        legalStatus = Some("legal status"),
        thirdCountryIDNumber = Some(Seq("idNumber")),
        dateOfEstablishmentBirth = Some(dateOfBirthOrEstablishment.toString),
        personType = Some(1),
        startDate = "start date",
        expiryDate = Some("expiry date")
      )

      val subscriptionDetails = SubscriptionDetails(eoriNumber = Some(eori.id), addressDetails = Some(addressViewModel))

      val request = SubscriptionCreateRequest(responseData, subscriptionDetails, email, Some(atarService))

      val requestCommon  = request.requestCommon
      val requestDetails = request.requestDetail

      requestCommon.regime shouldBe "CDS"
      requestDetails.SAFE shouldBe safeId.id
      requestDetails.EORINo shouldBe Some(eori.id)
      requestDetails.CDSFullName shouldBe fullName
      requestDetails.CDSEstablishmentAddress shouldBe establishmentAddress
      requestDetails.establishmentInTheCustomsTerritoryOfTheUnion shouldBe Some("0")
      requestDetails.typeOfLegalEntity shouldBe Some("legal status")
      requestDetails.contactInformation shouldBe Some(
        reg06ExpectedContactInformation(requestDetails.contactInformation.get.emailVerificationTimestamp.get)
      )
      requestDetails.vatIDs shouldBe Some(Seq(VatId(Some("GB"), Some("123456"))))
      requestDetails.consentToDisclosureOfPersonalData shouldBe None
      requestDetails.shortName shouldBe Some("short name")
      requestDetails.dateOfEstablishment shouldBe Some(dateOfBirthOrEstablishment)
      requestDetails.typeOfPerson shouldBe Some("1")
      requestDetails.principalEconomicActivity shouldBe Some("principal economic activity")
      requestDetails.serviceName shouldBe Some(atarService.enrolmentKey)
    }

    "correctly generate the request with REG06 establishment address" when {

      "REG06 establishment address contains correct country code" in {

        val reg06Country                 = "GB"
        val reg06PostCode                = Some("AA11 1AA")
        val reg06ResponseData            = responseData(postCode = reg06PostCode, countryCode = reg06Country)
        val sub02Request                 = SubscriptionCreateRequest(reg06ResponseData, subscriptionDetails, email, service)
        val expectedEstablishmentAddress = EstablishmentAddress(reg06Street, reg06City, reg06PostCode, reg06Country)

        sub02Request.requestDetail.CDSEstablishmentAddress shouldBe expectedEstablishmentAddress
      }
    }

    "correctly generate the request with country from user" when {

      "REG06 contains incorrect country" in {

        val reg06Country                 = "incorrect"
        val reg06PostCode                = Some("AA11 1AA")
        val reg06ResponseData            = responseData(postCode = reg06PostCode, countryCode = reg06Country)
        val sub02Request                 = SubscriptionCreateRequest(reg06ResponseData, subscriptionDetails, email, service)
        val expectedEstablishmentAddress = EstablishmentAddress(reg06Street, reg06City, reg06PostCode, cachedCountry)

        sub02Request.requestDetail.CDSEstablishmentAddress shouldBe expectedEstablishmentAddress
      }
    }

    "build request with invalid establishment address in Reg06 response for organisation ROW without UTR user based on the REG01 response" in {

      val service = Some(atarService)
      val registrationDetails = RegistrationDetailsOrganisation(
        customsId = Some(eori),
        sapNumber = taxPayerId,
        safeId = safeId,
        name = fullName,
        address = invalidAddress,
        dateOfEstablishment = Some(dateOfBirthOrEstablishment),
        etmpOrganisationType = Some(CorporateBody)
      )
      val subscriptionDetails = SubscriptionDetails(
        contactDetails = Some(contactDetails),
        registeredCompany = Some(CompanyRegisteredCountry("GB"))
      )

      val request = SubscriptionCreateRequest(registrationDetails, subscriptionDetails, service)

      val requestCommon  = request.requestCommon
      val requestDetails = request.requestDetail

      requestCommon.regime shouldBe "CDS"
      requestDetails.SAFE shouldBe safeId.id
      requestDetails.EORINo shouldBe Some(eori.id)
      requestDetails.CDSFullName shouldBe fullName
      requestDetails.CDSEstablishmentAddress shouldBe establishmentAddress
      requestDetails.establishmentInTheCustomsTerritoryOfTheUnion shouldBe None
      requestDetails.typeOfLegalEntity shouldBe Some("Corporate Body")
      requestDetails.contactInformation shouldBe Some(
        reg01ExpectedContactInformation(requestDetails.contactInformation.get.emailVerificationTimestamp.get)
      )
      requestDetails.vatIDs shouldBe None
      requestDetails.consentToDisclosureOfPersonalData shouldBe None
      requestDetails.shortName shouldBe None
      requestDetails.dateOfEstablishment shouldBe Some(dateOfBirthOrEstablishment)
      requestDetails.typeOfPerson shouldBe Some("2")
      requestDetails.principalEconomicActivity shouldBe None
      requestDetails.serviceName shouldBe Some(atarService.enrolmentKey)
    }

    "throw exception when  establishment address details are incorrect  in Reg06 response for organisation ROW without UTR user based on the REG01 response" in {

      val service = Some(atarService)
      val registrationDetails = RegistrationDetailsOrganisation(
        customsId = Some(eori),
        sapNumber = taxPayerId,
        safeId = safeId,
        name = fullName,
        address = invalidAddress,
        dateOfEstablishment = Some(dateOfBirthOrEstablishment),
        etmpOrganisationType = Some(CorporateBody)
      )
      val subscriptionDetails = SubscriptionDetails(contactDetails = Some(contactDetails))
      intercept[Exception] {
        SubscriptionCreateRequest(registrationDetails, subscriptionDetails, service)
      }
    }

    "throw IllegalArgumentException if registrationDetails is invalid " in {

      val service = Some(atarService)
      val registrationDetails = RegistrationDetailsSafeId(
        safeId = safeId,
        address = address,
        sapNumber = taxPayerId,
        customsId = Some(eori),
        name = fullName
      )
      val subscriptionDetails = SubscriptionDetails(contactDetails = Some(contactDetails))
      intercept[IllegalArgumentException] {

        SubscriptionCreateRequest(registrationDetails, subscriptionDetails, service)
      }

    }
    "throw IllegalStateException when invalid address is not present in subscription details" in {

      val responseData = ResponseData(
        SAFEID = safeId.id,
        trader = Trader(fullName, "short name"),
        establishmentAddress = invalildEstablishmentAddress,
        contactDetail = Some(contactDetail),
        VATIDs = Some(Seq(VatIds("GB", "123456"))),
        hasInternetPublication = false,
        principalEconomicActivity = Some("principal economic activity"),
        hasEstablishmentInCustomsTerritory = Some(false),
        legalStatus = Some("legal status"),
        thirdCountryIDNumber = Some(Seq("idNumber")),
        dateOfEstablishmentBirth = Some(dateOfBirthOrEstablishment.toString),
        personType = Some(1),
        startDate = "start date",
        expiryDate = Some("expiry date")
      )

      val subscriptionDetails = SubscriptionDetails(eoriNumber = Some(eori.id), addressDetails = None)
      intercept[IllegalStateException] {

        SubscriptionCreateRequest(responseData, subscriptionDetails, email, Some(atarService))
      }

    }

  }
}
