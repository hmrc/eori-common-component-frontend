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

package unit.forms.models.email

import base.UnitSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.{Domain, EmailAddress}

class EmailAddressSpec extends UnitSpec {

  "email address" should {
    "throw exception when email address is invalid" in {
      intercept[IllegalArgumentException] {
        EmailAddress("not an email address")
      }
        .getMessage shouldBe "'not an email address' is not a valid email address"
    }
  }

  "domain" should {
    "throw exception when domain is invalid" in {
      intercept[IllegalArgumentException] {
        Domain("some domain")
      }
        .getMessage shouldBe "'some domain' is not a valid email domain"
    }
  }
}
