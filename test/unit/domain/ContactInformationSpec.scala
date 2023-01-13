/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.ContactInformation
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel

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

    "populate contact information from contact address" in {
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
  }
}
