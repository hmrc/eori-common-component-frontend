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
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service.CDS
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{eori_enrol_success, has_existing_eori}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HasExistingEoriController @Inject() (
  authAction: AuthAction,
  hasExistingEoriView: has_existing_eori,
  enrolSuccessView: eori_enrol_success,
  mcc: MessagesControllerComponents,
  enrolmentService: EnrolmentService,
  cache: SessionCache
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  def displayPage(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      existingEoriToUse.map { eori =>
        Ok(hasExistingEoriView(service, eori))
      }
  }

  def enrol(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      existingEoriToUse.flatMap { eori =>
        enrolmentService.enrolWithExistingCDSEnrolment(eori, service).map {
          case NO_CONTENT => Redirect(routes.HasExistingEoriController.enrolSuccess(service))
          case status     => throw FailedEnrolmentException(status)
        }
      }
    }

  // Note: permitted for user with service enrolment
  def enrolSuccess(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithServiceAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      existingEoriToUse.map { eori =>
        Ok(enrolSuccessView(eori, service))
      }
  }

  private def existingEoriToUse(implicit loggedInUser: LoggedInUserWithEnrolments, hc: HeaderCarrier): Future[String] =
    enrolledForService(loggedInUser, CDS) match {
      case Some(eori) => Future.successful(eori.id)
      case _ =>
        cache.groupEnrolment.map(_.eori.getOrElse(throw new IllegalStateException("No EORI found")))
    }

}

case class FailedEnrolmentException(status: Int) extends Exception(s"Enrolment failed with status $status")
