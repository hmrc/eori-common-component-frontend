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
import play.i18n.Messages
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{CdsController, FailedEnrolmentException, MissingGroupId, routes}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, GroupEnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhatIsYourEoriController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{ExistingEori, GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailForm.{YesNo, confirmEmailYesNoAnswerForm}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney, Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{EnrolmentService, MissingEnrolmentException}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.{check_your_email, email_confirmed, verify_your_email}
import uk.gov.hmrc.http.HeaderCarrier

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
  emailVerificationService: EmailVerificationService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private val logger = Logger(this.getClass)

  private def populateView(email: Option[String], isInReviewMode: Boolean, service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    Future.successful(Ok(checkYourEmailView(email, confirmEmailYesNoAnswerForm, isInReviewMode, service, subscribeJourney)))

  private def populateEmailVerificationView(email: Option[String], service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    Future.successful(Ok(verifyYourEmail(email, service, subscribeJourney)))

  def createForm(service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
        save4LaterService.fetchEmail(GroupId(userWithEnrolments.groupId)) flatMap {
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
              save4LaterService
                .fetchEmail(GroupId(userWithEnrolments.groupId))
                .flatMap {
                  _.fold {
                    // $COVERAGE-OFF$Loggers
                    logger.warn("[CheckYourEmailController][submit] -   emailStatus cache none")
                    // $COVERAGE-ON
                    Future(
                      BadRequest(
                        checkYourEmailView(None, formWithErrors, isInReviewMode = isInReviewMode, service = service, subscribeJourney)
                      )
                    )
                  } { emailStatus =>
                    Future(
                      BadRequest(
                        checkYourEmailView(
                          emailStatus.email,
                          formWithErrors,
                          isInReviewMode = isInReviewMode,
                          service = service,
                          subscribeJourney
                        )
                      )
                    )
                  }
                },
            yesNoAnswer => locationByAnswer(GroupId(userWithEnrolments.groupId), yesNoAnswer, service, subscribeJourney)
          )
    }

  def verifyEmailView(service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => userWithEnrolments: LoggedInUserWithEnrolments =>
        save4LaterService.fetchEmail(GroupId(userWithEnrolments.groupId)) flatMap { emailStatus =>
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
        save4LaterService.fetchEmail(GroupId(userWithEnrolments.groupId)) flatMap { emailStatus =>
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
                .saveEmail(GroupId(userWithEnrolments.groupId), email.copy(isConfirmed = Some(true)))
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

  private def updateVerifiedEmail(email: String, service: Service, subscribeJourney: SubscribeJourney) = {
    //TODO: Call UpdateVerifiedEmailService.updateVerifiedEmail
    Future.successful(())
  }

  private def submitNewDetails(groupId: GroupId, service: Service, subscribeJourney: SubscribeJourney)(implicit request: Request[_]): Future[Result] =
    save4LaterService.fetchEmail(groupId) flatMap {
      _.fold {
        throw DataUnavailableException("[CheckYourEmailController][submitNewDetails] - emailStatus cache none")
      } { emailStatus =>
        val email: String = emailStatus.email.getOrElse(
          throw DataUnavailableException("[CheckYourEmailController][submitNewDetails] - emailStatus.email none")
        )
          emailVerificationService.createEmailVerificationRequest(email, EmailController.form(service, subscribeJourney).url) flatMap {
            case Some(true) =>
              Future.successful(Redirect(CheckYourEmailController.verifyEmailView(service, subscribeJourney)))
            case Some(false) =>
              // $COVERAGE-OFF$Loggers
              logger.warn(
                "[CheckYourEmailController][sendVerification] - " +
                  "Unable to send email verification request. Service responded with 'already verified'"
              )
              // $COVERAGE-ON
              for {
                _ <- subscribeJourney match {
                  case SubscribeJourney(AutoEnrolment) => updateVerifiedEmail(email, service, subscribeJourney) //here we update email after it's verified.
                  case SubscribeJourney(LongJourney) => Future.successful(()) //if it's a Long Journey we do not update email.
                }
                _ <- save4LaterService.saveEmail(groupId, emailStatus.copy(isVerified = true))
                _ <- cdsFrontendDataCache.saveEmail(email)
              } yield {
                Redirect(EmailController.form(service, subscribeJourney))
              }
            case _ =>
              throw new IllegalStateException("CreateEmailVerificationRequest Failed")
          }
      }
    }

  private def locationByAnswer(groupId: GroupId, yesNoAnswer: YesNo, service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent]
  ): Future[Result] = yesNoAnswer match {
    case theAnswer if theAnswer.isYes => submitNewDetails(groupId, service, subscribeJourney)
    case _                            => Future(Redirect(WhatIsYourEmailController.createForm(service, subscribeJourney)))
  }

}
