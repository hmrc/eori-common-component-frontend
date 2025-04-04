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
import play.api.libs.json.{JsSuccess, JsValue, Json}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._

class MatchingIdFormatSpec extends UnitSpec {

  val id: String               = java.util.UUID.randomUUID.toString
  val UTR: Utr                 = Utr(id)
  val EORI: Eori               = Eori(id)
  val NINO: Nino               = Nino(id)
  val SAFE_ID: SafeId          = SafeId(id)
  val TAX_PAYER_ID: TaxPayerId = TaxPayerId(id)

  val utrJson: JsValue        = Json.parse(s"""{ "utr": "$id" }""")
  val eoriJson: JsValue       = Json.parse(s"""{ "eori": "$id" }""")
  val ninoJson: JsValue       = Json.parse(s"""{ "nino": "$id" }""")
  val safeIdJson: JsValue     = Json.parse(s"""{ "safeId": "$id" }""")
  val taxPayerIdJson: JsValue = Json.parse(s"""{ "taxPayerId": "$id" }""")

  "UTR" should {
    passJsonTransformationCheck(UTR, utrJson)
  }

  "EORI" should {
    passJsonTransformationCheck(EORI, eoriJson)
  }

  "NINO" should {
    passJsonTransformationCheck(NINO, ninoJson)
  }

  "SAFE ID" should {
    passJsonTransformationCheck(SAFE_ID, safeIdJson)
  }

  "TAX PAYER ID" should {
    passJsonTransformationCheck(TAX_PAYER_ID, taxPayerIdJson)
  }

  private def passJsonTransformationCheck(customsId: CustomsId, expectedJson: JsValue): Unit = {
    "be marshalled" in {
      Json.toJson(customsId) shouldBe expectedJson
    }

    "be unmarshalled" in {
      Json.fromJson[CustomsId](expectedJson) shouldBe JsSuccess(customsId)
    }
  }

}
