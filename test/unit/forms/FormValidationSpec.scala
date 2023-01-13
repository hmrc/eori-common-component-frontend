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

package unit.forms

import base.UnitSpec
import play.api.data.{Form, FormError}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{IdMatchModel, NameDobMatchModel}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm

import java.time.{LocalDate, Year}
import java.time.format.DateTimeFormatter
import scala.util.Random

class FormValidationSpec extends UnitSpec {

  def randomNino: String = new Generator(new Random()).nextNino.nino

  val nameDobForm: Form[NameDobMatchModel] = MatchingForms.enterNameDobForm

  val dateOfEstablishmentForm: Form[LocalDate] = SubscriptionForm.subscriptionDateOfEstablishmentForm

  val ninoForm: Form[IdMatchModel] = MatchingForms.subscriptionNinoForm

  val formData = Map(
    "first-name"          -> "ff",
    "middle-name"         -> "",
    "last-name"           -> "ddd",
    "date-of-birth.day"   -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year"  -> "2019"
  )

  val formDataNino = Map(
    "first-name"          -> "ff",
    "last-name"           -> "ddd",
    "nino"                -> randomNino,
    "date-of-birth.day"   -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year"  -> "2019"
  )

  val formDataRow = Map(
    "given-name"          -> "ff",
    "middle-name"         -> "",
    "family-name"         -> "ddd",
    "date-of-birth.day"   -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year"  -> "2019"
  )

  val formDataVAT = Map(
    "postcode"                 -> "AB12CD",
    "vat-number"               -> "123456789",
    "vat-effective-date.day"   -> "1",
    "vat-effective-date.month" -> "1",
    "vat-effective-date.year"  -> "2019"
  )

  val formDataDoE = Map(
    "date-of-establishment.day"   -> "1",
    "date-of-establishment.month" -> "1",
    "date-of-establishment.year"  -> "2019"
  )

  "NameDobForm" should {

    "only accept valid form" in {
      val data = formData
      val res  = nameDobForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept first-name with ' apostrophe" in {
      val data = formData.updated("first-name", "apos'trophe")
      val res  = nameDobForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept last-name with ' apostrophe" in {
      val data = formData.updated("last-name", "apos'trophe")
      val res  = nameDobForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "accept middle-name with ' apostrophe" in {
      val data = formData.updated("middle-name", "apos'trophe")
      val res  = nameDobForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "fail when a First Name is invalid" in {
      val data = formData.updated("first-name", "")
      val res  = nameDobForm.bind(data)
      assert(res.errors.nonEmpty)
    }
    "fail when a Last Name is invalid" in {
      val data = formData.updated("last-name", "")
      val res  = nameDobForm.bind(data)
      assert(res.errors.nonEmpty)
    }
    "fail when a date of birth is missing" in {
      val data = formData.updated("date-of-birth.day", "").updated("date-of-birth.month", "")
      val res  = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth", Seq("dob.error.empty-date")))
    }
    "fail when a date of birth in future" in {
      val todayPlusOneDay = LocalDate.now().plusDays(1)
      val data =
        formData.updated("date-of-birth.day", DateTimeFormatter.ofPattern("dd").format(todayPlusOneDay)).updated(
          "date-of-birth.month",
          DateTimeFormatter.ofPattern("MM").format(todayPlusOneDay)
        ).updated("date-of-birth.year", DateTimeFormatter.ofPattern("yyyy").format(todayPlusOneDay))
      val res = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth", Seq("dob.error.future-date")))
    }
    "fail when a date of birth year invalid" in {
      val data = formData.updated("date-of-birth.year", Year.now.plusYears(1).getValue.toString)
      val res  = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth.year", Seq("date.year.error")))
    }
    "fail when a date of birth too early" in {
      val data = formData.updated("date-of-birth.year", "1800")
      val res  = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth.year", Seq("date.year.error")))
    }
  }

  "Date of establishment form" should {
    "only accept valid form" in {
      val data = formDataDoE
      val res  = dateOfEstablishmentForm.bind(data)
      assert(res.errors.isEmpty)
    }
    "fail when date of establishment is missing" in {
      val data = formDataDoE.updated("date-of-establishment.day", "").updated("date-of-establishment.month", "")
      val res  = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment", Seq("doe.error.empty-date")))
    }
    "fail when date of establishment in future" in {
      val todayPlusOneDay = LocalDate.now().plusDays(1)
      val data = formDataDoE.updated(
        "date-of-establishment.day",
        DateTimeFormatter.ofPattern("dd").format(todayPlusOneDay)
      ).updated("date-of-establishment.month", DateTimeFormatter.ofPattern("MM").format(todayPlusOneDay)).updated(
        "date-of-establishment.year",
        DateTimeFormatter.ofPattern("yyyy").format(todayPlusOneDay)
      )
      val res = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment", Seq("doe.error.future-date")))
    }
    "fail when date of establishment year invalid" in {
      val data = formDataDoE.updated("date-of-establishment.year", Year.now.plusYears(1).getValue.toString)
      val res  = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment.year", Seq("date.year.error")))
    }
    "fail when date of establishment too early" in {
      val data = formDataDoE.updated("date-of-establishment.year", "999")
      val res  = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment.year", Seq("date.year.error")))
    }
  }

  "Nino form" should {
    "only accept valid Nino" in {
      val res = ninoForm.bind(Map("nino" -> "ZH981921A"))
      assert(res.errors.isEmpty)
    }
    "fail when nino is empty" in {
      val res = ninoForm.bind(Map("nino" -> ""))
      res.errors shouldBe Seq(FormError("nino", Seq("cds.subscription.nino.error.empty")))
    }
    "fail when nino is wrong length" in {
      val res = ninoForm.bind(Map("nino" -> "12345678"))
      res.errors shouldBe Seq(FormError("nino", Seq("cds.subscription.nino.error.wrong-length")))
    }
    "fail when nino is invalid format" in {
      val res = ninoForm.bind(Map("nino" -> "--9819219"))
      res.errors shouldBe Seq(FormError("nino", Seq("cds.matching.nino.invalidFormat")))
    }
    "fail when nino is invalid nino" in {
      val res = ninoForm.bind(Map("nino" -> "BG981921A"))
      res.errors shouldBe Seq(FormError("nino", Seq("cds.matching.nino.invalidNino")))
    }
  }
}
