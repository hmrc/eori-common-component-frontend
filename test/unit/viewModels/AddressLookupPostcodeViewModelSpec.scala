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

package unit.viewModels

import base.UnitSpec
import play.api.mvc.Call
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.eoricommoncomponent.frontend.viewModels.AddressLookupPostcodeViewModel

class AddressLookupPostcodeViewModelSpec extends UnitSpec {

  "AddressLookupPostcodeViewModel" should {
    "return the expected model for the company in reviewMode" in {
      val isInReviewMode                                = true
      val selectedOrganisationType: CdsOrganisationType = CdsOrganisationType.Company

      val expectedPageTitleKey      = "ecc.address-lookup.postcode.organisation.title"
      val expectedFormHintKey       = "ecc.address-lookup.postcode.organisation.hint"
      val expectedHintTextKey       = None
      val expectedAddressLink: Call = Call("GET", "/customs-enrolment-services/atar/subscribe/address/review")

      val viewModel: AddressLookupPostcodeViewModel =
        AddressLookupPostcodeViewModel(isInReviewMode, selectedOrganisationType, atarService)

      viewModel.pageTitleKey shouldBe expectedPageTitleKey
      viewModel.formHintKey shouldBe expectedFormHintKey
      viewModel.hintTextKey shouldBe expectedHintTextKey
      viewModel.addressLink shouldBe expectedAddressLink
    }
    "return the expected model for the company" in {
      val isInReviewMode                                = false
      val selectedOrganisationType: CdsOrganisationType = CdsOrganisationType.Company

      val expectedPageTitleKey      = "ecc.address-lookup.postcode.organisation.title"
      val expectedFormHintKey       = "ecc.address-lookup.postcode.organisation.hint"
      val expectedHintTextKey       = None
      val expectedAddressLink: Call = Call("GET", "/customs-enrolment-services/atar/subscribe/address")

      val viewModel: AddressLookupPostcodeViewModel =
        AddressLookupPostcodeViewModel(isInReviewMode, selectedOrganisationType, atarService)

      viewModel.pageTitleKey shouldBe expectedPageTitleKey
      viewModel.formHintKey shouldBe expectedFormHintKey
      viewModel.hintTextKey shouldBe expectedHintTextKey
      viewModel.addressLink shouldBe expectedAddressLink
    }
  }

}
