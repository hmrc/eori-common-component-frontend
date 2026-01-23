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

package unit.forms.models.registration

import base.UnitSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel

class ContactDetailsModelSpec extends UnitSpec {

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

  ".contactDetailsModel" should {
    "return empty string for empty space in street" in {
      contactDetails.copy(street = Some("   ")).contactDetails.street shouldBe ""
    }

    "return empty string for no street provided" in {
      contactDetails.copy(street = None).contactDetails.street shouldBe ""
    }

    "return empty string for empty space in city" in {
      contactDetails.copy(city = Some("   ")).contactDetails.city shouldBe ""
    }

    "return empty string for no city provided" in {
      contactDetails.copy(city = None).contactDetails.city shouldBe ""
    }

    "return empty string for empty space in postcode" in {
      contactDetails.copy(postcode = Some("   ")).contactDetails.postcode shouldBe Some("")
    }

    "return empty string for no country code provided" in {
      contactDetails.copy(countryCode = None).contactDetails.countryCode shouldBe ""
    }
  }

}
