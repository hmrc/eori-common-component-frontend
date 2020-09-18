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

package uk.gov.hmrc.eoricommoncomponent.frontend.views

import play.api.i18n.Messages
import play.api.mvc.Request
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service

object ServiceName {

  private val default = "cds.service.friendly.name.default"

  def serviceName(service: Service)(implicit messages: Messages): String = {
    val key = s"cds.service.friendly.name.${service.name}"
    if (messages.isDefinedAt(key)) messages(key) else messages(default)
  }

  def serviceName(implicit messages: Messages, request: Request[_]): String = {
    val path = request.path
    Service.supportedServices.find(service => path.contains(s"/${service.name}/")).map(
      service => serviceName(service)
    ).getOrElse(messages(default))
  }

}
