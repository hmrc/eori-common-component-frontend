/*
 * Copyright 2020 HM Revenue & Customs
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

class SubscriptionExistingDetailsReviewPage extends WebPage {

  val BusinessNameLabelXpath = "//th[@id='review-tbl__business-name_heading']"
  val BusinessNameValueXpath = "//td[@id='review-tbl__business-name']"

  val UKVatIdentificationNumbersXpathLabel = "//th[@id='review-tbl__gb-vat_number_heading']"
  val UKVatIdentificationNumbersXpath = "//td[@id='review-tbl__gb-vat_number']"

  val UKVatIdentificationPostcodeXpathLabel = "//th[@id='review-tbl__gb-vat_postcode_heading']"
  val UKVatIdentificationPostcodeXpath = "//td[@id='review-tbl__gb-vat_postcode']"

  val UKVatIdentificationDateXpathLabel = "//th[@id='review-tbl__gb-vat_date_heading']"
  val UKVatIdentificationDateXpath = "//td[@id='review-tbl__gb-vat_date']"

  val UKVatIdentificationNumbersReviewLinkXpath = "//a[@id='review-tbl__gb-vat_change']"

  val EUVatIdentificationNumbersXpathLabel = "//th[@id='review-tbl__eu-vat_heading']"
  val EUVatIdentificationNumbersXpath = "//td[@id='review-tbl__eu-vat']"
  val EUVatIdentificationNumbersReviewLinkXpath = "//a[@id='review-tbl__eu-vat_change']"

  val ContactDetailsXPathLabel = "//th[@id='review-tbl__contact_heading']"
  val ContactDetailsXPath = "//td[@id='review-tbl__contact']"

  val ContactDetailsReviewLinkXPath = "//a[@id='review-tbl__contact_change']"

  val ConfirmAndRegisterInfoXpath = "//p[@id='disclaimer-content']"

  val EUDisclosureConsentXPathLabel = "//th[@id='review-tbl__disclosure_heading']"
  val EUDisclosureConsentXPath = "//tr[@id='review-tbl__disclosure']/td[1]"
  val EUDisclosureReviewLinkXpath = "//a[@id='review-tbl__disclosure_change']"

  val ShortNameXPathLabel = "//th[@id='review-tbl__short-name_heading']"
  val ShortNameXPath = "//td[@id='review-tbl__short-name']"
  val ShortNameReviewLinkXPath = "//a[@id='review-tbl__short-name_change']"

  val NatureOfBusinessXPathLabel = "//th[@id='review-tbl__activity_heading']"
  val NatureOfBusinessXPath = "//td[@id='review-tbl__activity']"
  val NatureOfBusinessReviewLinkXPath = "//a[@id='review-tbl__activity_change']"

  val startAgainLinkXPath = "//a[@id='review-tbl__start-again']"

  val UtrNoLabelXPath = "//th[@id='review-tbl__utr_heading']"
  val UtrNoLabelValueXPath = "//td[@id='review-tbl__utr']"

  val DateOfEstablishmentLabelXPath = "//tr[@id='review-tbl__doe']/th[@class='bold']"
  val DateOfEstablishmentXPath = "//tr[@id='review-tbl__doe']/td[1]"
  val DateOfEstablishmentReviewLinkXPath = "//*[@id='review-tbl__doe']/td[2]/a"

  val LimitedAddressLabelXpath = "//th[@id='review-tbl__address_heading']"
  val LimitedAddressValueXpath = "//td[@id='review-tbl__address']"
  val LimitedAddressReviewLink = "//a[@id='review-tbl__address_change']"

  def changeAnswerText(heading: String): String = s"Change $heading"


  override val title = "Check your details"

}

object SubscriptionExistingDetailsReviewPage extends SubscriptionExistingDetailsReviewPage
