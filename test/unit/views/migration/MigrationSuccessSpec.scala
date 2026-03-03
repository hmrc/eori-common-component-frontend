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

package unit.views.migration

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm.EoriRegion
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.migration_success
import util.ViewSpec

class MigrationSuccessSpec extends ViewSpec {

  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(fakeAtarSubscribeRequest)

  private val view = instanceOf[migration_success]

  "'Migration Success' Page" should {

    "have a feedback 'continue' button" in {
      val link = docGB().body.getElementById("feedback-continue")
      link.text mustBe "More about Advance Tariff Rulings"
      link.attr("href") mustBe "/test-atar/feedback?status=Processing"
    }

    "have a no feedback 'continue' button when config missing" in {
      val link = docGB(atarService.copy(feedbackUrl = None)).body.getElementById("feedback-continue")
      link mustBe null
    }

    "when cds and EU EORI turned on, " in {
      val contentFirstPara =
        docCdsEU(cdsService.copy(feedbackUrl = None)).body.getElementById("eu-process-application-text")
      val contentSecondPara =
        docCdsEU(cdsService.copy(feedbackUrl = None)).body.getElementById("eu-will-send-email-text")
      contentFirstPara.text mustBe "We will process your application. This can take up to 2 hours."
      contentSecondPara.text mustBe "We will email you when the subscription is ready to use. You can then start using the Customs Declaration Service."

    }
  }

  def docGB(service: Service = atarService): Document =
    Jsoup.parse(contentAsString(view("", "", service, false, Some(EoriRegion.GB))))

  def docCdsEU(service: Service = cdsService): Document =
    Jsoup.parse(contentAsString(view("", "", service, true, Some(EoriRegion.EU))))

}
