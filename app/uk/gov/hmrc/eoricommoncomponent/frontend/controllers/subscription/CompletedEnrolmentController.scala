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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription

import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.eori_enrol_success

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class CompletedEnrolmentController @Inject() (
  authAction: AuthAction,
  sessionCache: SessionCache,
  mcc: MessagesControllerComponents,
  enrolSuccessView: eori_enrol_success
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor with Logging {

  def enrolSuccess(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithServiceAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      activatedEnrolmentForService(loggedInUser, service) match {
        case Some(_) => sessionCache.remove.map(_ => Ok(enrolSuccessView(service)))
        case _       =>
          // $COVERAGE-OFF$Loggers
          logger.error("CompletedEnrolmentController - enrolSuccess: No enrolment found for the user.")
          // $COVERAGE-ON
          throw DataUnavailableException("No enrolment found for the user")
      }
  }

}
