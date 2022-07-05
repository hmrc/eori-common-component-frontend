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
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney, Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.UpdateVerifiedEmailService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.{Save4LaterService, UserGroupIdSubscriptionStatusCheckService}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{
  enrolment_pending_against_group_id,
  enrolment_pending_for_user
}
import uk.gov.hmrc.http.HeaderCarrier

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
  enrolmentPendingAgainstGroupId: enrolment_pending_against_group_id
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
    save4LaterService.fetchEmail(GroupId(user.groupId)) flatMap {
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
          case _ => Future.successful(Redirect(WhatIsYourEmailController.createForm(service, subscribeJourney)))
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

  private def updateVerifiedEmail(email: String)(implicit request: Request[AnyContent]): Future[Unit] =
    for {
      maybeEori <- sessionCache.eori
      result <- maybeEori.fold(Future.successful(Option(false))) {
        eori => updateVerifiedEmailService.updateVerifiedEmail(None, email, eori)
      }
    } yield result.map(isUpdated => if (isUpdated) () else throw new IllegalArgumentException("UpdateEmail failed"))

  private def checkWithEmailService(
    email: String,
    emailStatus: EmailStatus,
    service: Service,
    subscribeJourney: SubscribeJourney
  )(implicit request: Request[AnyContent], userWithEnrolments: LoggedInUserWithEnrolments): Future[Result] =
    emailVerificationService.isEmailVerified(email).flatMap {
      case Some(true) =>
        for {
          _ <- {
            // $COVERAGE-OFF$Loggers
            logger.warn("updated verified email status true to save4later")
            // $COVERAGE-ON
            save4LaterService.saveEmail(GroupId(userWithEnrolments.groupId), emailStatus.copy(isVerified = true))
          }
          _ <- {
            // $COVERAGE-OFF$Loggers
            logger.warn("saved verified email address true to cache")
            // $COVERAGE-ON
            sessionCache.saveEmail(email)
          }
          _ <- subscribeJourney match {
            case SubscribeJourney(AutoEnrolment) =>
              updateVerifiedEmail(
                email
              ) //here the email will be updated in case it was existing in save4later before and was not verified.
            case SubscribeJourney(LongJourney) => Future.successful(()) //if it's a Long Journey we do not update email.
          }
        } yield Redirect(CheckYourEmailController.emailConfirmed(service, subscribeJourney))
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

}
