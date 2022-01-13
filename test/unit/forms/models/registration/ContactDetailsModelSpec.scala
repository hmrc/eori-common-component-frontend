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

package unit.forms.models.registration

import base.UnitSpec
import java.time.LocalDateTime

import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.ContactInformation
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel

class ContactDetailsModelSpec extends UnitSpec {

  "Contact Details model" should {

    "correctly convert data with address data" in {

      val contactDetails =
        ContactDetailsModel(
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

      def expectedContactInformation(emailVerificationTimestamp: Option[LocalDateTime]) = ContactInformation(
        personOfContact = Some("Full name"),
        sepCorrAddrIndicator = Some(false),
        telephoneNumber = Some("01234123123"),
        emailAddress = Some("email"),
        streetAndNumber = Some("street"),
        city = Some("city"),
        postalCode = Some("postCode"),
        countryCode = Some("countryCode"),
        emailVerificationTimestamp = emailVerificationTimestamp
      )

      val contactInformation = contactDetails.toRowContactInformation()

      val timestamp = contactInformation.emailVerificationTimestamp

      contactInformation shouldBe expectedContactInformation(timestamp)
    }
  }
}
