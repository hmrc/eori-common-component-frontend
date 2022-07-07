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

package unit.views.subscription

import java.time.LocalDateTime

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.ApplicationController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.reg06_eori_already_linked
import uk.gov.hmrc.play.language.LanguageUtils
import util.ViewSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models._

class Reg06EoriAlreadyLinkedSpec extends ViewSpec {

  private val name              = "John Doe"
  private val eori              = "GB123456789012"
  private val processedDate     = LocalDateTime.now()
  private val expectedPageTitle = "The details you gave us did not match our records"
  private val expectedPageIntro = "The details you entered do not match our records for EORI number"
  private val languageUtils     = instanceOf[LanguageUtils]
  private val utr               = Some(Utr("UTRXXXXX"))
  private val utrNumber         = "UTRXXXXX"
  private val nino              = Some(Nino("AAXXXXX"))
  private val ninoNumber        = "AAXXXXX"
  private val nameIdOrg         = Some(NameIdOrganisationMatchModel("Name", utr.get.id))

  private val pageHeadingExpectedText =
    "The details you gave us did not match our records"

  private val view = instanceOf[reg06_eori_already_linked]

  "EORI Already Linked outcome page" should {

    "have the correct page title" in {

      docUtr().title() must startWith(expectedPageTitle)
    }

    "have the right heading" in {

      docUtr().getElementById("page-heading").text() mustBe pageHeadingExpectedText
    }

    "have the feedback link" in {

      docUtr()
        .getElementById("what-you-think")
        .text() must include("What did you think of this service?")
      docUtr().getElementById("feedback_link").attributes().get("href") must endWith(
        "/feedback/eori-common-component-subscribe-atar"
      )
    }

    "have a feedback 'continue' button" in {

      val link = docUtr().body.getElementById("feedback-continue")

      link.text mustBe "More about Advance Tariff Rulings"
      link.attr("href") mustBe "/test-atar/feedback?status=Failed"
    }

    "have a no feedback 'continue' button when config missing" in {

      val link = docUtr(service = atarService.copy(feedbackUrl = None)).body.getElementById("feedback-continue")

      link mustBe null
    }
  }

  "EORI already linked page" should {

    "has specific content for individual with UTR" in {

      val page = docUtr(isIndividual = true, hasUtr = true).body()

      val heading     = page.getElementById("why-heading")
      val utrElement  = page.getElementById("individual-utr")
      val infoElement = page.getElementById("additional-info")

      heading.text() mustBe "What you can do now"
      utrElement.text() mustBe s"The Unique Taxpayer Reference, $utrNumber, you entered does not match our records for EORI number $eori."
      infoElement.text() mustBe "If you think our records are incorrect, you can tell us about any changes."

      page.getElementById("individual-nino") mustBe null
      page.getElementById("organisation") mustBe null
      page.getElementById("organisation-utr") mustBe null
      page.getElementById("intro-text") mustBe null
    }

    "has specific content for individual with NINO" in {

      val page = docNino(isIndividual = true, hasUtr = false).body()

      val heading     = page.getElementById("why-heading")
      val ninoElement = page.getElementById("individual-nino")
      val infoElement = page.getElementById("additional-info")

      heading.text() mustBe "What you can do now"
      ninoElement.text() mustBe s"The National Insurance number, $ninoNumber, you entered does not match our records for EORI number $eori."
      infoElement.text() mustBe "If you think our records are incorrect, you can tell us about any changes."

      page.getElementById("individual-utr") mustBe null
      page.getElementById("organisation") mustBe null
      page.getElementById("organisation-utr") mustBe null
      page.getElementById("intro-text") mustBe null
    }

    "has specific content for organisation" in {

      val page = docUtr(isIndividual = false, hasUtr = false).body()

      val heading     = page.getElementById("why-heading")
      val utrElement  = page.getElementById("organisation-utr")
      val infoElement = page.getElementById("additional-info")

      heading.text() mustBe "What you can do now"
      utrElement.text() mustBe s"The Unique Taxpayer Reference, $utrNumber, you entered does not match our records for EORI number $eori."
      infoElement.text() mustBe "If you think our records are incorrect, you can tell us about a change to your business or organisation."

      page.getElementById("individual") mustBe null
      page.getElementById("individual-utr") mustBe null
      page.getElementById("individual-nino") mustBe null
      page.getElementById("intro-text") mustBe null
    }

    "has link to start again" in {

      val link = docUtr().body().getElementById("again-link")

      link.toString must include(ApplicationController.startSubscription(atarService).url)
    }
  }

  implicit val request = withFakeCSRF(FakeRequest.apply("GET", "/atar/subscribe"))

  def docUtr(
    isIndividual: Boolean = true,
    hasUtr: Boolean = true,
    service: Service = atarService,
    customsId: Option[CustomsId] = utr,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg
  ): Document =
    Jsoup.parse(contentAsString(view(name, eori, service, isIndividual, hasUtr, customsId, nameIdOrganisationDetails)))

  def docNino(
    isIndividual: Boolean = true,
    hasUtr: Boolean = true,
    service: Service = atarService,
    customsId: Option[CustomsId] = nino,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg
  ): Document =
    Jsoup.parse(contentAsString(view(name, eori, service, isIndividual, hasUtr, customsId, nameIdOrganisationDetails)))

}
