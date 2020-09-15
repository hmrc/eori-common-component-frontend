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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{LoggedInUserWithEnrolments, Sub02Outcome}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.eori_number_text_download

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EoriTextDownloadController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  cdsFrontendDataCache: SessionCache,
  eoriNumberTextDownloadView: eori_number_text_download,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def download(): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        Some(eori) <- cdsFrontendDataCache.sub02Outcome.map(_.eori)
        name       <- cdsFrontendDataCache.sub02Outcome.map(_.fullName.trim)
        processedDate <- cdsFrontendDataCache.sub02Outcome
          .map(_.processedDate)
      } yield Ok(eoriNumberTextDownloadView(eori, name, processedDate))
        .as("plain/text")
        .withHeaders(CONTENT_DISPOSITION -> "attachment; filename=EORI-number.txt")
  }

}
