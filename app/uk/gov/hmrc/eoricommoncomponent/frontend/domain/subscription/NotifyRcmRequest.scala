/*
 * Copyright 2025 HM Revenue & Customs
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
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service

import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneId}

case class NotifyRcmRequest(timestamp: String, eori: String, name: String, email: String, serviceName: String)

object NotifyRcmRequest {
  val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d-MMM-yyyy HH:mm:ss")

  implicit val jsonFormat: OFormat[NotifyRcmRequest] = Json.format[NotifyRcmRequest]

  def apply(eori: String, name: String, email: String, service: Service): NotifyRcmRequest =
    new NotifyRcmRequest(
      dateTimeFormat.format(LocalDateTime.now(ZoneId.of("Europe/London"))),
      eori,
      name,
      email,
      service.friendlyName
    )

}
