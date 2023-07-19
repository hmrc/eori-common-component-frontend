/*
 * Copyright 2023 HM Revenue & Customs
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

package unit.views.migration

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.i18n.{Messages, MessagesApi, MessagesImpl}
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import play.i18n.Lang
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{
  AddressLookupParams,
  AddressViewModel,
  CompanyRegisteredCountry,
  ContactAddressModel
}
import uk.gov.hmrc.play.language.LanguageUtils
import util.ViewSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.check_your_details
import java.time.LocalDate

class CheckYourDetailsSpec extends ViewSpec {

  val view = instanceOf[check_your_details]

  private val messageApi: MessagesApi = instanceOf[MessagesApi]

  private val languageUtils = instanceOf[LanguageUtils]

  private val organisationType = Some(CdsOrganisationType.Company)

  private val contactDetails = Some(
    ContactDetailsModel(
      "John Doe",
      "email@example.com",
      "11111111111",
      None,
      useAddressFromRegistrationDetails = false,
      None,
      None,
      None,
      None
    )
  )

  private val contactAddressDetail = Some(
    ContactAddressModel("flat 20", Some("street line 2"), "city", Some("region"), Some("HJ2 3HJ"), "FR")
  )

  private val address           = Some(AddressViewModel("Street", "City", Some("Postcode"), "GB"))
  private val eori              = Some("ZZ123456789112")
  private val email             = Some("email@example.com")
  private val utr               = Some(Utr("UTRXXXXX"))
  private val nameIdOrg         = Some(NameIdOrganisationMatchModel("Name", utr.get.id))
  private val dateTime          = Some(LocalDate.now())
  private val nino              = Some(Nino("AB123456C"))
  private val nameDobMatchModel = Some(NameDobMatchModel("FName", None, "LName", LocalDate.parse("2003-04-08")))
  private val registeredCountry = Some(CompanyRegisteredCountry("GB"))
  private val enLan             = Lang.forCode("en")
  private val cyLan             = Lang.forCode("cy")

  "Start page" should {

    "display all details" when {

      "user is during UK Company journey" in {
        val email = doc().body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Email address"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = doc().body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "EORI number"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val orgName = doc().body.getElementsByClass("review-tbl__orgname").get(0)
        orgName.getElementsByClass("govuk-summary-list__key").text mustBe "Company name"
        orgName.getElementsByClass("govuk-summary-list__value").text mustBe "Name"
        orgName.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/nameid/review"

        val utrRow = doc().body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass("govuk-summary-list__key").text mustBe "Corporation Tax UTR"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe "UTRXXXXX"
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/nameid/review"

        val address = doc().body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Company address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"

        val dateEstablished = doc().body.getElementsByClass("review-tbl__date-established").get(0)
        dateEstablished.getElementsByClass("govuk-summary-list__key").text mustBe "Date of establishment"
        dateEstablished.getElementsByClass("govuk-summary-list__value").text mustBe languageUtils.Dates.formatDate(
          dateTime.get
        )
        dateEstablished.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/date-established/review"
        doc().body.getElementsByClass("review-tbl__date-established").size() mustBe 1
      }

      "user is during UK Sole Trader UTR journey" in {

        val page = doc(isIndividualSubscriptionFlow = true, nameIdOrganisationDetails = None)

        val email = page.body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Email address"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = page.body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "EORI number"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val fullName = page.body.getElementsByClass("review-tbl__full-name").get(0)
        fullName.getElementsByClass("govuk-summary-list__key").text mustBe "Full name"
        fullName.getElementsByClass("govuk-summary-list__value").text mustBe "FName LName"
        fullName.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"

        val dob = page.body.getElementsByClass("review-tbl__date-of-birth").get(0)
        dob.getElementsByClass("govuk-summary-list__key").text mustBe "Date of birth"
        dob.getElementsByClass("govuk-summary-list__value").text mustBe "8 April 2003"
        dob.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"
        page.body.getElementsByClass("review-tbl__date-of-birth").size() mustBe 1

        val utrRow = page.body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass(
          "govuk-summary-list__key"
        ).text mustBe "Self Assessment Unique Taxpayer Reference (UTR)"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe "UTRXXXXX"
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/chooseid/review"

        val address = page.body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Your address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"
      }
      "user is during UK Sole Trader NINo journey" in {

        val page = doc(isIndividualSubscriptionFlow = true, customsId = nino, nameIdOrganisationDetails = None)

        val email = page.body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Email address"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = page.body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "EORI number"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val fullName = page.body.getElementsByClass("review-tbl__full-name").get(0)
        fullName.getElementsByClass("govuk-summary-list__key").text mustBe "Full name"
        fullName.getElementsByClass("govuk-summary-list__value").text mustBe "FName LName"
        fullName.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"

        val dob = page.body.getElementsByClass("review-tbl__date-of-birth").get(0)
        dob.getElementsByClass("govuk-summary-list__key").text mustBe "Date of birth"
        dob.getElementsByClass("govuk-summary-list__value").text mustBe "8 April 2003"
        dob.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"
        page.body.getElementsByClass("review-tbl__date-of-birth").size() mustBe 1

        val ninoRow = page.body.getElementsByClass("review-tbl__nino").get(0)
        ninoRow.getElementsByClass("govuk-summary-list__key").text mustBe "National Insurance number"
        ninoRow.getElementsByClass("govuk-summary-list__value").text mustBe "AB123456C"
        ninoRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/chooseid/review"

        val address = page.body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Your address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"
      }

      "user is during ROW Organisation journey without UTR" in {

        val page = doc(
          customsId = None,
          isThirdCountrySubscription = true,
          nameIdOrganisationDetails = None,
          companyRegisteredCountry = registeredCountry
        )

        val email = page.body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Email address"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = page.body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "EORI number"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val nameOrg = page.body.getElementsByClass("review-tbl__org_name").get(0)
        nameOrg.getElementsByClass("govuk-summary-list__key").text mustBe "Organisation name"
        nameOrg.getElementsByClass("govuk-summary-list__value").text mustBe "Org name"
        nameOrg.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/name/review"

        val utrRow = page.body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass(
          "govuk-summary-list__key"
        ).text mustBe "Self Assessment Unique Taxpayer Reference (UTR)"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe "Not entered"
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/row-utr/review"

        val countryLocation = page.body.getElementsByClass("review-tbl__country-location").get(0)
        countryLocation.getElementsByClass("govuk-summary-list__key").text mustBe "Country location"
        countryLocation.getElementsByClass("govuk-summary-list__value").text mustBe "United Kingdom"
        countryLocation.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/row-country/review"

        val contactDetails = page.body.getElementsByClass("review-tbl__contact-details").get(0)
        contactDetails.getElementsByClass("govuk-summary-list__key").text mustBe "Contact details"
        contactDetails.getElementsByClass("govuk-summary-list__value").text mustBe "John Doe 11111111111"
        contactDetails.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/contact-details/review"

        val contactAddress = page.body.getElementsByClass("review-tbl__contact-address").get(0)
        contactAddress.getElementsByClass("govuk-summary-list__key").text mustBe "Contact address"
        contactAddress.getElementsByClass(
          "govuk-summary-list__value"
        ).text mustBe "flat 20 street line 2 city region HJ2 3HJ France"
        contactAddress.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/contact-address/review"

        val dateEstablished = page.body.getElementsByClass("review-tbl__date-established").get(0)
        dateEstablished.getElementsByClass("govuk-summary-list__key").text mustBe "Date of establishment"
        dateEstablished.getElementsByClass("govuk-summary-list__value").text mustBe languageUtils.Dates.formatDate(
          dateTime.get
        )
        dateEstablished.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/date-established/review"
        page.body.getElementsByClass("review-tbl__date-established").size() mustBe 1
      }

      "user is during ROW Organisation journey without UTR (Welsh language)" in {
        val page = doc(
          customsId = None,
          isThirdCountrySubscription = true,
          nameIdOrganisationDetails = None,
          companyRegisteredCountry = registeredCountry,
          language = cyLan
        )

        val email = page.body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Cyfeiriad e-bost"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = page.body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "Rhif EORI"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val nameOrg = page.body.getElementsByClass("review-tbl__org_name").get(0)
        nameOrg.getElementsByClass("govuk-summary-list__key").text mustBe "Enw’r sefydliad"
        nameOrg.getElementsByClass("govuk-summary-list__value").text mustBe "Org name"
        nameOrg.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/name/review"

        val utrRow = page.body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass(
          "govuk-summary-list__key"
        ).text mustBe "Cyfeirnod Unigryw y Trethdalwr (UTR) ar gyfer Hunanasesiad"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe "Heb ei nodi"
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/row-utr/review"

        val countryLocation = page.body.getElementsByClass("review-tbl__country-location").get(0)
        countryLocation.getElementsByClass("govuk-summary-list__key").text mustBe "Lleoliad y wlad"
        countryLocation.getElementsByClass("govuk-summary-list__value").text mustBe "Y Deyrnas Unedig"
        countryLocation.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/row-country/review"

        val contactDetails = page.body.getElementsByClass("review-tbl__contact-details").get(0)
        contactDetails.getElementsByClass("govuk-summary-list__key").text mustBe "Cyswllt"
        contactDetails.getElementsByClass("govuk-summary-list__value").text mustBe "John Doe 11111111111"
        contactDetails.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/contact-details/review"

        val dateEstablished = page.body.getElementsByClass("review-tbl__date-established").get(0)
        dateEstablished.getElementsByClass("govuk-summary-list__key").text mustBe "Dyddiad sefydlu"
        dateEstablished.getElementsByClass("govuk-summary-list__value").text mustBe languageUtils.Dates.formatDate(
          dateTime.get
        )(MessagesImpl(cyLan, messageApi))
        dateEstablished.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/date-established/review"
        page.body.getElementsByClass("review-tbl__date-established").size() mustBe 1
      }

      "user is during ROW Organisation journey with UTR" in {

        val page = doc(isThirdCountrySubscription = true)

        val email = page.body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Email address"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = page.body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "EORI number"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val nameOrg = page.body.getElementsByClass("review-tbl__org_name").get(0)
        nameOrg.getElementsByClass("govuk-summary-list__key").text mustBe "Organisation name"
        nameOrg.getElementsByClass("govuk-summary-list__value").text mustBe "Org name"
        nameOrg.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/name/review"

        val utrRow = page.body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass("govuk-summary-list__key").text mustBe "Corporation Tax UTR"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe "UTRXXXXX"
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/row-utr/review"

        val address = page.body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Organisation address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"

        val dateEstablished = page.body.getElementsByClass("review-tbl__date-established").get(0)
        dateEstablished.getElementsByClass("govuk-summary-list__key").text mustBe "Date of establishment"
        dateEstablished.getElementsByClass("govuk-summary-list__value").text mustBe languageUtils.Dates.formatDate(
          dateTime.get
        )
        dateEstablished.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/date-established/review"
        page.body.getElementsByClass("review-tbl__date-established").size() mustBe 1
      }

      "user is during ROW Individual journey without UTR and NINo" in {

        val page = doc(
          isIndividualSubscriptionFlow = true,
          customsId = None,
          nameIdOrganisationDetails = None,
          isThirdCountrySubscription = true,
          companyRegisteredCountry = registeredCountry
        )

        val email = page.body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Email address"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = page.body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "EORI number"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val fullName = page.body.getElementsByClass("review-tbl__full-name").get(0)
        fullName.getElementsByClass("govuk-summary-list__key").text mustBe "Full name"
        fullName.getElementsByClass("govuk-summary-list__value").text mustBe "FName LName"
        fullName.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"

        val dob = page.body.getElementsByClass("review-tbl__date-of-birth").get(0)
        dob.getElementsByClass("govuk-summary-list__key").text mustBe "Date of birth"
        dob.getElementsByClass("govuk-summary-list__value").text mustBe "8 April 2003"
        dob.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"
        page.body.getElementsByClass("review-tbl__date-of-birth").size() mustBe 1

        val utrRow = page.body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass(
          "govuk-summary-list__key"
        ).text mustBe "Self Assessment Unique Taxpayer Reference (UTR)"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe "Not entered"
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/row-utr/review"

        val nino = page.body.getElementsByClass("review-tbl__nino").get(0)
        nino.getElementsByClass("govuk-summary-list__key").text mustBe "National Insurance number"
        nino.getElementsByClass("govuk-summary-list__value").text mustBe "Not entered"
        nino.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/row-nino/review"

        val countryLocation = page.body.getElementsByClass("review-tbl__country-location").get(0)
        countryLocation.getElementsByClass("govuk-summary-list__key").text mustBe "Country location"
        countryLocation.getElementsByClass("govuk-summary-list__value").text mustBe "United Kingdom"
        countryLocation.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/row-country/review"

        val contactDetails = page.body.getElementsByClass("review-tbl__contact-details").get(0)
        contactDetails.getElementsByClass("govuk-summary-list__key").text mustBe "Contact details"
        contactDetails.getElementsByClass("govuk-summary-list__value").text mustBe "John Doe 11111111111"
        contactDetails.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/contact-details/review"

        val contactAddress = page.body.getElementsByClass("review-tbl__contact-address").get(0)
        contactAddress.getElementsByClass("govuk-summary-list__key").text mustBe "Contact address"
        contactAddress.getElementsByClass(
          "govuk-summary-list__value"
        ).text mustBe "flat 20 street line 2 city region HJ2 3HJ France"
        contactAddress.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/contact-address/review"
      }

      "user is during ROW Individual journey with UTR" in {

        val page =
          doc(isIndividualSubscriptionFlow = true, nameIdOrganisationDetails = None, isThirdCountrySubscription = true)

        val email = page.body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Email address"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = page.body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "EORI number"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val fullName = page.body.getElementsByClass("review-tbl__full-name").get(0)
        fullName.getElementsByClass("govuk-summary-list__key").text mustBe "Full name"
        fullName.getElementsByClass("govuk-summary-list__value").text mustBe "FName LName"
        fullName.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"

        val dob = page.body.getElementsByClass("review-tbl__date-of-birth").get(0)
        dob.getElementsByClass("govuk-summary-list__key").text mustBe "Date of birth"
        dob.getElementsByClass("govuk-summary-list__value").text mustBe "8 April 2003"
        dob.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"
        page.body.getElementsByClass("review-tbl__date-of-birth").size() mustBe 1

        val utrRow = page.body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass(
          "govuk-summary-list__key"
        ).text mustBe "Self Assessment Unique Taxpayer Reference (UTR)"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe "UTRXXXXX"
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/row-utr/review"

        val address = page.body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Your address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"

      }

      "user is during ROW Individual journey with NINo" in {

        val page = doc(
          isIndividualSubscriptionFlow = true,
          customsId = nino,
          nameIdOrganisationDetails = None,
          isThirdCountrySubscription = true
        )

        val email = page.body.getElementsByClass("review-tbl__email").get(0)
        email.getElementsByClass("govuk-summary-list__key").text mustBe "Email address"
        email.getElementsByClass("govuk-summary-list__value").text mustBe "email@example.com"

        val eori = page.body.getElementsByClass("review-tbl__eori-number").get(0)
        eori.getElementsByClass("govuk-summary-list__key").text mustBe "EORI number"
        eori.getElementsByClass("govuk-summary-list__value").text mustBe "ZZ123456789112"
        eori.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori/review"

        val fullName = page.body.getElementsByClass("review-tbl__full-name").get(0)
        fullName.getElementsByClass("govuk-summary-list__key").text mustBe "Full name"
        fullName.getElementsByClass("govuk-summary-list__value").text mustBe "FName LName"
        fullName.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"

        val dob = page.body.getElementsByClass("review-tbl__date-of-birth").get(0)
        dob.getElementsByClass("govuk-summary-list__key").text mustBe "Date of birth"
        dob.getElementsByClass("govuk-summary-list__value").text mustBe "8 April 2003"
        dob.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/namedob/review"
        page.body.getElementsByClass("review-tbl__date-of-birth").size() mustBe 1

        val utrRow = page.body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass(
          "govuk-summary-list__key"
        ).text mustBe "Self Assessment Unique Taxpayer Reference (UTR)"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe "Not entered"
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/row-utr/review"

        val ninoRow = page.body.getElementsByClass("review-tbl__nino").get(0)
        ninoRow.getElementsByClass("govuk-summary-list__key").text mustBe "National Insurance number"
        ninoRow.getElementsByClass("govuk-summary-list__value").text mustBe "AB123456C"
        ninoRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/row-nino/review"

        val address = page.body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Your address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"

      }
    }

    "not display address" when {

      "user is during organisation ROW journey without UTR" in {

        val page = doc(
          customsId = None,
          nameIdOrganisationDetails = None,
          isThirdCountrySubscription = true,
          companyRegisteredCountry = registeredCountry
        )

        val countryLocation = page.body.getElementsByClass("review-tbl__country-location").get(0)
        countryLocation.getElementsByClass("govuk-summary-list__key").text mustBe "Country location"
        countryLocation.getElementsByClass("govuk-summary-list__value").text mustBe "United Kingdom"
        countryLocation.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/row-country/review"

        page.body.getElementsByClass("review-tbl__name-and-address") mustBe empty

      }

      "user is during individual ROW journey without UTR" in {

        val page = doc(
          isIndividualSubscriptionFlow = true,
          customsId = None,
          nameIdOrganisationDetails = None,
          isThirdCountrySubscription = true,
          companyRegisteredCountry = registeredCountry
        )

        val countryLocation = page.body.getElementsByClass("review-tbl__country-location").get(0)
        countryLocation.getElementsByClass("govuk-summary-list__key").text mustBe "Country location"
        countryLocation.getElementsByClass("govuk-summary-list__value").text mustBe "United Kingdom"
        countryLocation.getElementsByTag("a").attr(
          "href"
        ) mustBe "/customs-enrolment-services/atar/subscribe/row-country/review"

        page.body.getElementsByClass("review-tbl__name-and-address") mustBe empty
      }
    }

    "not display country location" when {

      "user is during organisation ROW journey with UTR" in {

        val page = doc(isThirdCountrySubscription = true)

        page.body.getElementsByClass("review-tbl__country-location") mustBe empty

        val address = page.body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Organisation address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"

      }

      "user is during individual ROW journey with UTR" in {

        val page =
          doc(isIndividualSubscriptionFlow = true, nameIdOrganisationDetails = None, isThirdCountrySubscription = true)

        page.body.getElementsByClass("review-tbl__country-location") mustBe empty

        val address = page.body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Your address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"

      }

      "user is during individual ROW journey with NINo" in {

        val page = doc(
          customsId = nino,
          isIndividualSubscriptionFlow = true,
          nameIdOrganisationDetails = None,
          isThirdCountrySubscription = true
        )

        page.body.getElementsByClass("review-tbl__country-location") mustBe empty

        val address = page.body.getElementsByClass("review-tbl__name-and-address").get(0)
        address.getElementsByClass("govuk-summary-list__key").text mustBe "Your address"
        address.getElementsByClass("govuk-summary-list__value").text mustBe "Street City Postcode United Kingdom"
        address.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/address/review"

      }
    }

    "not display any sole trader information" when {

      "user is during UK company journey" in {

        doc().body.getElementsByClass("review-tbl__full-name") mustBe empty

        doc().body.getElementsByClass("review-tbl__date-of-birth") mustBe empty

        doc().body.getElementsByClass("review-tbl__nino") mustBe empty
      }

      "user is during ROW organisation journey" in {

        val page = doc(isThirdCountrySubscription = true)

        page.body.getElementsByClass("review-tbl__full-name") mustBe empty

        page.body.getElementsByClass("review-tbl__date-of-birth") mustBe empty

        page.body.getElementsByClass("review-tbl__nino") mustBe empty
      }
    }

    "not display any company information" when {

      "user is during UK individual journey" in {

        val page = doc(isIndividualSubscriptionFlow = true, nameIdOrganisationDetails = None)

        page.body.getElementsByClass("review-tbl__orgname") mustBe empty

        page.body.getElementsByClass("review-tbl__date-established") mustBe empty

      }

      "user is during ROW individual journey" in {

        val page = doc(
          isIndividualSubscriptionFlow = true,
          customsId = nino,
          nameIdOrganisationDetails = None,
          isThirdCountrySubscription = true
        )

        page.body.getElementsByClass("review-tbl__orgname") mustBe empty

        page.body.getElementsByClass("review-tbl__date-established") mustBe empty

      }
    }

    "not display NINo" when {

      "UTR exists and user is during UK individual journey" in {

        val page = doc(isIndividualSubscriptionFlow = true, nameIdOrganisationDetails = None)

        page.body.getElementsByClass("review-tbl__nino") mustBe empty
      }

      "UTR exists and user is during ROW individual journey" in {
        val page =
          doc(isIndividualSubscriptionFlow = true, nameIdOrganisationDetails = None, isThirdCountrySubscription = true)

        page.body.getElementsByClass("review-tbl__nino") mustBe empty

      }
    }

    "not display contact details for ROW journeys" when {

      "user is organisation with UTR" in {

        val page = doc(isThirdCountrySubscription = true)

        page.body.getElementsByClass("review-tbl__contact-details") mustBe empty

      }

      "user is individual with UTR" in {

        val page =
          doc(isIndividualSubscriptionFlow = true, nameIdOrganisationDetails = None, isThirdCountrySubscription = true)

        page.body.getElementsByClass("review-tbl__contact-details") mustBe empty

      }

      "user is individual with NINo" in {

        val page = doc(
          isIndividualSubscriptionFlow = true,
          customsId = nino,
          nameIdOrganisationDetails = None,
          isThirdCountrySubscription = true
        )

        page.body.getElementsByClass("review-tbl__contact-details") mustBe empty
      }
    }
    "not display contact address for ROW journeys" when {

      "user is organisation with UTR" in {

        val page = doc(isThirdCountrySubscription = true)

        page.body.getElementsByClass("review-tbl__contact-address") mustBe empty

      }

      "user is individual with UTR" in {

        val page =
          doc(isIndividualSubscriptionFlow = true, nameIdOrganisationDetails = None, isThirdCountrySubscription = true)

        page.body.getElementsByClass("review-tbl__contact-address") mustBe empty

      }

      "user is individual with NINo" in {

        val page = doc(
          isIndividualSubscriptionFlow = true,
          customsId = nino,
          nameIdOrganisationDetails = None,
          isThirdCountrySubscription = true
        )

        page.body.getElementsByClass("review-tbl__contact-address") mustBe empty
      }
    }

    "display note on the bottom of the page with 'Confirm and send' button" in {

      doc().body.getElementById("declaration").text mustBe "Declaration"
      doc().body.getElementById(
        "disclaimer"
      ).text mustBe "By sending this application you confirm that the information you are providing is correct and complete."
      doc().body.getElementById("continue-button").text() mustBe "Confirm and send"
    }

    "should display individual UTR" when {

      "user has organisation UTR in cache" in {

        val individualUtr   = Utr("1111111111")
        val organisationUtr = "2222222222"

        val page = doc(
          isIndividualSubscriptionFlow = true,
          customsId = Some(individualUtr),
          nameIdOrganisationDetails = Some(NameIdOrganisationMatchModel("test", organisationUtr))
        )

        val utrRow = page.body.getElementsByClass("review-tbl__utr").get(0)
        utrRow.getElementsByClass(
          "govuk-summary-list__key"
        ).text mustBe "Self Assessment Unique Taxpayer Reference (UTR)"
        utrRow.getElementsByClass("govuk-summary-list__value").text mustBe individualUtr.id
        utrRow.getElementsByTag("a").attr("href") mustBe "/customs-enrolment-services/atar/subscribe/chooseid/review"

        page.body.toString mustNot contain(organisationUtr)
      }
    }
  }

  def doc(
    isIndividualSubscriptionFlow: Boolean = false,
    customsId: Option[CustomsId] = utr,
    orgType: Option[CdsOrganisationType] = organisationType,
    nameDobMatchModel: Option[NameDobMatchModel] = nameDobMatchModel,
    isThirdCountrySubscription: Boolean = false,
    nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = nameIdOrg,
    existingEori: Option[ExistingEori] = None,
    companyRegisteredCountry: Option[CompanyRegisteredCountry] = None,
    addressLookupParams: Option[AddressLookupParams] = None,
    language: Lang = enLan
  ): Document = {

    implicit val messages: Messages = MessagesImpl(language, messageApi)
    implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(
      FakeRequest().withSession(("selected-user-location", "third-country"))
    )
    val result = view(
      isThirdCountrySubscription,
      isIndividualSubscriptionFlow,
      orgType,
      contactDetails,
      address,
      eori,
      existingEori,
      email,
      nameIdOrganisationDetails,
      Some(NameOrganisationMatchModel("Org name")),
      nameDobMatchModel,
      dateTime,
      None,
      customsId,
      companyRegisteredCountry,
      addressLookupParams,
      contactAddressDetail,
      atarService
    )
    Jsoup.parse(contentAsString(result))
  }

}
