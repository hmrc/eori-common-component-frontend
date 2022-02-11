/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.use_this_eori
import util.ViewSpec

class UseThisEoriSpec extends ViewSpec {
  val previousPageUrl                                   = "/"
  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(FakeRequest())

  val view: use_this_eori = instanceOf[use_this_eori]

  "Use this Eori page for CDS access" should {
    "display correct title" in {
      MigrateDoc.title() must startWith("Your GB Economic Operator Registration and Identification (EORI) number")
    }
    "have the correct h1 text" in {
      MigrateDoc.body().getElementsByClass(
        "govuk-heading-l"
      ).text() mustBe "Your GB Economic Operator Registration and Identification (EORI) number"
    }
    "have the correct eori number in the body" in {
      MigrateDoc.body().getElementsByClass("eori-number").text() mustBe "GB123456789123"
    }

  }

  val MigrateDoc: Document = {
    val result = view("GB123456789123", atarService)
    Jsoup.parse(contentAsString(result))
  }

  val docWithErrors: Document = {
    val result = view("GB123456789123", atarService)
    Jsoup.parse(contentAsString(result))
  }

}
