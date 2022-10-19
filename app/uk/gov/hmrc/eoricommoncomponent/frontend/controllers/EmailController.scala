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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes.{
  CheckYourEmailController,
  WhatIsYourEmailController
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, InternalId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  Error,
  UpdateEmailError,
  UpdateError,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.{Save4LaterService, UserGroupIdSubscriptionStatusCheckService}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{
  email_error_template,
  enrolment_pending_against_group_id,
  enrolment_pending_for_user
}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailController @Inject() (
  authAction: AuthAction,
  emailVerificationService: EmailVerificationService,
  sessionCache: SessionCache,
  mcc: MessagesControllerComponents,
  save4LaterService: Save4LaterService,
  updateVerifiedEmailService: UpdateVerifiedEmailService,
  userGroupIdSubscriptionStatusCheckService: UserGroupIdSubscriptionStatusCheckService,
  enrolmentPendingForUser: enrolment_pending_for_user,
  enrolmentPendingAgainstGroupId: enrolment_pending_against_group_id,
  emailErrorPage: email_error_template
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  private val logger = Logger(this.getClass)

  private def userIsInProcess(
    service: Service
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    save4LaterService
      .fetchProcessingService(GroupId(user.groupId))
      .map(processingService => Ok(enrolmentPendingForUser(service, processingService)))

  private def otherUserWithinGroupIsInProcess(
    service: Service
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    save4LaterService
      .fetchProcessingService(GroupId(user.groupId))
      .map(processingService => Ok(enrolmentPendingAgainstGroupId(service, processingService)))

  private def continue(service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent],
    user: LoggedInUserWithEnrolments
  ): Future[Result] =
    save4LaterService.fetchEmailForService(service, subscribeJourney, GroupId(user.groupId)) flatMap {
      _.fold {
        // $COVERAGE-OFF$Loggers
        logger.info(s"emailStatus cache none ${user.internalId}")
        Future.successful(Redirect(WhatIsYourEmailController.createForm(service, subscribeJourney)))
      } { cachedEmailStatus =>
        cachedEmailStatus.email match {
          case Some(email) =>
            if (cachedEmailStatus.isVerified)
              sessionCache.saveEmail(email) map { _ =>
                Redirect(CheckYourEmailController.emailConfirmed(service, subscribeJourney))
              }
            else
              checkWithEmailService(email, cachedEmailStatus, service, subscribeJourney)
          case None => Future.successful(Redirect(WhatIsYourEmailController.createForm(service, subscribeJourney)))
        }
      }
    }

  def form(implicit service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      userGroupIdSubscriptionStatusCheckService
        .checksToProceed(GroupId(user.groupId), InternalId(user.internalId))(continue(service, subscribeJourney))(
          userIsInProcess(service)
        )(otherUserWithinGroupIsInProcess(service))
    }

  private def checkWithEmailService(
    email: String,
    emailStatus: EmailStatus,
    service: Service,
    subscribeJourney: SubscribeJourney
  )(implicit request: Request[AnyContent], userWithEnrolments: LoggedInUserWithEnrolments): Future[Result] =
    emailVerificationService.isEmailVerified(email).flatMap {
      case Some(true) =>
        onVerifiedEmail(subscribeJourney, service, email, emailStatus, GroupId(userWithEnrolments.groupId))
      case Some(false) =>
        // $COVERAGE-OFF$Loggers
        logger.warn("verified email address false")
        // $COVERAGE-ON
        Future.successful(Redirect(CheckYourEmailController.verifyEmailView(service, subscribeJourney)))
      case _ =>
        // $COVERAGE-OFF$Loggers
        logger.error("Couldn't verify email address")
        // $COVERAGE-ON
        Future.successful(Redirect(CheckYourEmailController.verifyEmailView(service, subscribeJourney)))
    }

  private def onVerifiedEmail(
    subscribeJourney: SubscribeJourney,
    service: Service,
    email: String,
    emailStatus: EmailStatus,
    groupId: GroupId
  )(implicit request: Request[AnyContent]) =
    (subscribeJourney match {
      case SubscribeJourney(AutoEnrolment) if service.enrolmentKey == Service.cds.enrolmentKey =>
        for {
          maybeEori <- sessionCache.eori
          verifiedEmailStatus <- maybeEori.fold(Future.successful(Left(Error): Either[UpdateError, Unit])) {
            eori => updateVerifiedEmailService.updateVerifiedEmail(None, email, eori)
          }
        } yield verifiedEmailStatus
      case _ =>
        //if it's a Long Journey or Short journey for other services than cds we do not update email.
        Future.successful(Right())
    }).flatMap {
      case Right(_) =>
        for {
          _ <- save4LaterService.saveEmailForService(emailStatus.copy(isVerified = true))(
            service,
            subscribeJourney,
            groupId
          )
          _ <- sessionCache.saveEmail(email)
        } yield Redirect(CheckYourEmailController.emailConfirmed(service, subscribeJourney))
      case Left(UpdateEmailError) =>
        // $COVERAGE-OFF$Loggers
        logger.warn("Update Verified Email failed with user-retriable error. Redirecting to error page.")
        // $COVERAGE-ON
        Future.successful(Ok(emailErrorPage()))
      case Left(_) => throw new IllegalArgumentException("Update Verified Email failed with non-retriable error")
    }

}
