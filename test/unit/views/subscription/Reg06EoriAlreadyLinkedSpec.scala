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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.reg06_eori_already_linked
import util.ViewSpec

class Reg06EoriAlreadyLinkedSpec extends ViewSpec {

  private val email             = "email@email.email"
  private val eori              = "GB123456789012"
  private val expectedPageTitle = "The details you gave us did not match our records"
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
        .text() must include(
        "Before you go Your feedback helps us make our service better. Take a short survey to share your feedback on this service."
      )
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

      val page = docUtr().body()

      val utrElement     = page.getElementById("individual-utr")
      val contactElement = page.getElementById("contact-info")

      utrElement.text() mustBe s"The Unique Taxpayer Reference, $utrNumber, you entered does not match our records for EORI number $eori."
      contactElement.text() mustBe "The details you gave us will be reviewed by the relevant team. They will contact you on email@email.email within three working days to provide the next steps."

      page.getElementById("individual-nino") mustBe null
      page.getElementById("organisation") mustBe null
      page.getElementById("organisation-utr") mustBe null
      page.getElementById("intro-text") mustBe null
      page.getElementById("intro-none-text") mustBe null
    }

    "has specific content for individual with NINO" in {

      val page = docNino().body()

      val ninoElement    = page.getElementById("individual-nino")
      val contactElement = page.getElementById("contact-info")

      ninoElement.text() mustBe s"The National Insurance number, $ninoNumber, you entered does not match our records for EORI number $eori."
      contactElement.text() mustBe "The details you gave us will be reviewed by the relevant team. They will contact you on email@email.email within three working days to provide the next steps."

      page.getElementById("individual-utr") mustBe null
      page.getElementById("organisation") mustBe null
      page.getElementById("organisation-utr") mustBe null
      page.getElementById("intro-text") mustBe null
      page.getElementById("intro-none-text") mustBe null
    }

    "has specific content for organisation" in {

      val page = docOrgUtr().body()

      val utrElement     = page.getElementById("organisation-utr")
      val contactElement = page.getElementById("contact-info")

      utrElement.text() mustBe s"The Unique Taxpayer Reference, $utrNumber, you entered does not match our records for EORI number $eori."
      contactElement.text() mustBe "The details you gave us will be reviewed by the relevant team. They will contact you on email@email.email within three working days to provide the next steps."

      page.getElementById("individual") mustBe null
      page.getElementById("individual-utr") mustBe null
      page.getElementById("individual-nino") mustBe null
      page.getElementById("intro-text") mustBe null
      page.getElementById("intro-none-text") mustBe null
    }

    "has specific content for No Ids Organisation" in {

      val page = docNoId().body()

      val contactElement = page.getElementById("contact-info")
      val introElement   = page.getElementById("intro-text")

      introElement.text() mustBe "The details you entered do not match our records for EORI number GB123456789012."
      contactElement.text() mustBe "The details you gave us will be reviewed by the relevant team. They will contact you on email@email.email within three working days to provide the next steps."

      page.getElementById("individual") mustBe null
      page.getElementById("individual-utr") mustBe null
      page.getElementById("individual-nino") mustBe null
      page.getElementById("intro-nino-text") mustBe null
      page.getElementById("organisation-utr") mustBe null
    }

    "has specific content for individual with no UTR or no NINO" in {

      val page = docNinoNone().body()

      val contactElement = page.getElementById("contact-info")
      val introElement   = page.getElementById("intro-ind-text")

      introElement.text() mustBe "The details you entered do not match our records for EORI number GB123456789012."
      contactElement.text() mustBe "The details you gave us will be reviewed by the relevant team. They will contact you on email@email.email within three working days to provide the next steps."

      page.getElementById("individual-utr") mustBe null
      page.getElementById("individual-nino") mustBe null
      page.getElementById("organisation") mustBe null
      page.getElementById("organisation-utr") mustBe null
      page.getElementById("intro-text") mustBe null
    }
  }

  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(FakeRequest.apply("GET", "/atar/subscribe"))

  def docUtr(
    isIndividual: Boolean = true,
    service: Service = atarService,
    customsId: Option[CustomsId] = utr,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg
  ): Document =
    Jsoup.parse(contentAsString(view(eori, service, isIndividual, customsId, nameIdOrganisationDetails, email)))

  def docNino(
    isIndividual: Boolean = true,
    service: Service = atarService,
    customsId: Option[CustomsId] = nino,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg
  ): Document =
    Jsoup.parse(contentAsString(view(eori, service, isIndividual, customsId, nameIdOrganisationDetails, email)))

  def docNinoNone(
    isIndividual: Boolean = true,
    service: Service = atarService,
    customsId: Option[CustomsId] = None,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg
  ): Document =
    Jsoup.parse(contentAsString(view(eori, service, isIndividual, customsId, nameIdOrganisationDetails, email)))

  def docOrgUtr(
    isIndividual: Boolean = false,
    service: Service = atarService,
    customsId: Option[CustomsId] = utr,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg
  ): Document =
    Jsoup.parse(contentAsString(view(eori, service, isIndividual, customsId, nameIdOrganisationDetails, email)))

  def docNoId(
    isIndividual: Boolean = false,
    service: Service = atarService,
    customsId: Option[CustomsId] = utr,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = None
  ): Document =
    Jsoup.parse(contentAsString(view(eori, service, isIndividual, customsId, nameIdOrganisationDetails, email)))

}
