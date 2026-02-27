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

package unit.domain

import base.UnitSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{
  EuEoriRegisteredAddressModel,
  EuEoriRegisteredAddressViewModel
}

class EuEoriRegisteredAddressSpec extends UnitSpec {

  "EuEoriRegisteredAddressModel" should {

    val address =
      EuEoriRegisteredAddressModel(" line 1 ", " line 3 ", Some(" HJ2 3HJ "), "FR")
    "trim address" in {
      address.lineOne shouldBe "line 1"
      address.lineThree shouldBe "line 3"
      address.postcode shouldBe Some("HJ2 3HJ")
      address.country shouldBe "FR"
    }

    "convert ContactAddressViewModel" in {
      val expectedViewModel =
        EuEoriRegisteredAddressViewModel("line 1", "line 3", Some("HJ2 3HJ"), "FR")
      address.toEuEoriRegisteredAddressViewModel shouldBe expectedViewModel
    }
  }
}
