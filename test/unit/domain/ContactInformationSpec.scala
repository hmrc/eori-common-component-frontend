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

package unit.domain

import base.UnitSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{ContactDetail, EstablishmentAddress}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.ContactInformation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ContactDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel

import java.time.LocalDateTime

class ContactInformationSpec extends UnitSpec {

  "ContactInformation" should {
    val contactAddress =
      ContactAddressModel("flat 20", Some("street line 2"), "city", Some("region"), Some("HJ2 3HJ"), "FR")
    val contactDetails = ContactDetailsModel(
      "Full name",
      "email",
      "01234123123",
      None,
      useAddressFromRegistrationDetails = false,
      Some("street"),
      Some("city"),
      Some("postCode"),
      Some("countryCode")
    )
    val contactInformationWithContactAddress =
      ContactInformation.apply(contactDetails, Some(contactAddress))
    val contactInformationWithoutContactAddress =
      ContactInformation.apply(contactDetails, None)

    "creates contact info with email" in {
      contactInformationWithContactAddress.personOfContact shouldBe Some("Full name")
      contactInformationWithContactAddress.emailAddress shouldBe Some("email")
      contactInformationWithContactAddress.faxNumber shouldBe None
      contactInformationWithContactAddress.sepCorrAddrIndicator shouldBe Some(true)
      contactInformationWithContactAddress.streetAndNumber shouldBe Some("flat 20 street line 2")
      contactInformationWithContactAddress.city shouldBe Some("city")
      contactInformationWithContactAddress.postalCode shouldBe Some("HJ2 3HJ")
      contactInformationWithContactAddress.countryCode shouldBe Some("FR")
      contactInformationWithContactAddress.telephoneNumber shouldBe Some("01234123123")

    }
    "creates contact info with telephone number empty " in {
      val contactDetailsTest =
        ContactDetailsModel(
          "Full name",
          "email",
          "",
          None,
          useAddressFromRegistrationDetails = false,
          Some("street"),
          Some("city"),
          Some("postCode"),
          Some("countryCode")
        )

      val contactInformationWithContactAddress =
        ContactInformation.apply(contactDetailsTest, Some(contactAddress))

      contactInformationWithContactAddress.personOfContact shouldBe Some("Full name")
      contactInformationWithContactAddress.emailAddress shouldBe Some("email")
      contactInformationWithContactAddress.faxNumber shouldBe None
      contactInformationWithContactAddress.sepCorrAddrIndicator shouldBe Some(true)
      contactInformationWithContactAddress.streetAndNumber shouldBe Some("flat 20 street line 2")
      contactInformationWithContactAddress.city shouldBe Some("city")
      contactInformationWithContactAddress.postalCode shouldBe Some("HJ2 3HJ")
      contactInformationWithContactAddress.countryCode shouldBe Some("FR")
      contactInformationWithContactAddress.telephoneNumber shouldBe None

    }

    "populate contact information from contact address and email" in {
      val contactDetailsTest = ContactDetails(
        "fullName",
        "email@email.email",
        "00000000000",
        Some("fax"),
        "Street",
        "city",
        Some("postcode"),
        "UK"
      )
      val contactInfo = ContactInformation.createContactInformation(contactDetailsTest)
      contactInfo.personOfContact shouldBe Some("fullName")
      contactInfo.emailAddress shouldBe Some("email@email.email")
      contactInfo.faxNumber shouldBe Some("fax")
      contactInfo.sepCorrAddrIndicator shouldBe Some(true)
      contactInfo.streetAndNumber shouldBe Some("Street")
      contactInfo.city shouldBe Some("city")
      contactInfo.postalCode shouldBe Some("postcode")
      contactInfo.countryCode shouldBe Some("UK")
      contactInfo.telephoneNumber shouldBe Some("00000000000")

    }
    "populate contact information from contact address and email where telephone is None" in {

      val contactDetailsTest =
        ContactDetails("fullName", "email@email.email", "", Some("fax"), "Street", "city", Some("postcode"), "UK")
      val contactInfo = ContactInformation.createContactInformation(contactDetailsTest)
      contactInfo.personOfContact shouldBe Some("fullName")
      contactInfo.emailAddress shouldBe Some("email@email.email")
      contactInfo.faxNumber shouldBe Some("fax")
      contactInfo.sepCorrAddrIndicator shouldBe Some(true)
      contactInfo.streetAndNumber shouldBe Some("Street")
      contactInfo.city shouldBe Some("city")
      contactInfo.postalCode shouldBe Some("postcode")
      contactInfo.countryCode shouldBe Some("UK")
      contactInfo.telephoneNumber shouldBe Some("")

    }

    "populate None for address if contact address is None" in {
      contactInformationWithoutContactAddress.personOfContact shouldBe Some("Full name")
      contactInformationWithoutContactAddress.emailAddress shouldBe Some("email")
      contactInformationWithoutContactAddress.faxNumber shouldBe None
      contactInformationWithoutContactAddress.sepCorrAddrIndicator shouldBe Some(false)
      contactInformationWithoutContactAddress.streetAndNumber shouldBe None
      contactInformationWithoutContactAddress.city shouldBe None
      contactInformationWithoutContactAddress.postalCode shouldBe None
      contactInformationWithoutContactAddress.countryCode shouldBe None
      contactInformationWithoutContactAddress.telephoneNumber shouldBe Some("01234123123")

    }
    "populate None for address if contact telephone is None" in {
      val contactDetailsNoTell = ContactDetailsModel(
        "Full name",
        "email",
        "",
        None,
        useAddressFromRegistrationDetails = false,
        Some("street"),
        Some("city"),
        Some("postCode"),
        Some("countryCode")
      )
      val contactInformationWithoutContactAddressNoTell =
        ContactInformation.apply(contactDetailsNoTell, None)

      contactInformationWithoutContactAddress.personOfContact shouldBe Some("Full name")
      contactInformationWithoutContactAddress.emailAddress shouldBe Some("email")
      contactInformationWithoutContactAddress.faxNumber shouldBe None
      contactInformationWithoutContactAddress.sepCorrAddrIndicator shouldBe Some(false)
      contactInformationWithoutContactAddress.streetAndNumber shouldBe None
      contactInformationWithoutContactAddress.city shouldBe None
      contactInformationWithoutContactAddress.postalCode shouldBe None
      contactInformationWithoutContactAddress.countryCode shouldBe None
      contactInformationWithoutContactAddressNoTell.telephoneNumber shouldBe None

    }

    "convert contact Details to ContactInformation" in {
      val date: LocalDateTime = LocalDateTime.now()
      val contactDetail = ContactDetail(
        EstablishmentAddress("Line 1", "City Name", Some("SE28 1AA"), "GB"),
        "John Contact Doe",
        Some("1234567"),
        Some("89067"),
        Some("john.doe@example.com")
      )
      val result =
        ContactInformation.createContactInformation(contactDetail).copy(emailVerificationTimestamp = Some(date))
      val expectedResult = ContactInformation(
        Some("John Contact Doe"),
        Some(true),
        Some("Line 1"),
        Some("City Name"),
        Some("SE28 1AA"),
        Some("GB"),
        Some("1234567"),
        Some("89067"),
        Some("john.doe@example.com"),
        Some(date)
      )

      result shouldBe expectedResult
      result.withEmail("test@gmail.com") shouldBe expectedResult.copy(emailAddress = Some("test@gmail.com"))
    }
  }
}
