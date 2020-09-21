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
import play.api.i18n.MessagesApi
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{AuthConnector, AuthProviders}
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service.CDS
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{accessibility_statement, start}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  mcc: MessagesControllerComponents,
  viewStart: start,
  accessibilityStatementView: accessibility_statement,
  cdsFrontendDataCache: SessionCache,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  appConfig: AppConfig
)(implicit override val messagesApi: MessagesApi, ec: ExecutionContext)
    extends CdsController(mcc) {

  def start: Action[AnyContent] = Action { implicit request =>
    Ok(viewStart(Journey.Register))
  }

  def startSubscription(service: Service): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      try {
        if (isUserEnrolledFor(loggedInUser, service)) throw SpecificEnrolmentExists(service)

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
      } catch {
        case SpecificEnrolmentExists(service) =>
          Future.successful(Redirect(routes.EnrolmentAlreadyExistsController.enrolmentAlreadyExists(service)))
        case SpecificGroupIdEnrolmentExists(service) =>
          // Below redirect should be to page that doesn't exists yet
          Future.successful(Redirect(routes.EnrolmentAlreadyExistsController.enrolmentAlreadyExists(service)))
      }
  }

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
