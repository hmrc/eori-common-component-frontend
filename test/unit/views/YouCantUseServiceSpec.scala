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

package unit.views

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.auth.core.AffinityGroup.{Agent, Organisation}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.you_cant_use_service
import util.ViewSpec

class YouCantUseServiceSpec extends ViewSpec {

  private implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(fakeAtarSubscribeRequest)
  private val youCantUseServiceView                             = instanceOf[you_cant_use_service]

  "You cannot use this service page for users of type standard org" should {

    "display correct title" in {
      standardOrgDoc.title must startWith("You cannot use this service")
    }

    "display correct heading" in {
      standardOrgDoc.body.getElementsByTag("h1").text mustBe "You cannot use this service"
    }

    "display para-1" in {
      standardOrgDoc.body
        .getElementById("para-1")
        .text mustBe "You signed in to Government Gateway as a standard user. To apply for access to Advance Tariff Rulings you must be an administrator user."
    }

    "display para-2" in {
      standardOrgDoc.body.getElementById(
        "para-2"
      ).text mustBe "Please log in as an administrator or contact the person who set up your Government Gateway."
    }

    "have a Sign out button with the correct href" in {
      standardOrgDoc.body().getElementsByClass("govuk-button").attr("href") must endWith("/subscribe/logout")
    }
  }

  "You cannot use this service page for users of type agent" should {

    "display correct title" in {
      agentDoc.title must startWith("You cannot use this service")
    }

    "display correct heading" in {
      agentDoc.body.getElementsByTag("h1").text mustBe "You cannot use this service"
    }

    "display para-1" in {
      agentDoc.body
        .getElementById("para-1")
        .text mustBe "You signed in to Government Gateway with an agent services account."
    }

    "display para-2" in {
      agentDoc.body
        .getElementById("para-2")
        .text mustBe "You need to sign in with the Government Gateway for the organisation or individual that is applying for access to Advance Tariff Rulings."
    }

    "have a Sign out button with the correct href" in {
      agentDoc.body().getElementsByClass("govuk-button").attr("href") must endWith("/subscribe/logout")
    }
  }

  private lazy val standardOrgDoc: Document =
    Jsoup.parse(contentAsString(youCantUseServiceView(Some(Organisation), atarService)))

  private lazy val agentDoc: Document = Jsoup.parse(contentAsString(youCantUseServiceView(Some(Agent), atarService)))

}
