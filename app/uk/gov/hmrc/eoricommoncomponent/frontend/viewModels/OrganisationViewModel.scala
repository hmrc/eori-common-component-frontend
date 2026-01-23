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

package uk.gov.hmrc.eoricommoncomponent.frontend.viewModels

import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.hint.Hint
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem

object OrganisationViewModel {

  def getRadioItem(isUk: Boolean, form: Form[CdsOrganisationType])(implicit messages: Messages): Seq[RadioItem] = {

    val hintTextOptions = {
      lazy val nonUkOptionHints = Seq(
        CdsOrganisationType.CompanyId    -> messages("cds.matching.organisation-type.radio.organisation.hint-text"),
        CdsOrganisationType.SoleTraderId -> messages("cds.matching.organisation-type.radio.sole-trader.hint-text"),
        CdsOrganisationType.IndividualId -> messages("cds.matching.organisation-type.radio.individual.hint-text")
      )
      if (isUk) Seq.empty else nonUkOptionHints
    }
    lazy val ukOptionsFirstScreen = Seq(
      CdsOrganisationType.CompanyId     -> messages("cds.matching.organisation-type.radio.company.label"),
      CdsOrganisationType.SoleTraderId  -> messages("cds.matching.organisation-type.radio.sole-trader.label"),
      CdsOrganisationType.IndividualId  -> messages("cds.matching.organisation-type.radio.individual.label"),
      CdsOrganisationType.PartnershipId -> messages("cds.matching.organisation-type.radio.partnership.label"),
      CdsOrganisationType.LimitedLiabilityPartnershipId -> messages(
        "cds.matching.organisation-type.radio.limited-liability-partnership.label"
      ),
      CdsOrganisationType.CharityPublicBodyNotForProfitId -> messages(
        "cds.matching.organisation-type.radio.charity-public-body-not-for-profit.label"
      )
    )

    lazy val rowOptionsFirstScreen = Seq(
      CdsOrganisationType.CompanyId    -> messages("cds.matching.organisation-type.radio.organisation.label"),
      CdsOrganisationType.SoleTraderId -> messages("cds.matching.organisation-type.radio.sole-trader.label"),
      CdsOrganisationType.IndividualId -> messages("cds.matching.organisation-type.radio.individual.label")
    )

    val validOptions =
      if (isUk) ukOptionsFirstScreen
      else rowOptionsFirstScreen

    validOptions.map {
      case (value, label) =>
        RadioItem(
          content = Text(label),
          value = Some(value),
          id = Some(s"${form("organisation-type").name}-${value.toLowerCase.replace(" ", "_")}"),
          checked = form("organisation-type").value.contains(value),
          hint = hintTextOptions.toMap.get(value).map(hint => Hint(content = HtmlContent(hint)))
        )

    }
  }

}
