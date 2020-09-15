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

package common.pages.registration

import common.pages.WebPage

class RegistrationReviewPage extends WebPage {

  val BusinessNameXPath           = "//td[@id='review-tbl__business-name']"
  val BusinessNameReviewLinkXPath = "//a[@id='review-tbl__business-name_change']"

  val BusinessNameAndAddressLabelXPath = "//th[@id='review-tbl__name-and-address_heading']"

  val UKVatIdentificationNumberXpath      = "//td[@id='review-tbl__gb-vat_number']"
  val UKVatIdentificationNumberXpathLabel = "//td[@id='review-tbl__gb-vat_number_heading']"

  val UKVatIdentificationPostcodeXpath = "//td[@id='review-tbl__gb-vat_postcode']"

  val UKVatIdentificationDateXpath = "//td[@id='review-tbl__gb-vat_date']"

  val UKVatIdentificationNumbersReviewLinkXpath = "//a[@id='review-tbl__gb-vat_change']"

  val EUVatDetailsXpath = "//td[@id='review-tbl__eu-vat']"

  val EUVatIdentificationNumbersReviewLinkXpath = "//a[@id='review-tbl__eu-vat_change']"

  private val EUVatDetailsChangeLinkId = "review-tbl__eu-vat_change"

  val UkVatDetailsChangeLinkId = "review-tbl__gb-vat_change"

  val ContactDetailsXPath = "//td[@id='review-tbl__contact']"

  val ContactDetailsReviewLinkXPath = "//a[@id='review-tbl__contact_change']"

  val CorrespondenceAddressXpath = "//td[@id='review-tbl__correspondence']"

  val ThirdCountryIdNumbersXPath = "//td[@id='review-tbl__third-country-id']"

  val ThirdCountryIdNumbersReviewLinkXPath = "//a[@id='review-tbl__third-country-id_change']"

  val EUDisclosureConsentXPath = "//tr[@id='review-tbl__disclosure']/td[1]"

  val ShortNameXPath = "//td[@id='review-tbl__short-name']"

  val ShortNameReviewLinkXPath = "//a[@id='review-tbl__short-name_change']"

  val DateOfEstablishmentXPath = "//tr[@id='review-tbl__doe']/td[1]"

  val DateOfEstablishmentLabelXPath = "//tr[@id='review-tbl__doe']/th"

  val IndividualDateOfBirthXPath           = "//td[@id='review-tbl__date-of-birth']"
  val IndividualDateOfBirthReviewLinkXPath = "//a[@id='review-tbl__date-of-birth_change']"

  val IndividualDateOfBirthLabelXPath = "//td[@id='review-tbl__date-of-birth_heading']"

  val PrincipalEconomicActivityXPath = "//td[@id='review-tbl__activity']"

  val AddressXPath           = "//td[@id='review-tbl__address']"
  val AddressReviewLinkXPath = "//a[@id='review-tbl__address_change']"

  val SixLineAddressXPath           = "//td[@id='review-tbl__six_line_address']"
  val SixLineAddressXPathLabel      = "//td[@id='review-tbl__six_line_address_heading']"
  val SixLineAddressReviewLinkXPath = "//a[@id='review-tbl__six_line_address_change']"

  val AddressHeadingXPath = "//th[@id='review-tbl__address_heading']"

  val BusinessNameLabelXPath = "//th[@id='review-tbl__business-name_heading']"

  val UtrLabelXPath = "//*[@id='review-tbl__utr_heading']"

  val FullNameXPath           = "//td[@id='review-tbl__full-name']"
  val FullNameReviewLinkXPath = "//a[@id='review-tbl__full-name_change']"

  val organisationAddressValueXpath = "//*[@id='review-tbl__six_line_address']"

  def changeAnswerText(heading: String): String = s"Change $heading"

  override val title = "Check your answers"

}

object RegistrationReviewPage extends RegistrationReviewPage
