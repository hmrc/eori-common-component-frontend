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

package unit.views.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.data.Form
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{CorporateBody, LLP, UnincorporatedBody}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.date_of_establishment
import util.ViewSpec

import java.time

class DateOfEstablishmentSpec extends ViewSpec {
  val form: Form[time.LocalDate]                        = SubscriptionForm.subscriptionDateOfEstablishmentForm
  val isInReviewMode                                    = false
  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(FakeRequest())

  private val view = instanceOf[date_of_establishment]

  "On a UK journey the 'When was the organisation established?' page" should {
    "display correct title" in {
      doc.title() must startWith("When was the company established?")
    }
    "have the correct h1 text" in {
      doc.body.getElementsByTag("h1").text() mustBe "When was the company established?"
    }
    "have the correct class on the h1" in {
      doc.body.getElementsByTag("h1").hasClass("govuk-heading-l") mustBe true
    }
    "have the correct text in the hint" in {
      doc.body.getElementById("date-of-establishment-hint").text() mustBe "For example, 31 03 1980."
    }

  }

  "On a RoW journey the 'When was the organisation established?' page" should {
    "display correct title" in {
      docRestOfWorld.title must startWith("When was the organisation established?")
    }
    "have the correct h1 text" in {
      docRestOfWorld.body.getElementsByTag("h1").text() mustBe "When was the organisation established?"
    }
    "have the correct class on the h1" in {
      docRestOfWorld.body.getElementsByTag("h1").hasClass("govuk-heading-l") mustBe true
    }
    "have the correct text in the description" in {
      docRestOfWorld.body
        .getElementById("date-of-establishment-hint")
        .text() mustBe "For example, 31 03 1980."
    }
  }

  "On an LLP org type journey Date Established page" should {
    "display correct title" in {
      docLlp.title must startWith("When was the partnership established?")
    }
    "have the correct h1 text" in {
      docLlp.body.getElementsByTag("h1").text() mustBe "When was the partnership established?"
    }
    "have the correct class on the h1" in {
      docLlp.body.getElementsByTag("h1").hasClass("govuk-heading-l") mustBe true
    }
    "not have intro paragraph" in {
      Option(docLlp.body.getElementById("date-of-establishment-info")) mustBe empty
    }
  }

  "On an UnincorporatedBody org type journey Date Established page" should {
    "display correct title" in {
      docCharity.title() must startWith("When was the organisation established?")
    }
    "have the correct h1 text" in {
      docCharity.body.getElementsByTag("h1").text() mustBe "When was the organisation established?"
    }
    "have the correct class on the h1" in {
      docCharity.body.getElementsByTag("h1").hasClass("govuk-heading-l") mustBe true
    }
    "have the correct text in the hint" in {
      docCharity.body.getElementById("date-of-establishment-hint").text() mustBe "For example, 31 03 1980."
    }

  }

  lazy val doc: Document = {
    val result =
      view(form, isInReviewMode, orgType = CorporateBody, isRestOfWorldJourney = false, atarService)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docRestOfWorld: Document = {
    val result =
      view(form, isInReviewMode, orgType = CorporateBody, isRestOfWorldJourney = true, atarService)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docLlp: Document = {
    val result =
      view(form, isInReviewMode, orgType = LLP, isRestOfWorldJourney = false, atarService)
    Jsoup.parse(contentAsString(result))
  }

  lazy val docCharity: Document = {
    val result =
      view(form, isInReviewMode, orgType = UnincorporatedBody, isRestOfWorldJourney = false, atarService)
    Jsoup.parse(contentAsString(result))
  }

}
