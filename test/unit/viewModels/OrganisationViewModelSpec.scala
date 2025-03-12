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

package unit.viewModels

import base.{Injector, UnitSpec}
import play.api.Application
import play.api.data.Form
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.organisationTypeDetailsForm
import uk.gov.hmrc.eoricommoncomponent.frontend.viewModels.OrganisationViewModel
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.hint.Hint
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem

class OrganisationViewModelSpec extends UnitSpec with Injector {

  implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser])
    .build()

  private val form: Form[CdsOrganisationType] = organisationTypeDetailsForm

  implicit val messages: Messages = MessagesImpl(Lang("en"), instanceOf[MessagesApi])

  val ukRadioItems: Seq[RadioItem] = Seq(
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
    ),
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.partnership.label")),
      value = Some(CdsOrganisationType.PartnershipId),
      id = Some("organisation-type-partnership"),
      checked = false,
      hint = None
    ),
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.limited-liability-partnership.label")),
      value = Some(CdsOrganisationType.LimitedLiabilityPartnershipId),
      id = Some("organisation-type-limited-liability-partnership"),
      checked = false,
      hint = None
    ),
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.charity-public-body-not-for-profit.label")),
      value = Some(CdsOrganisationType.CharityPublicBodyNotForProfitId),
      id = Some("organisation-type-charity-public-body-not-for-profit"),
      checked = false,
      hint = None
    )
  )

  val rowRadioItems: Seq[RadioItem] = Seq(
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.organisation.label")),
      value = Some(CdsOrganisationType.CompanyId),
      id = Some("organisation-type-company"),
      checked = false,
      hint = Some(Hint(content = HtmlContent(messages("cds.matching.organisation-type.radio.organisation.hint-text"))))
    ),
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.sole-trader.label")),
      value = Some(CdsOrganisationType.SoleTraderId),
      id = Some("organisation-type-sole-trader"),
      checked = false,
      hint = Some(Hint(content = HtmlContent(messages("cds.matching.organisation-type.radio.sole-trader.hint-text"))))
    ),
    RadioItem(
      content = Text(messages("cds.matching.organisation-type.radio.individual.label")),
      value = Some(CdsOrganisationType.IndividualId),
      id = Some("organisation-type-individual"),
      checked = false,
      hint = Some(Hint(content = HtmlContent(messages("cds.matching.organisation-type.radio.individual.hint-text"))))
    )
  )

  "getRadioItem" should {
    "return six radio items when isUK is true" in {
      val result = OrganisationViewModel.getRadioItem(isUk = true, form)
      result shouldBe ukRadioItems
    }
    "return three radio itmes when isUK is false" in {
      val result = OrganisationViewModel.getRadioItem(isUk = false, form)
      result shouldBe rowRadioItems
    }
  }

}
