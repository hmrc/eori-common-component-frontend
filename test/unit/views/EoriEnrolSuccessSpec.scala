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
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.eori_enrol_success
import util.ViewSpec

class EoriEnrolSuccessSpec extends ViewSpec {

  implicit val request = withFakeCSRF(fakeAtarSubscribeRequest)

  private val service              = atarService
  private val serviceNoCallBackUrl = atarService.copy(callBack = None)
  private val eori                 = "GB234532132435"

  private val view = instanceOf[eori_enrol_success]

  "EORI Enrol Success Page" should {

    "display correct title" in {
      doc(service).title must startWith("Application complete")
    }

    "display correct heading" in {
      doc(service).getElementsByTag("h1").text() must startWith("Application complete")
    }

    "display eori" in {
      doc(service).body.getElementById("eori-number").text mustBe eori
    }

    "display continue button when callBack URL exists" in {
      doc(service).body.getElementsByClass("govuk-button").first().text must startWith("Continue")
    }

    "not display continue button when callBack URL doesn't exists" in {
      doc(serviceNoCallBackUrl).body.getElementsByClass("govuk-button").size() must be(0)
    }
  }

  private def doc(service: Service): Document = Jsoup.parse(contentAsString(view(eori, service)))

}
