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

package uk.gov.hmrc.eoricommoncomponent.frontend.models.events

import org.joda.time.DateTime
import play.api.libs.json._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.RegisterWithoutIdResponseHolder
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.CommonHeader

case class RegisterWithoutIdResult(
  status: String,
  statusText: Option[String],
  processingDate: DateTime,
  safeId: Option[String],
  arn: Option[String]
)

object RegisterWithoutIdResult extends CommonHeader {
  implicit val format = Json.format[RegisterWithoutIdResult]

  def apply(response: RegisterWithoutIdResponseHolder): RegisterWithoutIdResult = {
    val responseCommon = response.registerWithoutIDResponse.responseCommon
    val responseDetail = response.registerWithoutIDResponse.responseDetail
    RegisterWithoutIdResult(
      status = responseCommon.status,
      statusText = responseCommon.statusText,
      processingDate = responseCommon.processingDate,
      safeId = responseDetail.map(_.SAFEID),
      arn = responseDetail.flatMap(_.ARN)
    )
  }

}
