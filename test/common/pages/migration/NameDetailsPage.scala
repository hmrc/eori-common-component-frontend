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

package common.pages.migration

import common.pages.WebPage
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.NameModel

trait NameDetailsPage extends WebPage {

  override val title = "What is your name?"

  val nameDetailsFormId = "subscriptionNameForm"

  val givenNameFieldXPath           = "//*[@id='given-name']"
  val givenNameFieldLevelErrorXPath = "//p[@id='given-name-error'][@class='govuk-error-message']"
  val givenNameFieldLabel           = "Given name"
  val givenNameFieldId              = "given-name"
  val givenNameFieldName            = "given-name"

  val familyNameFieldXPath           = "//*[@id='family-name']"
  val familyNameFieldLevelErrorXPath = "//p[@id='family-name-error'][@class='govuk-error-message']"
  val familyNameFieldLabel           = "Family name"
  val familyNameFieldId              = "family-name"
  val familyNameFieldName            = "family-name"

  val filledValues: NameModel = NameModel(
    givenName = "Alain",
    familyName = "Lemoine"
  )

}

object NameDetailsPage extends NameDetailsPage
