/*
 * Copyright 2021 HM Revenue & Customs
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
import com.github.nscala_time.time.Imports.LocalDate
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.SubscriptionCreateRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EstablishmentAddress, ResponseData, Trader}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service

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

  "Subscription Create Request" should {

    "correctly generate request with REG06 response address" when {

      "REG06 response address is outside the UK" in {
        val reg06Country                 = "PL"
        val reg06ResponseData            = responseData(postCode = None, countryCode = reg06Country)
        val sub02Request                 = SubscriptionCreateRequest(reg06ResponseData, subscriptionDetails, email, service)
        val expectedEstablishmentAddress = EstablishmentAddress(reg06Street, reg06City, None, reg06Country)

        sub02Request.requestDetail.CDSEstablishmentAddress shouldBe expectedEstablishmentAddress
      }

      "REG06 response address is in UK and postcode is correct" in {

        val reg06Country                 = "GB"
        val reg06PostCode                = Some("AA11 1AA")
        val reg06ResponseData            = responseData(postCode = reg06PostCode, countryCode = reg06Country)
        val sub02Request                 = SubscriptionCreateRequest(reg06ResponseData, subscriptionDetails, email, service)
        val expectedEstablishmentAddress = EstablishmentAddress(reg06Street, reg06City, reg06PostCode, reg06Country)

        sub02Request.requestDetail.CDSEstablishmentAddress shouldBe expectedEstablishmentAddress
      }
    }

    "correctly generate request with address provided by user" when {

      "REG06 response address is in the UK and has incorrect post code" in {

        val reg06Country                 = "GB"
        val reg06PostCode                = Some("incorrect")
        val reg06ResponseData            = responseData(postCode = reg06PostCode, countryCode = reg06Country)
        val sub02Request                 = SubscriptionCreateRequest(reg06ResponseData, subscriptionDetails, email, service)
        val expectedEstablishmentAddress = EstablishmentAddress(cachedStreet, cachedCity, cachedPostCode, cachedCountry)

        sub02Request.requestDetail.CDSEstablishmentAddress shouldBe expectedEstablishmentAddress
      }

      "REG06 response address is in the UK and has missing postcode" in {

        val reg06Country                 = "GB"
        val reg06PostCode                = None
        val reg06ResponseData            = responseData(postCode = reg06PostCode, countryCode = reg06Country)
        val sub02Request                 = SubscriptionCreateRequest(reg06ResponseData, subscriptionDetails, email, service)
        val expectedEstablishmentAddress = EstablishmentAddress(cachedStreet, cachedCity, cachedPostCode, cachedCountry)

        sub02Request.requestDetail.CDSEstablishmentAddress shouldBe expectedEstablishmentAddress
      }
    }
  }
}
