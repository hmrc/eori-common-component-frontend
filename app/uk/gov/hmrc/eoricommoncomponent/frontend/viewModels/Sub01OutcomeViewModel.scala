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

package uk.gov.hmrc.eoricommoncomponent.frontend.viewModels

import play.api.i18n.Messages
import play.api.mvc.Request
import uk.gov.hmrc.eoricommoncomponent.frontend.views.ServiceName.{longName, shortName}

object Sub01OutcomeViewModel {

  def headingForRejected(name: Option[String])(implicit messages: Messages, request: Request[_]): String = name match {
    case Some(user) => messages(s"cds.sub01.outcome.rejected.subscribe.heading", longName, user)
    case None       => messages(s"cds.sub01.outcome.rejected.subscribe.heading-noname", longName)
  }

  def headingForProcessing(name: Option[String])(implicit messages: Messages, request: Request[_]): String = name match {
    case Some(org) => messages("cds.sub01.outcome.processing.heading", shortName, org)
    case None      => messages("cds.sub01.outcome.processing.heading-noname", shortName)

  }

}
