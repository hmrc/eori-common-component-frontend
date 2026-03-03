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

import base.UnitSpec
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.data.{Form, FormError}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EuEoriRegisteredAddressModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.EuEoriRegisteredAddressForm

class EuEoriRegisteredAddressFormSpec extends UnitSpec with ScalaCheckPropertyChecks {

  private val form: Form[EuEoriRegisteredAddressModel] =
    EuEoriRegisteredAddressForm.euEoriRegisteredAddressCreateForm()

  // ---------------------------------------------------------------------------
  // Base valid data + helpers
  // ---------------------------------------------------------------------------

  private val validDefaults: Map[String, String] = Map(
    "line-1"      -> "33 Nine Elms Ln",
    "line-3"      -> "Battersea",
    "postcode"    -> "SW11 7US",
    "countryCode" -> "FR"
  )

  private def bind(overrides: (String, String)*): Form[EuEoriRegisteredAddressModel] =
    form.bind(validDefaults ++ overrides.toMap)

  private def error(field: String, key: String) =
    Seq(FormError(field, key))

  // ---------------------------------------------------------------------------
  // Generators
  // ---------------------------------------------------------------------------

  private val validCharGen: Gen[Char] =
    Gen.oneOf("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789' .&-")

  private val invalidCharGen: Gen[Char] =
    Gen.oneOf("!@#$%^*()=+[]{}|;:\"<>?/`~")

  private def validLineGen(max: Int): Gen[String] =
    for {
      length <- Gen.choose(1, max)
      chars  <- Gen.listOfN(length, validCharGen)
    } yield chars.mkString

  private def invalidLineGen(max: Int): Gen[String] =
    for {
      length <- Gen.choose(1, max)
      chars  <- Gen.listOfN(length, invalidCharGen)
    } yield chars.mkString

  // ---------------------------------------------------------------------------
  // Central reusable validation helpers
  // ---------------------------------------------------------------------------

  private def shouldAcceptValidInput(
    field: String,
    max: Int
  ): Unit =
    forAll(validLineGen(max)) { value =>
      bind(field -> value).errors shouldBe empty
    }

  private def shouldRejectInvalidChars(
    field: String,
    max: Int,
    expectedError: String
  ): Unit =
    forAll(invalidLineGen(max)) { value =>
      bind(field -> value).errors shouldBe
        error(field, expectedError)
    }

  private def shouldRejectTooLong(
    field: String,
    max: Int,
    expectedError: String
  ): Unit = {
    val tooLong = "a" * (max + 1)
    bind(field -> tooLong).errors shouldBe
      error(field, expectedError)
  }

  // ---------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------

  "Eu Eori Registered Address Form" should {

    "accept a valid address" in {
      bind().errors shouldBe empty
    }

    "validate line-1" should {

      val max    = 70
      val prefix = "eu.eori.registered.address.line-1"

      "accept valid input" in {
        shouldAcceptValidInput("line-1", max)
      }

      "reject invalid characters" in {
        shouldRejectInvalidChars(
          "line-1",
          max,
          s"$prefix.error.invalid-chars"
        )
      }

      "reject too long input" in {
        shouldRejectTooLong(
          "line-1",
          max,
          s"$prefix.error.too-long"
        )
      }

      "reject empty value" in {
        bind("line-1" -> "").errors shouldBe
          error("line-1", s"$prefix.error.empty")
      }
    }

    "validate line-3" should {

      val max    = 35
      val prefix = "eu.eori.registered.address.line-3"

      "accept valid input" in {
        shouldAcceptValidInput("line-3", max)
      }

      "reject invalid characters" in {
        shouldRejectInvalidChars(
          "line-3",
          max,
          s"$prefix.error.invalid-chars"
        )
      }

      "reject empty value" in {
        bind("line-3" -> "").errors shouldBe
          error("line-3", s"$prefix.error.empty")
      }
    }

    "validate postcode" should {

      val max    = 35
      val prefix = "eu.eori.registered.address.postcode"

      "accept valid input" in {
        shouldAcceptValidInput("postcode", max)
      }

      "reject invalid characters" in {
        shouldRejectInvalidChars(
          "postcode",
          max,
          s"$prefix.error.invalid-chars"
        )
      }

      "reject too long input" in {
        shouldRejectTooLong(
          "postcode",
          max,
          s"$prefix.error.too-long"
        )
      }
    }

    "validate country code" should {

      val prefix = "eu.eori.registered.address.country"

      "reject empty value" in {
        bind("countryCode" -> "").errors shouldBe
          error("countryCode", s"$prefix.error.empty")
      }

      "reject longer than 2 characters" in {
        bind("countryCode" -> "FRR").errors shouldBe
          error("countryCode", s"$prefix.error.empty")
      }
    }
  }
}
