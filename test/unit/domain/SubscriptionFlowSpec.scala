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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.DataUnavailableException

class SubscriptionFlowSpec extends UnitSpec {

  "SubscriptionFlow object" should {
    "create for valid flow name" in {
      SubscriptionFlow("migration-eori-Organisation") shouldBe OrganisationFlow
    }

    "throw an exception for create for invalid flow name" in {
      val thrown = intercept[DataUnavailableException] {
        SubscriptionFlow("DOES_NOT_EXISTS") shouldBe OrganisationFlow
      }
      thrown.getMessage shouldBe s"Unknown Subscription flowname DOES_NOT_EXISTS"
    }

    "create for valid flow name for ROW organisation" in {
      SubscriptionFlow("migration-eori-row-utrNino-enabled-Organisation") shouldBe RowOrganisationFlow
    }

    "create for valid flow name for ROW individual" in {
      SubscriptionFlow("migration-eori-row-utrNino-enabled-Individual") shouldBe RowIndividualFlow
    }
  }

  "flows" should {

    "returns the flowConfig for RowIndividualFlow" in {
      SubscriptionFlows(RowIndividualFlow) shouldBe SubscriptionFlowConfig(
        pageBeforeFirstFlowPage = UserLocationPage,
        List(
          NameDobDetailsSubscriptionFlowPage,
          UtrSubscriptionFlowPage,
          NinoSubscriptionFlowPage,
          AddressDetailsSubscriptionFlowPage,
          ContactDetailsSubscriptionFlowPageMigrate,
          ContactAddressSubscriptionFlowPage,
          ConfirmContactAddressSubscriptionFlowPage
        ),
        pageAfterLastFlowPage = ReviewDetailsPageSubscription
      )
    }

  }

}
