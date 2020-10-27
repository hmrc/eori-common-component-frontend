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
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{
  AuthAction,
  EnrolmentExtractor,
  GroupEnrolmentExtractor
}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes.CheckYourEmailController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes.WhatIsYourEmailController.createForm
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.{
  EnrolmentAlreadyExistsController,
  EnrolmentPendingAgainstGroupIdController,
  YouAlreadyHaveEoriController
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, InternalId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.{Save4LaterService, UserGroupIdSubscriptionStatusCheckService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailController @Inject() (
  authAction: AuthAction,
  emailVerificationService: EmailVerificationService,
  sessionCache: SessionCache,
  mcc: MessagesControllerComponents,
  save4LaterService: Save4LaterService,
  userGroupIdSubscriptionStatusCheckService: UserGroupIdSubscriptionStatusCheckService,
  groupEnrolment: GroupEnrolmentExtractor
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  private val logger = Logger(this.getClass)

  private def userIsInProcess(service: Service, journey: Journey.Value)(implicit
    request: Request[AnyContent],
    user: LoggedInUserWithEnrolments
  ): Future[Result] =
    continue(service, journey)

  private def otherUserWithinGroupIsInProcess(service: Service, journey: Journey.Value): Future[Result] =
    Future.successful(Redirect(EnrolmentPendingAgainstGroupIdController.show(service, journey)))

  private def continue(service: Service, journey: Journey.Value)(implicit
    request: Request[AnyContent],
    user: LoggedInUserWithEnrolments
  ): Future[Result] =
    save4LaterService.fetchEmail(InternalId(user.internalId)) flatMap {
      _.fold {
        logger.warn(s"emailStatus cache none ${user.internalId}")
        Future.successful(Redirect(createForm(service, journey)))
      } { cachedEmailStatus =>
        if (cachedEmailStatus.isVerified)
          sessionCache.saveEmail(cachedEmailStatus.email) map { _ =>
            Redirect(CheckYourEmailController.emailConfirmed(service, journey))
          }
        else checkWithEmailService(cachedEmailStatus, service, journey)
      }
    }

  def form(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      journey match {
        case Journey.Subscribe => startSubscribeJourney(service)
        case Journey.Register  => startRegisterJourney(service)
      }
    }

  private def startSubscribeJourney(
    service: Service
  )(implicit hc: HeaderCarrier, request: Request[AnyContent], user: LoggedInUserWithEnrolments) =
    userGroupIdSubscriptionStatusCheckService
      .checksToProceed(GroupId(user.groupId), InternalId(user.internalId))(continue(service, Journey.Subscribe))(
        userIsInProcess(service, Journey.Subscribe)
      )(otherUserWithinGroupIsInProcess(service, Journey.Subscribe))

  private def startRegisterJourney(
    service: Service
  )(implicit hc: HeaderCarrier, request: Request[AnyContent], user: LoggedInUserWithEnrolments) =
    groupEnrolment.groupIdEnrolments(user.groupId.getOrElse(throw MissingGroupId())).flatMap {
      groupEnrolments =>
        if (groupEnrolments.exists(_.service == service.enrolmentKey))
          // user has specified service
          Future.successful(
            Redirect(EnrolmentAlreadyExistsController.enrolmentAlreadyExistsForGroup(service, Journey.Register))
          )
        else
          existingEoriForUserOrGroup(user, groupEnrolments) match {
            case Some(_) =>
              // user already has EORI
              Future.successful(Redirect(YouAlreadyHaveEoriController.display(service)))
            case None =>
              userGroupIdSubscriptionStatusCheckService
                .checksToProceed(GroupId(user.groupId), InternalId(user.internalId))(
                  continue(service, Journey.Register)
                )(userIsInProcess(service, Journey.Register))(
                  otherUserWithinGroupIsInProcess(service, Journey.Register)
                )
          }
    }

  private def checkWithEmailService(emailStatus: EmailStatus, service: Service, journey: Journey.Value)(implicit
    hc: HeaderCarrier,
    userWithEnrolments: LoggedInUserWithEnrolments
  ): Future[Result] =
    emailVerificationService.isEmailVerified(emailStatus.email).flatMap {
      case Some(true) =>
        for {
          _ <- {
            logger.warn("updated verified email status true to save4later")
            save4LaterService.saveEmail(InternalId(userWithEnrolments.internalId), emailStatus.copy(isVerified = true))
          }
          _ <- {
            logger.warn("saved verified email address true to cache")
            sessionCache.saveEmail(emailStatus.email)
          }
        } yield Redirect(CheckYourEmailController.emailConfirmed(service, journey))
      case Some(false) =>
        logger.warn("verified email address false")
        Future.successful(Redirect(CheckYourEmailController.verifyEmailView(service, journey)))
      case _ =>
        logger.error("Couldn't verify email address")
        Future.successful(Redirect(CheckYourEmailController.verifyEmailView(service, journey)))
    }

}
