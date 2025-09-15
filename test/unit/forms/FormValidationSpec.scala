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

package unit.forms

import base.UnitSpec
import play.api.data.{Form, FormError}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{IdMatchModel, NameDobMatchModel, Nino}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, Year}
import scala.collection.immutable.ArraySeq
import scala.util.Random

class Generator(random: Random = new Random) {
  def this(seed: Int) = this(new scala.util.Random(seed))

  val invalidPrefixes       = List("BG", "GB", "NK", "KN", "TN", "NT", "ZZ")
  val validFirstCharacters  = ('A' to 'Z').filterNot(List('D', 'F', 'I', 'Q', 'U', 'V').contains).map(_.toString)
  val validSecondCharacters = ('A' to 'Z').filterNot(List('D', 'F', 'I', 'O', 'Q', 'U', 'V').contains).map(_.toString)

  val validPrefixes =
    validFirstCharacters.flatMap(a => validSecondCharacters.map(a + _)).filterNot(invalidPrefixes.contains(_))

  val validSuffixes = ('A' to 'D').map(_.toString)

  def nextNino: Nino = {
    val prefix = validPrefixes(random.nextInt(validPrefixes.length))
    val number = random.nextInt(1000000)
    val suffix = validSuffixes(random.nextInt(validSuffixes.length))
    Nino(f"$prefix$number%06d$suffix")
  }

}

class FormValidationSpec extends UnitSpec {

  def randomNino: String = new Generator(new Random()).nextNino.id

  val nameDobForm: Form[NameDobMatchModel] = MatchingForms.enterNameDobForm

  val dateOfEstablishmentForm: Form[LocalDate] = SubscriptionForm.subscriptionDateOfEstablishmentForm

  val ninoForm: Form[IdMatchModel] = MatchingForms.subscriptionNinoForm

  val formData: Map[String, String] = Map(
    "first-name"          -> "ff",
    "middle-name"         -> "",
    "last-name"           -> "ddd",
    "date-of-birth.day"   -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year"  -> "2019"
  )

  val formDataNino: Map[String, String] = Map(
    "first-name"          -> "ff",
    "last-name"           -> "ddd",
    "nino"                -> randomNino,
    "date-of-birth.day"   -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year"  -> "2019"
  )

  val formDataRow: Map[String, String] = Map(
    "given-name"          -> "ff",
    "middle-name"         -> "",
    "family-name"         -> "ddd",
    "date-of-birth.day"   -> "22",
    "date-of-birth.month" -> "10",
    "date-of-birth.year"  -> "2019"
  )

  val formDataVAT: Map[String, String] = Map(
    "postcode"                 -> "AB12CD",
    "vat-number"               -> "123456789",
    "vat-effective-date.day"   -> "1",
    "vat-effective-date.month" -> "1",
    "vat-effective-date.year"  -> "2019"
  )

  val formDataDoE: Map[String, String] = Map(
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
      res.errors shouldBe Seq(
        FormError("date-of-birth.day", List("date-of-birth.day-date-of-birth.month.empty"), List()),
        FormError("date-of-birth.month", List(""), List())
      )
    }
    "fail when a date of birth in future" in {
      val todayPlusOneDay = LocalDate.now().plusDays(1)
      val data =
        formData.updated("date-of-birth.day", DateTimeFormatter.ofPattern("dd").format(todayPlusOneDay)).updated(
          "date-of-birth.month",
          DateTimeFormatter.ofPattern("MM").format(todayPlusOneDay)
        ).updated("date-of-birth.year", DateTimeFormatter.ofPattern("yyyy").format(todayPlusOneDay))
      val res = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth", List("dob.error.future-date"), ArraySeq("1900")))
    }
    "fail when a date of birth year invalid" in {
      val data = formData.updated("date-of-birth.year", Year.now.plusYears(1).getValue.toString)
      val res  = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth.year", List("date.year.error"), List()))
    }
    "fail when a date of birth too early" in {
      val data = formData.updated("date-of-birth.year", "1800")
      val res  = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth", List("dob.error.minMax"), ArraySeq("1900")))
    }
    "fail when the date is invalid" in {
      val updatedData = Map("date-of-birth.day" -> "31", "date-of-birth.month" -> "2", "date-of-birth.year" -> "2019")
      val data        = formData ++ updatedData
      val res         = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth", List("dob.error.invalid-date"), List()))
    }

