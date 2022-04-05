/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{
  AuthAction,
  EnrolmentExtractor,
  GroupEnrolmentExtractor
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{EnrolmentService, MissingEnrolmentException}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{eori_enrol_success, has_existing_eori}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HasExistingEoriController @Inject() (
  authAction: AuthAction,
  hasExistingEoriView: has_existing_eori,
  enrolSuccessView: eori_enrol_success,
  mcc: MessagesControllerComponents,
  enrolmentService: EnrolmentService,
  groupEnrolment: GroupEnrolmentExtractor,
  cache: SessionCache
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  private val logger = Logger(this.getClass)

  def displayPage(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      existingEoriToUse.map { eori =>
        Ok(hasExistingEoriView(service, eori.id))
      }
  }

  def enrol(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      groupEnrolment.hasGroupIdEnrolmentTo(user.groupId.getOrElse(throw MissingGroupId()), service).flatMap {
        groupIdEnrolmentExists =>
          if (groupIdEnrolmentExists)
            Future.successful(Redirect(routes.EnrolmentAlreadyExistsController.enrolmentAlreadyExistsForGroup(service)))
          else
            existingEoriToUse.flatMap { eori =>
              enrolmentService.enrolWithExistingEnrolment(eori, service).map {
                case NO_CONTENT => Redirect(routes.HasExistingEoriController.enrolSuccess(service))
                case status     => throw FailedEnrolmentException(status)
              } recover {
                case e: MissingEnrolmentException =>
                  logger.info(s"EnrolWithExistingEnrolment : ${e.getMessage}")
                  Redirect(routes.EmailController.form(service))
              }
            }
      }
    }

  // Note: permitted for user with service enrolment
  def enrolSuccess(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithServiceAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      existingEoriToUse.map { eori =>
        Ok(enrolSuccessView(eori.id, service))
      }
  }

  private def existingEoriToUse(implicit
    loggedInUser: LoggedInUserWithEnrolments,
    request: Request[_]
  ): Future[ExistingEori] =
    enrolledForService(loggedInUser, Service.cds) match {
      case Some(eori) => Future.successful(eori)
      case _          => checkOtherEnrollments

    }

  private def checkOtherEnrollments(implicit loggedInUser: LoggedInUserWithEnrolments, request: Request[_]) =
    enrolledForOtherServices(loggedInUser) match {
      case Some(eori) => Future.successful(eori)
      case _ =>
        cache.groupEnrolment.map { enrolment =>
          ExistingEori(enrolment.eori.getOrElse(throw DataUnavailableException("No EORI found")), enrolment.service)
        }
    }

}

case class FailedEnrolmentException(status: Int) extends Exception(s"Enrolment failed with status $status")
