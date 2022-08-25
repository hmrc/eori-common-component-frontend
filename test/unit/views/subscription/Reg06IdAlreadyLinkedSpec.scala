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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.ApplicationController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.reg06_id_already_linked
import util.ViewSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._

class Reg06IdAlreadyLinkedSpec extends ViewSpec {

  private val name              = "John Doe"
  private val email             = "email@email.email"
  private val eori              = "GB123456789012"
  private val expectedPageTitle = "The details you gave us are matched to a different EORI number"
  private val utr               = Some(Utr("UTRXXXXX"))
  private val utrNumber         = "UTRXXXXX"
  private val nino              = Some(Nino("AAXXXXX"))
  private val ninoNumber        = "AAXXXXX"
  private val nameIdOrg         = Some(NameIdOrganisationMatchModel("Name", utr.get.id))

  private val pageHeadingExpectedText =
    "The details you gave us are matched to a different EORI number"

  private val view = instanceOf[reg06_id_already_linked]

  "Id Already Linked outcome page" should {

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

  "Id already linked page" should {

    "has specific content for individual with UTR" in {

      val page = docUtr(isIndividual = true, hasUtr = true).body()

      val utrElement  = page.getElementById("individual-utr")
      val infoElement = page.getElementById("additional-info")

      utrElement.text() mustBe s"The Unique Taxpayer Reference, $utrNumber, is already used and cannot be matched with $eori."
      infoElement.text() mustBe "The details you gave us have already been linked to another EORI number, not GB123456789012."

      page.getElementById("individual-nino") mustBe null
      page.getElementById("organisation") mustBe null
      page.getElementById("organisation-utr") mustBe null
      page.getElementById("info-steps") mustBe null
    }

    "has specific content for individual with NINO" in {

      val page = docNino(isIndividual = true, hasUtr = false).body()

      val ninoElement = page.getElementById("individual-nino")
      val infoElement = page.getElementById("additional-info")

      ninoElement.text() mustBe s"The National Insurance number, $ninoNumber, is already used and cannot be matched with $eori."
      infoElement.text() mustBe "The details you gave us have already been linked to another EORI number, not GB123456789012."

      page.getElementById("individual-utr") mustBe null
      page.getElementById("organisation") mustBe null
      page.getElementById("organisation-utr") mustBe null
      page.getElementById("info-steps") mustBe null
    }

    "has specific content for organisation" in {

      val page = docUtr(isIndividual = false, hasUtr = false).body()

      val utrElement  = page.getElementById("organisation-utr")
      val infoElement = page.getElementById("additional-info")

      utrElement.text() mustBe s"The Unique Taxpayer Reference, $utrNumber, is already used and cannot be matched with $eori."
      infoElement.text() mustBe "The details you gave us have already been linked to another EORI number, not GB123456789012."

      page.getElementById("individual") mustBe null
      page.getElementById("individual-utr") mustBe null
      page.getElementById("individual-nino") mustBe null
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
    Jsoup.parse(
      contentAsString(view(name, eori, service, isIndividual, hasUtr, customsId, nameIdOrganisationDetails, email))
    )

  def docNino(
    isIndividual: Boolean = true,
    hasUtr: Boolean = true,
    service: Service = atarService,
    customsId: Option[CustomsId] = nino,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg
  ): Document =
    Jsoup.parse(
      contentAsString(view(name, eori, service, isIndividual, hasUtr, customsId, nameIdOrganisationDetails, email))
    )

}
