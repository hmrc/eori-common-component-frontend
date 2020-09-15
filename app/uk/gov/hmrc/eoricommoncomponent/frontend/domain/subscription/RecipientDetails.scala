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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey

case class RecipientDetails(
                             journey: Journey.Value,
                             service: String,
                             recipientEmailAddress: String,
                             recipientFullName: String,
                             orgName: Option[String],
                             completionDate: Option[String] = None
                           )

object RecipientDetails {
  implicit val jsonFormat: OFormat[RecipientDetails] = Json.format[RecipientDetails]

  def apply(journey: Journey.Value, recipientEmailAddress: String, recipientFullName: String, orgName: Option[String], completionDate: Option[String]): RecipientDetails =
    new RecipientDetails(journey, "ATaR", recipientEmailAddress, recipientFullName, orgName, completionDate)

  def apply(journey: Journey.Value, contactDetails: ContactDetails): RecipientDetails =
    RecipientDetails(
      journey,
      "ATaR",
      contactDetails.emailAddress,
      contactDetails.fullName,
      orgName = None,
      completionDate = None
    )
}


