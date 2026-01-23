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

package unit.views.registration

import org.jsoup.Jsoup
import play.api.data.Form
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.organisation_type
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import util.ViewSpec

class OrganisationTypePageSpec extends ViewSpec {
  private val form: Form[CdsOrganisationType] = organisationTypeDetailsForm
  private val thirdCountryOrganisationLabel   = "label[for=organisation-type-company]"
  private val thirdCountrySoleTraderLabel     = "label[for=organisation-type-sole-trader]"
  private val thirdCountryIndividualLabel     = "label[for=organisation-type-individual]"

  private val view = instanceOf[organisation_type]

  "Rest of World (ROW) What do you want to apply as? page" should {

    "display 'an organisation' as an option" in {
      doc.select(thirdCountryOrganisationLabel).text() must include(
        messages("cds.matching.organisation-type.radio.company.label")
      )
    }

    "display 'a sole trader' as an option" in {
      doc.select(thirdCountrySoleTraderLabel).text() must include(
        messages("cds.matching.organisation-type.radio.sole-trader.label")
      )
    }

    "display 'an individual' as an option" in {
      doc.select(thirdCountryIndividualLabel).text() must include(
        messages("cds.matching.organisation-type.radio.individual.label")
      )
    }

  }

  val testRatioItems: Seq[RadioItem] = Seq(
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.company.label")),
      value = Some(CdsOrganisationType.CompanyId),
      id = Some("organisation-type-company"),
      checked = false,
      hint = None
    ),
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.sole-trader.label")),
      value = Some(CdsOrganisationType.SoleTraderId),
      id = Some("organisation-type-sole-trader"),
      checked = false,
      hint = None
    ),
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.individual.label")),
      value = Some(CdsOrganisationType.IndividualId),
      id = Some("organisation-type-individual"),
      checked = false,
      hint = None
    )
  )

  private lazy val doc = {
    implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(
      FakeRequest().withSession(("selected-user-location", "third-country"))
    )
    val result = view(form, testRatioItems, atarService)
    Jsoup.parse(contentAsString(result))
  }

}
