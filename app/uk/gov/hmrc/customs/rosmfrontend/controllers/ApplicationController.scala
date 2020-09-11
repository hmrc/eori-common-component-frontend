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
import play.api.i18n.MessagesApi
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders}
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.models.{Journey, Service}
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.migration_start
import uk.gov.hmrc.customs.rosmfrontend.views.html.{accessibility_statement, start}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationController @Inject()(
                                       override val currentApp: Application,
                                       override val authConnector: AuthConnector,
                                       mcc: MessagesControllerComponents,
                                       viewStart: start,
                                       migrationStart: migration_start,
                                       accessibilityStatementView: accessibility_statement,
                                       cdsFrontendDataCache: SessionCache,
                                       appConfig: AppConfig
                                     )(implicit override val messagesApi: MessagesApi, ec: ExecutionContext)
  extends CdsController(mcc) {

  def start: Action[AnyContent] = Action { implicit request =>
    Ok(viewStart(Journey.Register))
  }

  def startSubscription(service: Service.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request =>
      implicit loggedInUser: LoggedInUserWithEnrolments =>

        enrolledForService(loggedInUser, service) match {
          case Some(_) => Future.successful(Redirect(routes.EnrolmentAlreadyExistsController.enrolmentAlreadyExists(service)))
          case None => enrolledCds(loggedInUser) match {
            case Some(_) => Future.successful(Redirect(routes.HasExistingEoriController.displayPage(service)))
            case None => Future.successful(Redirect(routes.EmailController.form(Journey.Subscribe)))
          }
        }
  }

  def accessibilityStatement(): Action[AnyContent] = Action { implicit request =>
    Ok(accessibilityStatementView())
  }

  def logout(journey: Journey.Value): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway)) {
      journey match {
        case Journey.Register => {
          cdsFrontendDataCache.remove map { _ =>
            Redirect(appConfig.feedbackLink).withNewSession
          }
        }
        case Journey.Subscribe => {
          cdsFrontendDataCache.remove map { _ =>
            Redirect(appConfig.feedbackLinkSubscribe).withNewSession
          }
        }
      }
    } recover withAuthRecovery(request)
  }

  def keepAlive(journey: Journey.Value): Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok("Ok"))
  }
}
