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

package unit.forms.subscription

import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsString, JsSuccess, Json}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm.*

class EoriPrefixFormSpec extends AnyWordSpec with Matchers with ScalaCheckPropertyChecks {

  given Arbitrary[EoriRegion] =
    Arbitrary(Gen.oneOf(EoriRegion.values.toSeq))

  val invalidRegionGen: Gen[String] =
    Gen.alphaStr.suchThat(s => s.nonEmpty && !EoriRegion.values.map(_.toString).contains(s))

  "EoriRegion JSON Format" should {

    "serialise / deserialise successfully for all enum values" in {
      forAll { (region: EoriRegion) =>
        val json = Json.toJson(region)
        Json.fromJson[EoriRegion](json) shouldBe JsSuccess(region)
      }
    }

    "fail for any invalid string" in {
      forAll(invalidRegionGen) { invalid =>
        Json.fromJson[EoriRegion](JsString(invalid)).isError shouldBe true
      }
    }
  }

  "EoriRegion Formatter" should {

    val formatter = eoriRegionFormatter
    val key       = "region"

    "bind and unbind for all enum values" in {
      forAll { (region: EoriRegion) =>
        val bound = formatter.bind(key, Map(key -> region.toString))
        bound shouldBe Right(region)

        val unbound = formatter.unbind(key, region)
        unbound shouldBe Map(key -> region.toString)
      }
    }

    "fail binding for any invalid string" in {
      forAll(invalidRegionGen) { invalid =>
        formatter.bind(key, Map(key -> invalid)).isLeft shouldBe true
      }
    }
  }
}
