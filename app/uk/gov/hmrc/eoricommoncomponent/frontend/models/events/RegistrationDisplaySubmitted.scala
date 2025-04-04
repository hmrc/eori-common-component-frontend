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

package uk.gov.hmrc.eoricommoncomponent.frontend.models.events

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.registration.RegistrationDisplayRequestHolder

case class RegistrationDisplaySubmitted(safeId: Option[String])

object RegistrationDisplaySubmitted {
  implicit val format: OFormat[RegistrationDisplaySubmitted] = Json.format[RegistrationDisplaySubmitted]

  def apply(request: RegistrationDisplayRequestHolder): RegistrationDisplaySubmitted =
    RegistrationDisplaySubmitted(safeId =
      request.registrationDisplayRequest.requestCommon.requestParameters.find(_.paramName == "ID_Value").map(
        _.paramValue
      )
    )

}
