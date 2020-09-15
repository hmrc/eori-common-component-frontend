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

package unit.models

import base.UnitSpec
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription._

import scala.collection.Map

class CaseObjectAuditHelperSubscriptionSpec extends UnitSpec {

  import play.api.libs.json.Json

  val establishmentAddressFromJson =
    Json.parse("""{
                 |        "streetAndNumber": "86 Mysore Road",
                 |        "city": "London",
                 |        "postalCode": "SW11 5RZ",
                 |        "countryCode": "GB"
                 |      }""".stripMargin).as[EstablishmentAddress]

  "EstablishmentAddress Object" should {
    "create audit map" in {
      val establishmentAddressMap = establishmentAddressFromJson.toMap()
      establishmentAddressMap shouldBe Map(
        "streetAndNumber" -> "86 Mysore Road",
        "city" -> "London",
        "postalCode" -> "SW11 5RZ",
        "countryCode" -> "GB"
      )
    }
  }

  val contactInformationFromJson =
    Json.parse("""  {
                 |    "personOfContact": "Pepper Pott",
                 |    "sepCorrAddrIndicator": true,
                 |    "streetAndNumber": "2nd floor, Alexander House",
                 |    "city": "Southend-on-sea",
                 |    "postalCode": "SS99 1AA",
                 |    "countryCode": "GB",
                 |    "telephoneNumber": "01702 215001",
                 |    "faxNumber": "01702 215002",
                 |    "emailAddress": "Pepper.p@example.com"
                 |  }""".stripMargin).as[ContactInformation]

  "ContactInformation Object" should {
    "create audit map" in {
      val contactInformationMap = contactInformationFromJson.toMap()
      contactInformationMap shouldBe Map(
        "city" -> "Southend-on-sea",
        "telephoneNumber" -> "01702 215001",
        "personOfContact" -> "Pepper Pott",
        "emailAddress" -> "Pepper.p@example.com",
        "postalCode" -> "SS99 1AA",
        "countryCode" -> "GB",
        "sepCorrAddrIndicator" -> "true",
        "streetAndNumber" -> "2nd floor, Alexander House",
        "faxNumber" -> "01702 215002"
      )
    }
  }

  val vatIdsFromJson =
    Json.parse("""[{
                 |        "countryCode": "GB",
                 |        "vatID": "12164568990"
                 |      }]""".stripMargin).as[Seq[VatId]]

  "VatIds Object" should {
    "create audit map" in {
      val vatIds = vatIdsFromJson.map(_.toMap())
      vatIds shouldBe Seq(Map("countryCode" -> "GB", "vatID" -> "12164568990"))
    }
  }

  val subscriptionCreateRequestFromJson =
    Json.parse("""{
                 |    "requestCommon": {
                 |      "receiptDate": "2001-12-17T09:30:47.000Z",
                 |      "acknowledgementReference":
                 |      "01234567890123456789012345678901",
                 |      "regime":"CDS" ,
                 |      "PID": "123456789123",
                 |      "originatingSystem": "MDTP-Internal"
                 |    },
                 |    "requestDetail": {
                 |    "SAFE" : "SAFEID",
                 |      "EORINo": "GBE9EEK910BCKEYAX",
                 |      "CDSFullName": "Tony Stark",
                 |      "CDSEstablishmentAddress": {
                 |        "streetAndNumber": "86 Mysore Road",
                 |        "city": "London",
                 |        "postalCode": "SW11 5RZ",
                 |        "countryCode": "GB"
                 |      },
                 |      "establishmentInTheCustomsTerritoryOfTheUnion": "0",
                 |      "typeOfLegalEntity": "0001",
                 |      "contactInformation": {
                 |        "personOfContact": "Pepper Pott",
                 |        "sepCorrAddrIndicator": true,
                 |        "streetAndNumber": "2nd floor, Alexander House",
                 |        "city": "Southend-on-sea",
                 |        "postalCode": "SS99 1AA",
                 |        "countryCode": "GB",
                 |        "telephoneNumber": "01702 215001",
                 |        "faxNumber": "01702 215002",
                 |        "emailAddress": "Pepper.p@example.com"
                 |      },
                 |      "vatIDs": [{
                 |        "countryCode": "GB",
                 |        "vatID": "12164568990"
                 |      }],
                 |      "thirdCountryUniqueIdentificationNumber": ["321"],
                 |      "consentToDisclosureOfPersonalData": "1",
                 |      "shortName": " Robinson",
                 |      "dateOfEstablishment": "1963-04-01",
                 |      "typeOfPerson": "1",
                 |      "principalEconomicActivity": "2000"
                 |    }
                 |}""".stripMargin).as[SubscriptionCreateRequest]

