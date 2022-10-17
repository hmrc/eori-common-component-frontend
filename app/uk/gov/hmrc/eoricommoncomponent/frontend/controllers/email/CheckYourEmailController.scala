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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email

import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailForm.{confirmEmailYesNoAnswerForm, YesNo}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  NonRetriableError,
  RetriableError,
  UpdateError,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.{check_your_email, email_confirmed, verify_your_email}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email_error_template

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CheckYourEmailController @Inject() (
  authAction: AuthAction,
  save4LaterService: Save4LaterService,
  cdsFrontendDataCache: SessionCache,
  mcc: MessagesControllerComponents,
  checkYourEmailView: check_your_email,
  emailConfirmedView: email_confirmed,
  verifyYourEmail: verify_your_email,
  emailVerificationService: EmailVerificationService,
  updateVerifiedEmailService: UpdateVerifiedEmailService,
  emailErrorPage: email_error_template
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private val logger = Logger(this.getClass)

  private def populateView(
    email: Option[String],
    isInReviewMode: Boolean,
    service: Service,
    subscribeJourney: SubscribeJourney
  )(implicit request: Request[AnyContent]): Future[Result] =
    Future.successful(
      Ok(checkYourEmailView(email, confirmEmailYesNoAnswerForm, isInReviewMode, service, subscribeJourney))
    )

  private def populateEmailVerificationView(
    email: Option[String],
    service: Service,
    subscribeJourney: SubscribeJourney
  )(implicit request: Request[AnyContent]): Future[Result] =
    Future.successful(Ok(verifyYourEmail(email, service, subscribeJourney)))

  def createForm(service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
        save4LaterService.fetchEmailForService(service, subscribeJourney, GroupId(userWithEnrolments.groupId)) flatMap {
          _.fold {
            // $COVERAGE-OFF$Loggers
            logger.warn("[CheckYourEmailController][createForm] -   emailStatus cache none")
            // $COVERAGE-ON
            populateView(None, isInReviewMode = false, service, subscribeJourney)
          } { emailStatus =>
            populateView(emailStatus.email, isInReviewMode = false, service, subscribeJourney: SubscribeJourney)
          }
        }
    }

  def submit(isInReviewMode: Boolean, service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
        confirmEmailYesNoAnswerForm
          .bindFromRequest()
          .fold(
            formWithErrors =>
              Future(
                BadRequest(
                  checkYourEmailView(
                    None,
                    formWithErrors,
                    isInReviewMode = isInReviewMode,
                    service = service,
                    subscribeJourney
                  )
                )
              ),
            yesNoAnswer => locationByAnswer(GroupId(userWithEnrolments.groupId), yesNoAnswer, service, subscribeJourney)
          )
    }

  def verifyEmailView(service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
        save4LaterService.fetchEmailForService(service, subscribeJourney, GroupId(userWithEnrolments.groupId)) flatMap {
          emailStatus =>
            emailStatus.fold {
              // $COVERAGE-OFF$Loggers
              logger.warn("[CheckYourEmailController][verifyEmailView] -  emailStatus cache none")
              // $COVERAGE-ON
              populateEmailVerificationView(None, service, subscribeJourney)
            } { email =>
              populateEmailVerificationView(email.email, service, subscribeJourney)
            }
        }
    }

  def emailConfirmed(service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
        save4LaterService.fetchEmailForService(service, subscribeJourney, GroupId(userWithEnrolments.groupId)) flatMap {
          emailStatus =>
            emailStatus.fold {
              // $COVERAGE-OFF$Loggers
              logger.warn("[CheckYourEmailController][emailConfirmed] -  emailStatus cache none")
              // $COVERAGE-ON
              Future.successful(Redirect(SecuritySignOutController.signOut(service)))
            } { email =>
              if (email.isConfirmed.getOrElse(false))
                Future.successful(toResult(service, subscribeJourney))
              else
                save4LaterService
                  .saveEmailForService(email.copy(isConfirmed = Some(true)))(
                    service,
                    subscribeJourney,
                    GroupId(userWithEnrolments.groupId)
                  )
                  .map { _ =>
                    Ok(emailConfirmedView(service, subscribeJourney))
                  }

            }
        }
    }

  def emailConfirmedContinue(service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(toResult(service, subscribeJourney))
    }

  def toResult(service: Service, subscribeJourney: SubscribeJourney)(implicit r: Request[AnyContent]): Result =
    Ok(emailConfirmedView(service, subscribeJourney))

  private def submitNewDetails(groupId: GroupId, service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    save4LaterService.fetchEmailForService(service, subscribeJourney, groupId) flatMap {
      _.fold {
        throw DataUnavailableException("[CheckYourEmailController][submitNewDetails] - emailStatus cache none")
      } { emailStatus =>
        val email: String = emailStatus.email.getOrElse(
          throw DataUnavailableException("[CheckYourEmailController][submitNewDetails] - emailStatus.email none")
        )
        emailVerificationService.createEmailVerificationRequest(
          email,
          EmailController.form(service, subscribeJourney).url
        ) flatMap {
          case Some(true) =>
            Future.successful(Redirect(CheckYourEmailController.verifyEmailView(service, subscribeJourney)))
          case Some(false) =>
            // $COVERAGE-OFF$Loggers
            logger.warn(
              "[CheckYourEmailController][sendVerification] - " +
                "Unable to send email verification request. Service responded with 'already verified'"
            )
            // $COVERAGE-ON
            onVerifiedEmail(subscribeJourney, service, email, emailStatus, groupId)
          case _ =>
            throw new IllegalStateException("CreateEmailVerificationRequest Failed")
        }
      }
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
          maybeEori <- cdsFrontendDataCache.eori
          verifiedEmailStatus <- maybeEori.fold(Future.successful(Left(NonRetriableError): Either[UpdateError, Unit])) {
            eori => updateVerifiedEmailService.updateVerifiedEmail(None, email, eori)
          }
        } yield verifiedEmailStatus
      case _ =>
        //if it's a Long Journey or Short journey for other services than cds we do not update email.
        Future.successful(Right())
    }).flatMap {
      case Right(_) =>
        for {
          _ <- save4LaterService.saveEmailForService(emailStatus.copy(isConfirmed = Some(true)))(
            service,
            subscribeJourney,
            groupId
          )
          _ <- cdsFrontendDataCache.saveEmail(email)
        } yield Redirect(EmailController.form(service, subscribeJourney))
      case Left(RetriableError) =>
        logger.warn("Update Verified Email failed with user-retriable error. Redirecting to error page.")
        Future.successful(Ok(emailErrorPage()))
      case Left(_) => throw new IllegalArgumentException("Update Verified Email failed with non-retriable error")
    }

  private def locationByAnswer(
    groupId: GroupId,
    yesNoAnswer: YesNo,
    service: Service,
    subscribeJourney: SubscribeJourney
  )(implicit request: Request[AnyContent]): Future[Result] = yesNoAnswer match {
    case theAnswer if theAnswer.isYes => submitNewDetails(groupId, service, subscribeJourney)
    case _                            => Future(Redirect(WhatIsYourEmailController.createForm(service, subscribeJourney)))
  }

}
