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

package unit.views.subscription

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.subscription_outcome_pending
import util.ViewSpec

class SubscriptionOutcomePendingSpec extends ViewSpec {

  implicit val request = withFakeCSRF(fakeAtarSubscribeRequest)

  private val view = instanceOf[subscription_outcome_pending]

  val orgName       = "Test Organisation Name"
  val eoriNumber    = "EORI123"
  val processedDate = "01 Feb 2020"

  "'Subscription Pending' Page" should {

    "display correct heading" in {
      doc().body.getElementsByTag("h1").text() must startWith(s"We need to make more checks on your application")
    }

    "have the correct 'what happens next' text" in {
      doc().body
        .getElementById("what-happens-next")
        .text mustBe "If the checks are successful you need to apply again We will not email you. You can check if you can apply by following these steps: Return to the page on GOV.UK where you started your application. Start the application and sign in using Government Gateway. If we’ve completed the checks you’ll be able to continue. If we’ve not completed the checks you’ll see this screen again. If the checks on your application are unsuccessful We will email you the reason."
    }

    "have a feedback 'continue' button" in {
      val link = doc().body.getElementById("feedback-continue")
      link.text mustBe "More about Advance Tariff Rulings"
      link.attr("href") mustBe "/test-atar/feedback?status=Processing"
    }

    "have a no feedback 'continue' button when config missing" in {
      val link = doc(atarService.copy(feedbackUrl = None)).body.getElementById("feedback-continue")
      link mustBe null
    }
  }

  def doc(service: Service = atarService): Document =
    Jsoup.parse(contentAsString(view(eoriNumber, processedDate, orgName, service)))

}