  "SubscriptionCreateRequest Object" should {
    "create audit map" in {
      val subscriptionCreateRequestMap = subscriptionCreateRequestFromJson.keyValueMap()
      subscriptionCreateRequestMap shouldBe Map(
        "SAFE" -> "SAFEID",
        "receiptDate" -> "2001-12-17T09:30:47.000Z",
        "contactInformation.countryCode" -> "GB",
        "contactInformation.streetAndNumber" -> "2nd floor, Alexander House",
        "contactInformation.city" -> "Southend-on-sea",
        "typeOfPerson" -> "1",
        "contactInformation.postalCode" -> "SS99 1AA",
        "regime" -> "CDS",
        "address.postalCode" -> "SW11 5RZ",
        "vatIDs.countryCode" -> "GB",
        "contactInformation.personOfContact" -> "Pepper Pott",
        "address.city" -> "London",
        "contactInformation.emailAddress" -> "Pepper.p@example.com",
        "address.countryCode" -> "GB",
        "EORINo" -> "GBE9EEK910BCKEYAX",
        "address.streetAndNumber" -> "86 Mysore Road",
        "principalEconomicActivity" -> "2000",
        "consentToDisclosureOfPersonalData" -> "1",
        "vatIDs.vatID" -> "12164568990",
        "originatingSystem" -> "MDTP-Internal",
        "contactInformation.faxNumber" -> "01702 215002",
        "CDSFullName" -> "Tony Stark",
        "typeOfLegalEntity" -> "0001",
        "acknowledgementReference" -> "01234567890123456789012345678901",
        "contactInformation.telephoneNumber" -> "01702 215001",
        "shortName" -> " Robinson",
        "contactInformation.sepCorrAddrIndicator" -> "true",
        "establishmentInTheCustomsTerritoryOfTheUnion" -> "0"
      )
    }
  }

  val subscriptionCreateResponseFromeJson =
    Json.parse(s"""
         |{
         |    "responseCommon": {
         |      "status": "OK",
         |      "processingDate": "2001-12-17T09:30:47.000Z",
         |      "returnParameters": [
         |        {
         |          "paramName": "ETMPFORMBUNDLENUMBER",
         |          "paramValue": "Form-Bundle-Id"
         |        },
         |        {
         |          "paramName": "POSITION",
         |          "paramValue": "LINK"
         |        }
         |      ]
         |    },
         |    "responseDetail": {
         |      "EORINo": "ZZZ1ZZZZ23ZZZZZZZ"
         |    }
         |}
      """.stripMargin).as[SubscriptionCreateResponse]

  "SubscriptionCreateResponse Object" should {
    "create audit map" in {
      val subscriptionCreateResponseMap = subscriptionCreateResponseFromeJson.keyValueMap()
      subscriptionCreateResponseMap shouldBe Map(
        "processingDate" -> "2001-12-17T09:30:47.000Z",
        "EORINo" -> "ZZZ1ZZZZ23ZZZZZZZ",
        "paramValue.0" -> "Form-Bundle-Id",
        "status" -> "OK",
        "paramName.1" -> "POSITION",
        "paramValue.1" -> "LINK",
        "paramName.0" -> "ETMPFORMBUNDLENUMBER"
      )
    }
  }
}