    "pass when the date has a day of 31" in {
      val res = nameDobForm.bind(formData)
      res.errors shouldBe Nil
    }

    "fail when the date contains a day greater than 31" in {
      val updatedData = Map("date-of-birth.day" -> "32", "date-of-birth.month" -> "1", "date-of-birth.year" -> "2019")
      val data        = formData ++ updatedData
      val res         = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth.day", Seq("date.day.error"), ArraySeq()))
    }

    "pass when the date has a month of 12" in {
      val data = formData.updated("date-of-birth.month", "12")
      val res  = nameDobForm.bind(data)
      res.errors shouldBe Nil
    }

    "fail when the date contains a month greater than 12" in {
      val updatedFormData =
        Map("date-of-birth.day" -> "31", "date-of-birth.month" -> "13", "date-of-birth.year" -> "2019")
      val data = formData ++ updatedFormData

      val res = nameDobForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-birth.month", Seq("date.month.error"), ArraySeq()))
    }

    "fail when the date contains a day greater than 31 and a month greater than 12" in {
      val updatedFormData =
        Map("date-of-birth.day" -> "32", "date-of-birth.month" -> "13", "date-of-birth.year" -> "2019")
      val data = formData ++ updatedFormData
      val res  = nameDobForm.bind(data)
      res.errors shouldBe Seq(
        FormError("date-of-birth.day", Seq("date.day.error"), ArraySeq()),
        FormError("date-of-birth.month", Seq("date.month.error"), ArraySeq())
      )
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
      res.errors shouldBe Seq(
        FormError(
          "date-of-establishment.day",
          List("date-of-establishment.day-date-of-establishment.month.empty"),
          List()
        ),
        FormError("date-of-establishment.month", List(""), List())
      )
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
      res.errors shouldBe Seq(FormError("date-of-establishment", List("doe.error.future-date"), ArraySeq("1000")))
    }
    "fail when date of establishment year invalid" in {
      val data = formDataDoE.updated("date-of-establishment.year", Year.now.plusYears(1).getValue.toString)
      val res  = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment.year", List("date.year.error"), List()))
    }
    "fail when date of establishment too early" in {
      val data = formDataDoE.updated("date-of-establishment.year", "999")
      val res  = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment.year", List("date-invalid-year-too-short"), List()))
    }
    "fail when the date is invalid" in {
      val data = Map(
        "date-of-establishment.day"   -> "31",
        "date-of-establishment.month" -> "2",
        "date-of-establishment.year"  -> "2019"
      )
      val res = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment", List("doe.error.invalid-date"), List()))
    }

    "pass when the date has a day of 31" in {
      val data = Map(
        "date-of-establishment.day"   -> "31",
        "date-of-establishment.month" -> "1",
        "date-of-establishment.year"  -> "2019"
      )
      val res = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Nil
    }

    "fail when the date contains a day greater than 31" in {
      val data = Map(
        "date-of-establishment.day"   -> "32",
        "date-of-establishment.month" -> "1",
        "date-of-establishment.year"  -> "2019"
      )
      val res = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment.day", Seq("date.day.error"), ArraySeq()))
    }

    "pass when the date has a month of 12" in {
      val data = Map(
        "date-of-establishment.day"   -> "31",
        "date-of-establishment.month" -> "12",
        "date-of-establishment.year"  -> "2019"
      )
      val res = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Nil
    }

    "fail when the date contains a month greater than 12" in {
      val data = Map(
        "date-of-establishment.day"   -> "31",
        "date-of-establishment.month" -> "13",
        "date-of-establishment.year"  -> "2019"
      )
      val res = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(FormError("date-of-establishment.month", Seq("date.month.error"), ArraySeq()))
    }

    "fail when the date contains a day greater than 31 and a month greater than 12" in {
      val data = Map(
        "date-of-establishment.day"   -> "32",
        "date-of-establishment.month" -> "13",
        "date-of-establishment.year"  -> "2019"
      )
      val res = dateOfEstablishmentForm.bind(data)
      res.errors shouldBe Seq(
        FormError("date-of-establishment.day", Seq("date.day.error"), ArraySeq()),
        FormError("date-of-establishment.month", Seq("date.month.error"), ArraySeq())
      )
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
