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

package unit.viewModels

import base.UnitSpec
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.eoricommoncomponent.frontend.viewModels.YouCantUseServiceViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.views.ServiceName.longName
import util.ControllerSpec

class YouCantUseServiceViewModelSpec extends UnitSpec with ControllerSpec {
  implicit val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  "YouCantUseServiceViewModel firstParagraph" should {
    "return the correct first paragraph for the 'Agent' user type" in {
      val name   = Some(AffinityGroup.Agent)
      val result = YouCantUseServiceViewModel.firstParagraph(name)(messages, fakeRequest)
      result shouldBe messages("cds.you-cant-use-service-agent.para1")
    }

    Seq(None, Some(AffinityGroup.Individual), Some(AffinityGroup.Organisation)).foreach { affinityGroup =>
      s"return the correct first paragraph for $affinityGroup user type" in {
        val result = YouCantUseServiceViewModel.firstParagraph(affinityGroup)(messages, fakeRequest)
        result shouldBe messages("cds.you-cant-use-service-standard-organisation.para1", longName)
      }
    }

  }
  "YouCantUseServiceViewModel.secondParagraph" should {
    "return the correct second paragraph for the 'Agent' user type" in {
      val name   = Some(AffinityGroup.Agent)
      val result = YouCantUseServiceViewModel.secondParagraph(name)(messages, fakeRequest)
      result shouldBe messages("cds.you-cant-use-service-agent.para2", longName)
    }

    Seq(None, Some(AffinityGroup.Individual), Some(AffinityGroup.Organisation)).foreach { affinityGroup =>
      s"return the correct second paragraph for $affinityGroup user type" in {
        val result = YouCantUseServiceViewModel.secondParagraph(affinityGroup)(messages, fakeRequest)
        result shouldBe messages("cds.you-cant-use-service-standard-organisation.para2")
      }
    }

  }

}
