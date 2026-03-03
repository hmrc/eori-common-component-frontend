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

package common.pages.subscription

import common.pages.WebPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EuEoriRegisteredAddressModel

sealed trait EuEoriRegisteredAddressPage extends WebPage {

  override val title = "Your details"

  val formId: String = "euEoriRegisteredAddressForm"

  val continueButtonXpath = "//*[@id='continue-button']"

  val PageLevelErrorSummaryListXPath  = "//ul[@class='govuk-list govuk-error-summary__list']"
  val lineOneFieldXPath               = "//*[@id='line-1']"
  val fieldLevelErrorAddressLineOne   = "//p[@id='line-1-error' and @class='govuk-error-message']"
  val lineThreeFieldXPath             = "//*[@id='line-3']"
  val fieldLevelErrorAddressLineThree = "//p[@id='line-3-error' and @class='govuk-error-message']"
  val postcodeFieldXPath              = "//*[@id='postcode']"
  val fieldLevelErrorPostcode         = "//p[@id='postcode-error' and @class='govuk-error-message']"
  val countryFieldXPath               = "//*[@id='countryCode']"
  val fieldLevelErrorCountry          = "//p[@id='countryCode-error' and @class='govuk-error-message']"

  val filledValues: EuEoriRegisteredAddressModel =
    EuEoriRegisteredAddressModel(
      lineOne = "Line 1",
      lineThree = "Town",
      postcode = Some("FR29 1AA"),
      country = "France"
    )

}

object EuEoriRegisteredAddressPage extends EuEoriRegisteredAddressPage
