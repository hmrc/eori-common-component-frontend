/*
 * Copyright 2026 HM Revenue & Customs
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
import uk.gov.hmrc.eoricommoncomponent.frontend.services.ExistingEoriService

import javax.inject.{Inject, Singleton}

@Singleton
class HasExistingEoriController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  existingEoriService: ExistingEoriService
) extends CdsController(mcc) {

  def displayPage(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      existingEoriService.onDisplay(service)
  }

  def enrol(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      existingEoriService.onEnrol(service)
    }

  // Note: permitted for user with service enrolment
  def enrolSuccess(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithServiceAction {
    implicit request => implicit user: LoggedInUserWithEnrolments =>
      existingEoriService.onEnrolSuccess(service)
  }

}
