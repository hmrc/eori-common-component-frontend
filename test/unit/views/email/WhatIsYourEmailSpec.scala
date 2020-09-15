/*
 * Copyright 2020 HM Revenue & Customs
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

package unit.views.email

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.data.Form
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.{EmailForm, EmailViewModel}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.what_is_your_email
import util.ViewSpec

class WhatIsYourEmailSpec extends ViewSpec {
  val form: Form[EmailViewModel] = EmailForm.emailForm
  val formWithError: Form[EmailViewModel] = EmailForm.emailForm.bind(Map("email" -> "invalid"))
  val previousPageUrl = "/"
  implicit val request = withFakeCSRF(FakeRequest())

  val view = app.injector.instanceOf[what_is_your_email]

  "What Is Your Email Address page for CDS access" should {
    "display correct title" in {
      MigrateDoc.title() must startWith("What is your email address?")
    }
    "have the correct h1 text" in {
      MigrateDoc.body().getElementsByClass("heading-large").text() mustBe "What is your email address?"
    }
    "have the correct text on the list header" in {
      MigrateDoc.body().getElementById("list-Header").text() mustBe "We'll use this to send you:"
    }
    "have the correct text for the email input description" in {
      MigrateDoc.body().getElementById("list-content").text() mustBe
        "the result of your application to get access to CDS updates on changes to CDS declarations and services financial notifications, including new statements and direct debit advance notices exports notifications"
    }
    "have an input of type 'text'" in {
      MigrateDoc.body().getElementById("email").attr("type") mustBe "text"
    }
  }
  "What Is Your Email Address page with errors" should {
    "display a field level error message" in {
      docWithErrors
        .body()
        .getElementById("email-outer")
        .getElementsByClass("error-message")
        .text() mustBe "Enter a valid email address"
    }
  }

  "What Is Your Email Address page for GYE" should {
    "have the correct text for the email input description" in {
      GYEDoc.body().getElementById("list-content").text() mustBe
        "the result of your EORI application updates on changes to CDS declarations and services financial notifications, including new statements and direct debit advance notices exports notifications"
    }
  }

  lazy val MigrateDoc: Document = {
    val result = view(form, Journey.Subscribe)
    Jsoup.parse(contentAsString(result))
  }

  lazy val GYEDoc: Document = {
    val result = view(form, Journey.Register)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docWithErrors: Document = {
    val result = view(formWithError, Journey.Subscribe)
    Jsoup.parse(contentAsString(result))
  }
}
