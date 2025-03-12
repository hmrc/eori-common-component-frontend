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
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.viewModels.UserLocationViewModel
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem

class UserLocationViewModelSpec extends UnitSpec with Injector {

  implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser])
    .build()

  implicit val messages: Messages = MessagesImpl(Lang("en"), instanceOf[MessagesApi])

  "UserLocationViewModel" should {

    "return the correct title for an affinity organization" in {
      val isAffinityOrganisation = true
      val expectedTitle          = messages("cds.registration.user-location.organisation.title-and-heading")
      val actualTitle            = UserLocationViewModel.title(isAffinityOrganisation)
      actualTitle shouldBe expectedTitle
    }

    "return the correct title for an individual" in {
      val isAffinityOrganisation = false
      val expectedTitle          = messages("cds.registration.user-location.individual.title-and-heading")
      val actualTitle            = UserLocationViewModel.title(isAffinityOrganisation)
      actualTitle shouldBe expectedTitle
    }

    "generate valid options with radio items" in {
      val expectedOptions = Seq(
        RadioItem(
          content = Text(messages("cds.registration.user-location.location.uk.label")),
          value = Some(UserLocation.Uk),
          id = Some("location-uk"),
          checked = false
        ),
        RadioItem(
          content = Text(messages("cds.registration.user-location.location.islands-or-iom.label")),
          value = Some(UserLocation.Islands),
          id = Some("location-islands"),
          checked = false
        ),
        RadioItem(
          content = Text(messages("cds.registration.user-location.location.third-country-inc-eu.label")),
          value = Some(UserLocation.ThirdCountryIncEU),
          id = Some("location-third-country-inc-eu"),
          checked = false
        )
      )

      UserLocationViewModel.options should contain theSameElementsAs expectedOptions
    }

  }
}
