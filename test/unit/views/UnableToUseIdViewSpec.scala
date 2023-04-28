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

package unit.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.Request
import play.api.test.Helpers.contentAsString
import util.ViewSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.unable_to_use_id

class UnableToUseIdViewSpec extends ViewSpec {

  private val view                   = instanceOf[unable_to_use_id]
  implicit val request: Request[Any] = withFakeCSRF(fakeAtarSubscribeRequest)

  private val doc: Document = Jsoup.parse(contentAsString(view(atarService, "GB123456789123")))

  "Unable to use id page" should {

    "display correct title" in {

      doc.title() must startWith("You cannot use this service with this Government Gateway user ID")
    }

    "display correct header" in {

      doc.body().getElementsByTag("h1").text() mustBe "You cannot use this service with this Government Gateway user ID"
    }

    "display eori paragraph" in {

      val body = doc.body()

      body.getElementById(
        "eori-number-text"
      ).text() mustBe "The user ID needs to be the one that you or your organisation subscribed to this service with."
      body.getElementById(
        "para1"
      ).text() mustBe "Subscribing to a service is what you do the first time you use it. You or someone in your organisation subscribed with a user ID that’s different to this one."
    }

    "display additional paragraph" in {

      doc.body().getElementById("para2").text() mustBe "You need to sign out then sign in with that user ID."
    }

    "display signout button" in {

      val signoutButton = doc.body().getElementsByClass("govuk-button")

      signoutButton.text() mustBe "Sign out"
      signoutButton.attr("href") mustBe "/customs-enrolment-services/atar/subscribe/logout"
    }
  }
}
