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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailForm.emailForm
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.{EmailStatus, EmailViewModel}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourEmailController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  whatIsYourEmailView: what_is_your_email,
  save4LaterService: Save4LaterService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def populateView(email: Option[String], service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent]
  ): Future[Result] = {
    lazy val form = email.map(EmailViewModel).fold(emailForm) {
      emailForm.fill
    }
    Future.successful(Ok(whatIsYourEmailView(emailForm = form, service, subscribeJourney)))
  }

  def createForm(service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.enrolledUserClearingCacheOnCompletionAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        populateView(None, service, subscribeJourney)
    }

  def submit(service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.enrolledUserClearingCacheOnCompletionAction {
      implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
        emailForm.bindFromRequest().fold(
          formWithErrors =>
            Future.successful(BadRequest(whatIsYourEmailView(emailForm = formWithErrors, service, subscribeJourney))),
          formData => submitNewDetails(GroupId(userWithEnrolments.groupId), formData, service, subscribeJourney)
        )
    }

  private def submitNewDetails(
    groupId: GroupId,
    formData: EmailViewModel,
    service: Service,
    subscribeJourney: SubscribeJourney
  )(implicit hc: HeaderCarrier): Future[Result] =
    save4LaterService
      .saveEmailForService(EmailStatus(Some(formData.email)))(service, subscribeJourney, groupId)
      .flatMap(_ => Future.successful(Redirect(routes.CheckYourEmailController.createForm(service, subscribeJourney))))

}
