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

package unit.services.cache

import base.UnitSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.{Address, ResponseCommon}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.CachedData

import java.time.LocalDateTime

class CachedDataSpec extends UnitSpec with MockitoSugar {

  val sessionId: String      = "1234567"
  implicit val mockRequest   = mock[Request[_]]
  def errorMsg(name: String) = s"$name is not cached in data for the sessionId: ${sessionId}"

  "CachedData" should {

    "throw IllegalStateException" when {

      "safeId missing " in {
        intercept[Exception](CachedData().safeId(sessionId)).getMessage shouldBe errorMsg(CachedData.safeIdKey)
      }
    }

    "return SAFEID" when {

      "registerWithEoriAndIdResponse" in {
        val safeId = "someSafeId"
        CachedData(registerWithEoriAndIdResponse = Some(registerWithEoriAndIdResponse(safeId))).safeId(
          sessionId
        ) shouldBe SafeId(safeId)
      }

      "registrationDetails" in {
        val safeId = "anotherSafeId"
        CachedData(regDetails = Some(registrationDetails(safeId))).safeId(sessionId) shouldBe SafeId(safeId)
      }
    }
  }

  def registrationDetails(safeId: String) = RegistrationDetailsSafeId(
    SafeId(safeId),
    Address("", Some(""), Some(""), Some(""), Some(""), ""),
    TaxPayerId(""),
    None,
    ""
  )

  def registerWithEoriAndIdResponse(safeId: String) = RegisterWithEoriAndIdResponse(
    ResponseCommon("OK", None, LocalDateTime.now(), None),
    Some(
      RegisterWithEoriAndIdResponseDetail(
        Some("PASS"),
        Some("C001"),
        responseData = Some(
          ResponseData(
            safeId,
            Trader("John Doe", "Mr D"),
            EstablishmentAddress("Line 1", "City Name", Some("SE28 1AA"), "GB"),
            Some(
              ContactDetail(
                EstablishmentAddress("Line 1", "City Name", Some("SE28 1AA"), "GB"),
                "John Contact Doe",
                Some("1234567"),
                Some("89067"),
                Some("john.doe@example.com")
              )
            ),
            VATIDs = Some(Seq(VatIds("AD", "1234"), VatIds("GB", "4567"))),
            hasInternetPublication = false,
            principalEconomicActivity = Some("P001"),
            hasEstablishmentInCustomsTerritory = Some(true),
            legalStatus = Some("Official"),
            thirdCountryIDNumber = Some(Seq("1234", "67890")),
            personType = Some(9),
            dateOfEstablishmentBirth = Some("2018-05-16"),
            startDate = "2018-05-15",
            expiryDate = Some("2018-05-16")
          )
        )
      )
    )
  )

}
