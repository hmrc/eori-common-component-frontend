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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.eori_already_used

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EoriAlreadyUsedController @Inject() (
  authAction: AuthAction,
  cache: SessionCache,
  mcc: MessagesControllerComponents,
  eoriAlreadyUsedView: eori_already_used
)(implicit val ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayPage(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => (_: LoggedInUserWithEnrolments) =>
      Future.successful(Ok(eoriAlreadyUsedView(service)))
  }

  def signInToAnotherAccount(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserAction {
      implicit request => (_: LoggedInUserWithEnrolments) =>
        cache.remove map { _ =>
          Redirect(routes.ApplicationController.startSubscription(service)).withNewSession
        }
    }

}
