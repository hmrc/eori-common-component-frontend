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
import play.api.i18n.MessagesApi
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EnrolmentResponse, GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service.CDS
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{accessibility_statement, start}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationController @Inject() (
  authorise: AuthAction,
  mcc: MessagesControllerComponents,
  viewStart: start,
  accessibilityStatementView: accessibility_statement,
  cache: SessionCache,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  appConfig: AppConfig
)(implicit override val messagesApi: MessagesApi, ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  def start: Action[AnyContent] = Action { implicit request =>
    Ok(viewStart(Journey.Register))
  }

  // Below method cannot be formatted by scalafmt, so scalafmt will be disabled for it
  // format: off
  def startSubscription(service: Service): Action[AnyContent] = authorise.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      {
          loggedInUser.groupId match {
            case Some(groupId) =>
              hasGroupIdEnrolmentTo(groupId, service).flatMap { groupIdEnrolmentExists =>
                if (groupIdEnrolmentExists) throw SpecificGroupIdEnrolmentExists(service)

                cdsEnrolmentCheck(loggedInUser, groupId, service)
              }
            case _ =>
              throw MissingGroupId()
          }
       
      }.recover {
        case SpecificGroupIdEnrolmentExists(service) =>
          Redirect(routes.EnrolmentAlreadyExistsController.enrolmentAlreadyExistsForGroup(service))
      }
  }
  // format: on

  private def isUserEnrolledFor(loggedInUser: LoggedInUserWithEnrolments, service: Service): Boolean =
    enrolledForService(loggedInUser, service).isDefined

  private def hasGroupIdEnrolmentTo(groupId: String, service: Service)(implicit hc: HeaderCarrier): Future[Boolean] =
    groupIdEnrolmentTo(groupId, service).map(_.isDefined)

  private def groupIdEnrolmentTo(groupId: String, service: Service)(implicit
    hc: HeaderCarrier
  ): Future[Option[EnrolmentResponse]] =
    enrolmentStoreProxyService.enrolmentForGroup(GroupId(groupId), service)

  private def cdsEnrolmentCheck(loggedInUser: LoggedInUserWithEnrolments, groupId: String, serviceToEnrol: Service)(
    implicit hc: HeaderCarrier
  ): Future[Result] =
    if (isUserEnrolledFor(loggedInUser, CDS))
      Future.successful(Redirect(routes.HasExistingEoriController.displayPage(serviceToEnrol)))
    else
      groupIdEnrolmentTo(groupId, CDS).flatMap {
        case Some(groupEnrolment) if groupEnrolment.eori.isDefined =>
          cache.saveGroupEnrolment(groupEnrolment).map { _ =>
            Redirect(routes.HasExistingEoriController.displayPage(serviceToEnrol)) // AutoEnrolment
          }
        case _ =>
          Future.successful(Redirect(routes.EmailController.form(serviceToEnrol, Journey.Subscribe))) // Whole journey
      }

  // For register journey we need to pass service here and to the view
  // Message accessibility-statement.reporting-problems.paragraph contains inlined link to the feedback
  // This link need to be dynamic based on the user journey
  def accessibilityStatement(): Action[AnyContent] = Action { implicit request =>
    Ok(accessibilityStatementView())
  }

  def logout(service: Service, journey: Journey.Value): Action[AnyContent] =
    authorise.ggAuthorisedUserAction {
      implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
        cache.remove map { _ =>
          Redirect(appConfig.feedbackUrl(service, journey)).withNewSession
        }
    }

  def keepAlive(service: Service, journey: Journey.Value): Action[AnyContent] = Action.async { implicit request =>
    Future.successful(Ok("Ok"))
  }

}

case class SpecificGroupIdEnrolmentExists(service: Service)
    extends Exception(s"Group Id has enrolment to ${service.code}")

case class MissingGroupId() extends Exception(s"User doesn't have groupId")
