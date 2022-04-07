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

package common.pages.subscription

import common.pages.WebPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel

sealed trait ContactAddressPage extends WebPage {

  override val title = "Your details"

  val formId: String = "contactAddressForm"

  val continueButtonXpath = "//*[@class='govuk-button']"

  val PageLevelErrorSummaryListXPath  = "//ul[@class='govuk-list govuk-error-summary__list']"
  val lineOneFieldXPath               = "//*[@id='line-1']"
  val fieldLevelErrorAddressLineOne   = "//p[@id='line-1-error' and @class='govuk-error-message']"
  val lineTwoFieldXPath               = "//*[@id='line-2']"
  val fieldLevelErrorAddressLineTwo   = "//p[@id='line-2-error' and @class='govuk-error-message']"
  val lineThreeFieldXPath             = "//*[@id='line-3']"
  val fieldLevelErrorAddressLineThree = "//p[@id='line-3-error' and @class='govuk-error-message']"
  val lineFourFieldXPath              = "//*[@id='line-4']"
  val fieldLevelErrorAddressLineFour  = "//p[@id='line-4-error' and @class='govuk-error-message']"
  val postcodeFieldXPath              = "//*[@id='postcode']"
  val fieldLevelErrorPostcode         = "//p[@id='postcode-error' and @class='govuk-error-message']"
  val countryFieldXPath               = "//*[@id='countryCode']"
  val fieldLevelErrorCountry          = "//p[@id='countryCode-error' and @class='govuk-error-message']"

  val filledValues: ContactAddressModel =
    ContactAddressModel(
      lineOne = "Line 1",
      lineTwo = Some("Line 2"),
      lineThree = "Town",
      lineFour = Some("Region"),
      postcode = Some("SE28 1AA"),
      country = "United Kingdom"
    )

}

object ContactAddressPage extends ContactAddressPage
