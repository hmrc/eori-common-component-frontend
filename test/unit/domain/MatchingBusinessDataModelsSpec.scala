/*
 * Copyright 2021 HM Revenue & Customs
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

package unit.domain

import base.UnitSpec
import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.SixLineAddressMatchModel
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.matching._

class MatchingBusinessDataModelsSpec extends UnitSpec {

  private val processingDate = (new DateTime).withDate(2016, 1, 8).withTime(9, 0, 0, 0)
  private val status         = "OK"
  private val returnParams   = Some(List(MessagingServiceParam("SAP_NUMBER", "0000000000")))

  private val safeId  = "XE0000123456789"
  private val address = Address("Line 1", Some("City Name"), Some("addLine3"), Some("addLine4"), Some("SE28 1AA"), "GB")

  private val contactResponse =
    ContactResponse(Some("011 5666 4444"), Some("011 5666, 4445"), Some("011 5666 4556"), Some("john.doe@example.com"))

  private val organisationResponse = Some(OrganisationResponse("orgName", None, Some(false), Some("LLP")))
  private val individualResponse   = Some(IndividualResponse("John", Some("A"), "Doe", Some("01/01/1900")))

  private val countryCodesChannelIslands = Set(
    "AD",
    "AE",
    "AF",
    "AG",
    "AI",
    "AL",
    "AM",
    "AN",
    "AO",
    "AQ",
    "AR",
    "AS",
    "AT",
    "AU",
    "AW",
    "AX",
    "AZ",
    "BA",
    "BB",
    "BD",
    "BE",
    "BF",
    "BG",
    "BH",
    "BI",
    "BJ",
    "BM",
    "BN",
    "BO",
    "BQ",
    "BR",
    "BS",
    "BT",
    "BV",
    "BW",
    "BY",
    "BZ",
    "CA",
    "CC",
    "CD",
    "CF",
    "CG",
    "CH",
    "CI",
    "CK",
    "CL",
    "CM",
    "CN",
    "CO",
    "CR",
    "CS",
    "CU",
    "CV",
    "CW",
    "CX",
    "CY",
    "CZ",
    "DE",
    "DJ",
    "DK",
    "DM",
    "DO",
    "DZ",
    "EC",
    "EE",
    "EG",
    "EH",
    "ER",
    "ES",
    "ET",
    "EU",
    "FI",
    "FJ",
    "FK",
    "FM",
    "FO",
    "FR",
    "GA",
    "GD",
    "GE",
    "GF",
    "GH",
    "GI",
    "GL",
    "GM",
    "GN",
    "GP",
    "GQ",
    "GR",
    "GS",
    "GT",
    "GU",
    "GW",
    "GY",
    "HK",
    "HM",
    "HN",
    "HR",
    "HT",
    "HU",
    "ID",
    "IE",
    "IL",
    "IN",
    "IO",
    "IQ",
    "IR",
    "IS",
    "IT",
    "JM",
    "JO",
    "JP",
    "KE",
    "KG",
    "KH",
    "KI",
    "KM",
    "KN",
    "KP",
    "KR",
    "KW",
    "KY",
    "KZ",
    "LA",
    "LB",
    "LC",
    "LI",
    "LK",
    "LR",
    "LS",
    "LT",
    "LU",
    "LV",
    "LY",
    "MA",
    "MC",
    "MD",
    "ME",
    "MF",
    "MG",
    "MH",
    "MK",
    "ML",
    "MM",
    "MN",
    "MO",
    "MP",
    "MQ",
    "MR",
    "MS",
    "MT",
    "MU",
    "MV",
    "MW",
    "MX",
    "MY",
    "MZ",
    "NA",
    "NC",
    "NE",
    "NF",
    "NG",
    "NI",
    "NL",
    "NO",
    "NP",
    "NR",
    "NT",
    "NU",
    "NZ",
    "OM",
    "PA",
    "PE",
    "PF",
    "PG",
    "PH",
    "PK",
    "PL",
    "PM",
    "PN",
    "PR",
    "PS",
    "PT",
    "PW",
    "PY",
    "QA",
    "RE",
    "RO",
    "RS",
    "RU",
    "RW",
    "SA",
    "SB",
    "SC",
    "SD",
    "SE",
    "SG",
    "SH",
    "SI",
    "SJ",
    "SK",
    "SL",
    "SM",
    "SN",
    "SO",
    "SR",
    "SS",
    "ST",
    "SV",
    "SX",
    "SY",
    "SZ",
    "TC",
    "TD",
    "TF",
    "TG",
    "TH",
    "TJ",
    "TK",
    "TL",
    "TM",
    "TN",
    "TO",
    "TP",
    "TR",
    "TT",
    "TV",
    "TW",
    "TZ",
    "UA",
    "UG",
    "UM",
    "UN",
    "US",
    "UY",
    "UZ",
    "VA",
    "VC",
    "VE",
    "VG",
    "VI",
    "VN",
    "VU",
    "WF",
    "WS",
    "YE",
    "YT",
    "ZA",
    "ZM",
    "ZW"
  )

  "matchingModels OrganisationAddress" should {
    "throw an exception if a postcode is missing and the country code is Jersey" in {
      the[IllegalArgumentException] thrownBy {
        SixLineAddressMatchModel("AddL1", None, "AddLine3", None, None, "JE")
      } should have message "requirement failed: Postcode required for country code: JE"
    }

    "throw an exception if a postcode is missing and the country code is Guernsey" in {
      the[IllegalArgumentException] thrownBy {
        SixLineAddressMatchModel("AddL1", None, "AddLine3", None, Some(""), "GG")
      } should have message "requirement failed: Postcode required for country code: GG"
    }

    "not throw an exception if a postcode is provided for Jersey" in {
      noException should be thrownBy SixLineAddressMatchModel("AddL1", None, "AddLine3", None, Some("POSTCODE"), "JE")
    }

    "not throw an exception if a postcode is provided for Guernsey" in {
      noException should be thrownBy SixLineAddressMatchModel("AddL1", None, "AddLine3", None, Some("POSTCODE"), "GG")
    }

    "not throw an exception if a postcode is not provided for any other ROW country code" in {
      countryCodesChannelIslands.foreach { cc =>
        noException should be thrownBy SixLineAddressMatchModel("AddL1", None, "AddLine3", None, Some("POSTCODE"), cc)
      }
    }

  }
}
