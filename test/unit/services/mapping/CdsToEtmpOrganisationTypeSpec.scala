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

package unit.services.mapping

import base.UnitSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Address
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.*
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.DataUnavailableException
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.EtmpTypeOfPerson.{AssociationOfPerson, LegalPerson}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.{
  CdsToEtmpOrganisationType,
  EtmpLegalStatus,
  OrganisationTypeConfiguration
}

class CdsToEtmpOrganisationTypeSpec extends UnitSpec {

  "CdsToEtmpOrganisationType" should {
    "map partnership" in {

      val orgDetails = new RegistrationDetailsOrganisation(
        None,
        TaxPayerId(""),
        SafeId(""),
        "",
        Address("", None, None, None, None, ""),
        None,
        Some(Partnership)
      )

      CdsToEtmpOrganisationType(orgDetails).head shouldBe OrganisationTypeConfiguration(
        LegalPerson,
        EtmpLegalStatus.Partnership
      )
    }

    "map LLP" in {
      val orgDetails = new RegistrationDetailsOrganisation(
        None,
        TaxPayerId(""),
        SafeId(""),
        "",
        Address("", None, None, None, None, ""),
        None,
        Some(LLP)
      )

      CdsToEtmpOrganisationType(orgDetails).head shouldBe OrganisationTypeConfiguration(
        LegalPerson,
        EtmpLegalStatus.Llp
      )
    }

    "map unincorporated body" in {
      val orgDetails = new RegistrationDetailsOrganisation(
        None,
        TaxPayerId(""),
        SafeId(""),
        "",
        Address("", None, None, None, None, ""),
        None,
        Some(UnincorporatedBody)
      )

      CdsToEtmpOrganisationType(orgDetails).head shouldBe OrganisationTypeConfiguration(
        AssociationOfPerson,
        EtmpLegalStatus.UnincorporatedBody
      )
    }

    "throw exception on incomplete journey details" in {

      val eori       = Eori("GB123456789123")
      val taxPayerId = TaxPayerId("taxPayerId")
      val safeId     = SafeId("safeId")
      val fullName   = "Full name"
      val address    = Address("addressLine1", None, Some("city"), None, Some("postcode"), "GB")

      val registrationDetails = RegistrationDetailsSafeId(
        safeId = safeId,
        address = address,
        sapNumber = taxPayerId,
        customsId = Some(eori),
        name = fullName
      )

      intercept[DataUnavailableException] {
        CdsToEtmpOrganisationType.apply(registrationDetails)
      }
    }
  }
}
