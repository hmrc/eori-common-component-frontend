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

package unit.views.subscription

import play.api.data.Form
import play.api.mvc.Request
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.first_2_letters_eori_number
import util.ViewSpec
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm.EoriRegion

import scala.jdk.CollectionConverters._

class FirstTwoLettersEoriNumberViewSpec extends ViewSpec {

  private val view: first_2_letters_eori_number = instanceOf[first_2_letters_eori_number]
  implicit val request: Request[Any]            = withFakeCSRF(fakeAtarSubscribeRequest)

  private val form: Form[EoriPrefixForm.EoriRegion] = EoriPrefixForm.eoriPrefixForm

  val result        = view(form, Some(EoriRegion.GB), false, cdsService, subscribeJourneyLong)
  val doc: Document = Jsoup.parse(contentAsString(result))

  "What are the first 2 letters of you EORI number page" should {

    "Display the correct title" in {
      doc.title must startWith("What are the first two letters of your EORI number?")
    }

    "Have the correct H1 text" in {
      doc.body.getElementsByTag("h1").text() mustBe "What are the first two letters of your EORI number?"
    }

    "Hide the legend of the radio buttons" in {
      doc.body().getElementsByTag("legend").hasClass("govuk-visually-hidden") mustBe true
    }

    "Show the hint" in {
      doc.body().getElementById("region-hint").text() mustBe "An EORI number is 2 letters followed by up to 15 characters, like GB123456123456, ESX1234567X or NL12345678."
    }

    "Show details link" in {
      doc.body().getElementsByClass(
        "govuk-details__summary-text"
      ).text() mustBe "I do not have an EORI number starting with GB"

      doc.select(".govuk-details__text p").eachText().asScala.toSeq mustBe Seq(
        "You need an EORI number to use this online service.",
        "If your organisation is based in UK, you should apply for both a subscription and an EORI number starting with GB.",
        "If your organisation is based in a country in the EU, you should apply for an EORI number in that country."
      )
    }
  }
}
