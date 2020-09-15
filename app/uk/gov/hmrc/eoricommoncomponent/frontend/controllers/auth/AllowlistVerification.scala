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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth

import play.api.Application
import play.api.mvc.Request

trait AllowlistVerification {
  // TODO Get rid of this application - only configuration is used, this def can be a val config: Configuration
  def currentApp: Application

  private lazy val allowlistEnabled: Boolean = currentApp.configuration.get[Boolean]("allowlistEnabled")
  private lazy val allowlist: Array[String]  = currentApp.configuration.get[String]("allowlist").split(',').map(_.trim)

  def isAllowlisted(email: Option[String])(implicit request: Request[_]): Boolean =
    if (allowlistEnabled) {
      val alreadyAllowlisted = request.session.get("allowlisted").contains("true")
      val permittedEmail     = email.exists(e => allowlist.exists(_.equalsIgnoreCase(e)))
      alreadyAllowlisted || permittedEmail
    } else
      true

}
