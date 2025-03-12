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

package uk.gov.hmrc.eoricommoncomponent.frontend.viewModels

import play.api.i18n.Messages
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.userLocationForm
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem

object UserLocationViewModel {

  def title(isAffinityOrganisation: Boolean)(implicit messages: Messages): String =
    if (isAffinityOrganisation) messages("cds.registration.user-location.organisation.title-and-heading")
    else messages("cds.registration.user-location.individual.title-and-heading")

  val validOptions: Seq[(String, String)] =
    Seq(
      (UserLocation.Uk, "cds.registration.user-location.location.uk.label"),
      (UserLocation.Islands, "cds.registration.user-location.location.islands-or-iom.label"),
      (UserLocation.ThirdCountryIncEU, "cds.registration.user-location.location.third-country-inc-eu.label")
    )

  def options(implicit messages: Messages): Seq[RadioItem] = validOptions.map {
    case (value, label) =>
      RadioItem(
        content = Text(messages(label)),
        value = Some(value),
        id = Some(s"${userLocationForm("location").name}-${value.toLowerCase.replace(" ", "_")}"),
        checked = userLocationForm("location").value.contains(value)
      )
  }

}
