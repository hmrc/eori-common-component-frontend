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
import play.api.i18n.MessagesApi
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders, AuthorisedFunctions}
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, AuthRedirectSupport, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service.CDS
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{accessibility_statement, start}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}


// TODO Get rid of config, env and authConnector
// TODO Get rid of AuthorisedFunctions and AuthRedirectSupport trait
// If necessary move logic to AuthAction
// This is required now for logout method
@Singleton
class ApplicationController @Inject() (
  override val config: Configuration,
  override val env: Environment,
  override val authConnector: AuthConnector,
  authorise: AuthAction,
  mcc: MessagesControllerComponents,
  viewStart: start,
  accessibilityStatementView: accessibility_statement,
  cdsFrontendDataCache: SessionCache,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  appConfig: AppConfig
)(implicit override val messagesApi: MessagesApi, ec: ExecutionContext)
    extends CdsController(mcc) with AuthorisedFunctions with EnrolmentExtractor with AuthRedirectSupport {

  def start: Action[AnyContent] = Action { implicit request =>
    Ok(viewStart(Journey.Register))
  }

  // Below method cannot be formatted by scalafmt, so scalafmt will be disabled for it
  // format: off
  def startSubscription(service: Service): Action[AnyContent] = authorise.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      {
        Future.successful(isUserEnrolledFor(loggedInUser, service)).flatMap { isUserEnrolled =>
          if (isUserEnrolled) throw SpecificEnrolmentExists(service)

          loggedInUser.groupId match {
            case Some(groupId) =>
              hasGroupIdEnrolmentTo(groupId, service).flatMap { groupIdEnrolmentExists =>
                if (groupIdEnrolmentExists) throw SpecificGroupIdEnrolmentExists(service)

                cdsEnrolmentCheck(loggedInUser, groupId, service)
              }
            case None if isUserEnrolledFor(loggedInUser, CDS) =>
              Future.successful(Redirect(routes.HasExistingEoriController.displayPage(service))) // AutoEnrolment
            case None =>
              Future.successful(Redirect(routes.EmailController.form(service, Journey.Subscribe))) // Whole journey
          }
        }
      }.recover {
        case SpecificEnrolmentExists(service) =>
          Redirect(routes.EnrolmentAlreadyExistsController.enrolmentAlreadyExists(service))
        case SpecificGroupIdEnrolmentExists(service) =>
          // Below redirect should be to page that doesn't exists yet
          Redirect(routes.EnrolmentAlreadyExistsController.enrolmentAlreadyExists(service))
      }
  }
  // format: on

  private def isUserEnrolledFor(loggedInUser: LoggedInUserWithEnrolments, service: Service): Boolean =
    enrolledForService(loggedInUser, service).isDefined

  private def hasGroupIdEnrolmentTo(groupId: String, service: Service)(implicit hc: HeaderCarrier): Future[Boolean] =
    enrolmentStoreProxyService.isEnrolmentAssociatedToGroup(GroupId(groupId), service)

  private def cdsEnrolmentCheck(loggedInUser: LoggedInUserWithEnrolments, groupId: String, serviceToEnrol: Service)(
    implicit hc: HeaderCarrier
  ): Future[Result] =
    if (isUserEnrolledFor(loggedInUser, CDS))
      Future.successful(Redirect(routes.HasExistingEoriController.displayPage(serviceToEnrol)))
    else
      hasGroupIdEnrolmentTo(groupId, CDS).map { groupIdEnrolledForCds =>
        if (groupIdEnrolledForCds)
          Redirect(
            routes.HasExistingEoriController.displayPage(serviceToEnrol)
          )                                                                           // Now autoenrolment, in future journey to ask about using EORI connected to account
        else Redirect(routes.EmailController.form(serviceToEnrol, Journey.Subscribe)) // Whole journey
      }

  def accessibilityStatement(): Action[AnyContent] = Action { implicit request =>
    Ok(accessibilityStatementView())
  }

  def logout(journey: Journey.Value): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProviders(GovernmentGateway)) {
      journey match {
        case Journey.Register =>
          cdsFrontendDataCache.remove map { _ =>
            Redirect(appConfig.feedbackLink).withNewSession
          }
        case Journey.Subscribe =>
          cdsFrontendDataCache.remove map { _ =>
            Redirect(appConfig.feedbackLinkSubscribe).withNewSession
          }
      }
    } recover withAuthRecovery(request)
  }

  def keepAlive(journey: Journey.Value): Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok("Ok"))
  }

}

case class SpecificEnrolmentExists(service: Service)
    extends Exception(s"User has already enrolment for ${service.name}")

case class SpecificGroupIdEnrolmentExists(service: Service)
    extends Exception(s"Group Id has enrolment to ${service.name}")
