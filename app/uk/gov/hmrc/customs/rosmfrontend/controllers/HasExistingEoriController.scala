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

package uk.gov.hmrc.customs.rosmfrontend.controllers

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.domain._
import uk.gov.hmrc.customs.rosmfrontend.models.Service
import uk.gov.hmrc.customs.rosmfrontend.views.html.has_existing_eori

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HasExistingEoriController @Inject()(
                                           override val currentApp: Application,
                                           override val authConnector: AuthConnector,
                                           hasExistingEoriView: has_existing_eori,
                                           mcc: MessagesControllerComponents
                                         )(implicit ec: ExecutionContext)
  extends CdsController(mcc) {

  def displayPage(service: Service.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request =>
      implicit loggedInUser: LoggedInUserWithEnrolments =>
        Future.successful(Ok(hasExistingEoriView(service, existingEori.id)))
  }

  private def existingEori(implicit loggedInUser: LoggedInUserWithEnrolments) = {
    enrolledCds(loggedInUser).getOrElse(throw new IllegalStateException("No EORI found in enrolments"))
  }
}
