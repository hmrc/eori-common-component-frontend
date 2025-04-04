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

package unit.domain.messaging.matching

import base.UnitSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.matching.ResponseDetail
import unit.services.registration.MatchingServiceTestData

class RegisterWithIDResponseSpec extends UnitSpec with MatchingServiceTestData {

  "getResponseDetail" should {

    "return response details for the valid input" in {
      val response                = matchSuccessResponse.registerWithIDResponse
      val details: ResponseDetail = response.getResponseDetail
      details shouldBe response.responseDetail.value
    }
    "throw and exception when input is None" in {
      val response = matchSuccessResponse.registerWithIDResponse.copy(responseDetail = None)
      intercept[Exception](response.getResponseDetail)
    }
  }
}
