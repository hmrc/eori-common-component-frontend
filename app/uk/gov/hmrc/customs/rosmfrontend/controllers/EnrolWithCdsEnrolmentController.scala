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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.customs.rosmfrontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.customs.rosmfrontend.services.subscription.EnrolmentService
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.ExecutionContext

@Singleton
class EnrolWithCdsEnrolmentController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  enrolmentService: EnrolmentService,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext) extends CdsController(mcc) {

  def enrol(serviceName: String): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => user: LoggedInUserWithEnrolments =>
      // Instead of successful enrolment message we will redirect to confirmation screen
      enrolmentService.enrolWithExistingCDSEnrolment(user, serviceName).map {
        case NO_CONTENT => Ok("Enrolment successful")
        case _ => BadRequest("Enrolment failed")
      }
    }
}

case class IncorrectServiceException(msg: String = "Incorrect service") extends Exception(msg)
