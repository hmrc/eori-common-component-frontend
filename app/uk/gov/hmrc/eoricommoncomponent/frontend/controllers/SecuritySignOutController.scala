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
import play.api.{Configuration, Environment}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthRedirectSupport
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.SecuritySignOutController
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.display_sign_out

import scala.concurrent.ExecutionContext

// TODO Get rid of config, env and authConnector
// TODO Get rid of AuthorisedFunctions and AuthRedirectSupport trait
// If necessary move logic to AuthAction
// This is required now for signout method
@Singleton
class SecuritySignOutController @Inject() (
  override val config: Configuration,
  override val env: Environment,
  override val authConnector: AuthConnector,
  cdsFrontendDataCache: SessionCache,
  displaySignOutView: display_sign_out,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with AuthorisedFunctions with AuthRedirectSupport {

  def displayPage(journey: Journey.Value): Action[AnyContent] = Action { implicit request =>
    Ok(displaySignOutView(journey))
  }

  def signOut(journey: Journey.Value): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway)) {
      cdsFrontendDataCache.remove map { _ =>
        Redirect(SecuritySignOutController.displayPage(journey).url).withNewSession
      }
    } recover withAuthRecovery(request)
  }
}
