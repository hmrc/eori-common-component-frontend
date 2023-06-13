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

package uk.gov.hmrc.eoricommoncomponent.frontend.viewModels

import play.api.mvc.Call
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service

case class AddressLookupPostcodeViewModel(
  pageTitleKey: String,
  formHintKey: String,
  hintTextKey: String,
  addressLink: Call
)

object AddressLookupPostcodeViewModel {

  def apply(
    isInReviewMode: Boolean,
    selectedOrganisationType: CdsOrganisationType,
    service: Service
  ): AddressLookupPostcodeViewModel = {
    val pageTitleKey = selectedOrganisationType.id match {
      case CompanyId                       => "ecc.address-lookup.postcode.organisation.title"
      case SoleTraderId | IndividualId     => "ecc.address-lookup.postcode.individual.title"
      case PartnershipId                   => "ecc.address-lookup.postcode.partnership.title"
      case LimitedLiabilityPartnershipId   => "ecc.address-lookup.postcode.partnership.title"
      case CharityPublicBodyNotForProfitId => "ecc.address-lookup.postcode.charity.title"
      case _                               => "ecc.address-lookup.postcode.organisation.title"
    }

    val formHintKey = selectedOrganisationType.id match {
      case PartnershipId => "ecc.address-lookup.postcode.partnership.hint.text"
      case _             => "ecc.address-lookup.postcode.organisation.hint"
    }

    val hintTextKey = selectedOrganisationType.id match {
      case CompanyId                       => "ecc.address-lookup.postcode.hint.company"
      case LimitedLiabilityPartnershipId   => "ecc.address-lookup.postcode.hint.llp"
      case CharityPublicBodyNotForProfitId => "ecc.address-lookup.postcode.hint.organisation"
      case _                               => "ecc.address-lookup.postcode.hint.partnership"
    }

    val addressLink = {
      if (isInReviewMode)
        uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController.reviewForm(service)
      else uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController.createForm(service)
    }
    AddressLookupPostcodeViewModel(pageTitleKey, formHintKey, hintTextKey, addressLink)

  }

}
