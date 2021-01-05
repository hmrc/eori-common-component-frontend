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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription

import java.time.Clock

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.CommonHeader
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{CaseClassAuditHelper, ContactDetail}

case class ContactInformation(
  personOfContact: Option[String] = None,
  sepCorrAddrIndicator: Option[Boolean] = None,
  streetAndNumber: Option[String] = None,
  city: Option[String] = None,
  postalCode: Option[String] = None,
  countryCode: Option[String] = None,
  telephoneNumber: Option[String] = None,
  faxNumber: Option[String] = None,
  emailAddress: Option[String] = None,
  emailVerificationTimestamp: Option[DateTime] = Some(
    new DateTime(Clock.systemUTC().instant.toEpochMilli, DateTimeZone.UTC)
  )
) extends CaseClassAuditHelper

object ContactInformation extends CommonHeader {
  implicit val jsonFormat = Json.format[ContactInformation]

  def getContactInformation(contactDetail: Option[ContactDetail]): Option[ContactInformation] =
    contactDetail.map { cd =>
      ContactInformation(
        personOfContact = Some(cd.contactName),
        sepCorrAddrIndicator = Some(true), //ASSUMPTION
        streetAndNumber = Some(cd.address.streetAndNumber),
        city = Some(cd.address.city),
        postalCode = cd.address.postalCode,
        countryCode = Some(cd.address.countryCode),
        telephoneNumber = cd.phone,
        emailAddress = cd.email,
        faxNumber = cd.fax
      )
    }

}
