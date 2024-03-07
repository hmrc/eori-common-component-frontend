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
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.viewModels.Sub01OutcomeViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.views.ServiceName.{longName, shortName}
import util.ControllerSpec

class Sub01OutcomeViewModelSpec extends UnitSpec with ControllerSpec {
  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  "Sub01outcomeView headingForRejected" should {
    "return the correct heading with name for a rejected outcome" in {
      val name   = Some("John Doe")
      val result = Sub01OutcomeViewModel.headingForRejected(name)(messages, fakeRequest)
      result shouldBe messages("cds.sub01.outcome.rejected.subscribe.heading", longName, "John Doe")
    }
    "return the correct heading without name for a rejected outcome" in {

      val result = Sub01OutcomeViewModel.headingForRejected(None)(messages, fakeRequest)
      result shouldBe messages("cds.sub01.outcome.rejected.subscribe.heading-noname", longName)
    }

  }
  "Sub01outcomeView headingForProcessing" should {
    "return the correct heading with name for a processing outcome" in {
      val name   = Some("John Doe")
      val result = Sub01OutcomeViewModel.headingForProcessing(name)(messages, fakeRequest)
      result shouldBe messages("cds.sub01.outcome.processing.heading", shortName, "John Doe")
    }
    "return the correct heading without name for a processing outcome" in {

      val result = Sub01OutcomeViewModel.headingForProcessing(None)(messages, fakeRequest)
      result shouldBe messages("cds.sub01.outcome.processing.heading-noname", shortName)
    }

  }

}
