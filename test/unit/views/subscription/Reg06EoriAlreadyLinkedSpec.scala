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

package unit.views.subscription

import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.FormUtils.dateTimeFormat
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.reg06_eori_already_linked
import util.ViewSpec

class Reg06EoriAlreadyLinkedSpec extends ViewSpec {

  private val name                     = "John Doe"
  private val processedDate            = DateTime.now()
  private val expectedPageTitle        = "The application for"
  private val pageHeadingExpectedText  = "The application for"
  private val pageHeadingExpectedText1 = s"$name has been unsuccessful"
  private val processDateExpectedText  = s"Application received by HMRC on ${dateTimeFormat.print(processedDate)}"

  private val view = app.injector.instanceOf[reg06_eori_already_linked]

  "GYE EORI Already Linked outcome page" should {
    "have the correct page title" in {
      doc.title() must startWith(expectedPageTitle)
    }

    "have the right heading" in {
      doc.getElementById("page-heading").text() mustBe pageHeadingExpectedText
    }

    "have the right second heading" in {
      doc.getElementById("page-heading2").text() mustBe pageHeadingExpectedText1
    }

    "have the right processed date" in {
      doc.getElementById("processed-date").text() mustBe processDateExpectedText
    }

    "have the right vat registered text" in {
      doc.getElementById("use-cds-heading").text() mustBe "To use CDS"
      doc
        .getElementById("use-cds-para")
        .text() mustBe s"You need to sign in with the Government Gateway $name used to get access to CDS."
    }

    "have the feedback link" in {
      doc
        .getElementById("what-you-think")
        .text() must include("What did you think of this service?")
      doc.getElementById("feedback_link").attributes().get("href").mustBe("/feedback/CDS")
    }
  }

  implicit val request = withFakeCSRF(FakeRequest())

  lazy val doc: Document = Jsoup.parse(contentAsString(view(name, processedDate)))
}
