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

package unit.models

import org.scalatest.{EitherValues, MustMatchers, OptionValues, WordSpec}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service

class ServiceSpec extends WordSpec with MustMatchers with EitherValues with OptionValues {

  "Service" must {

    "unbind the values correctly" in {

      val cds: Service = Service("cds", "HMRC-CUS-ORG", "", None, "", "", "", None)
      cds.enrolmentKey mustEqual "HMRC-CUS-ORG"
    }

    "bind Service name properly from query" in {

      val result =
        Service.binder.bind("key", "atar").right.value

      result.code mustEqual "atar"
    }

    "return invalid value when incorrect service name is sent from query" in {

      val result =
        Service.binder.bind("key", "invalid").left.value

      result mustEqual "invalid value"
    }

  }
}
