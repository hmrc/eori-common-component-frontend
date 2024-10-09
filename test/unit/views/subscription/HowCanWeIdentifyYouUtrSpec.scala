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
import play.api.data.Form
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.IdMatchModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.how_can_we_identify_you_utr
import util.ViewSpec

class HowCanWeIdentifyYouUtrSpec extends ViewSpec {
  val form: Form[IdMatchModel]                   = subscriptionUtrForm
  val formWithNothingEntered: Form[IdMatchModel] = subscriptionUtrForm.bind(Map("utr" -> ""))

  val isInReviewMode                                    = false
  val previousPageUrl                                   = "/"
  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(FakeRequest())

  private val view = instanceOf[how_can_we_identify_you_utr]

  "Subscription Enter Your Nino Page" should {

    "display correct heading" in {
      doc.body().getElementsByTag("h1").text() mustBe "Your Self Assessment Unique Taxpayer Reference (UTR)"
    }

    "include the heading in the title" in {
      doc.title() must startWith(doc.body().getElementsByTag("h1").text())
    }

    "have the correct text in the hint" in {

      doc.body()
        .getElementById("utr-hint")
        .text() mustBe "This is 10 numbers, for example 1234567890. It will be on tax returns and other letters about Self Assessment. It may be called ‘reference’, ‘UTR’ or ‘official use’."
    }

    "display an page level error if no nino entered" in {
      docWithNoUtrError
        .body()
        .getElementsByClass("govuk-error-summary__list")
        .text() mustBe messages("cds.matching-error.business-details.utr.isEmpty")
    }

    "display an field level error if no nino entered" in {
      docWithNoUtrError.body().getElementsByClass("govuk-error-message").text() mustBe s"Error: ${messages("cds.matching-error.business-details.utr.isEmpty")}"
    }

  }

  lazy val doc: Document = Jsoup.parse(
    contentAsString(
      view(
        form,
        Map(
          "hintMessage"    -> "subscription-journey.how-confirm-identity.utr.hint",
          "headingMessage" -> "subscription-journey.how-confirm-identity.utr.heading",
          "subHeading"     -> "subscription-journey.how-confirm-identity.utr.subheading",
          "infoMessage"    -> "subscription-journey.navigation.self-utr-message",
          "findUtrText"    -> "subscription.navigation.find-lost-utr"
        ),
        isInReviewMode,
        routes.HowCanWeIdentifyYouUtrController.submit(isInReviewMode, atarService),
        atarService
      )
    )
  )

  lazy val docWithNoUtrError: Document =
    Jsoup.parse(
      contentAsString(
        view(
          formWithNothingEntered,
          Map(
            "hintMessage"    -> "subscription-journey.how-confirm-identity.utr.hint",
            "headingMessage" -> "subscription-journey.how-confirm-identity.utr.heading",
            "subHeading"     -> "subscription-journey.how-confirm-identity.utr.subheading",
            "infoMessage"    -> "subscription-journey.navigation.self-utr-message",
            "findUtrText"    -> "subscription.navigation.find-lost-utr"
          ),
          isInReviewMode,
          routes.HowCanWeIdentifyYouUtrController.submit(isInReviewMode, atarService),
          atarService
        )
      )
    )

}
