/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhatIsYourEoriController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{routes, CdsController}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, LoggedInUserWithEnrolments, YesNo}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailForm.confirmEmailYesNoAnswerForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailJourneyService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.{check_your_email, email_confirmed}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CheckYourEmailController @Inject() (
  authAction: AuthAction,
  save4LaterService: Save4LaterService,
  mcc: MessagesControllerComponents,
  checkYourEmailView: check_your_email,
  emailConfirmedView: email_confirmed,
  emailJourneyService: EmailJourneyService
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
      Ok(checkYourEmailView(email, confirmEmailYesNoAnswerForm(), isInReviewMode, service, subscribeJourney))
    )

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
      implicit request => implicit userWithEnrolments: LoggedInUserWithEnrolments =>
        confirmEmailYesNoAnswerForm()
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
            yesNoAnswer => locationByAnswer(yesNoAnswer, service, subscribeJourney)
          )
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
              Future.successful(Redirect(routes.SecuritySignOutController.signOut(service)))
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

  def toResult(service: Service, subscribeJourney: SubscribeJourney)(implicit r: Request[AnyContent]): Result =
    Ok(emailConfirmedView(service, subscribeJourney))

  def acceptConfirmation(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { _ => _: LoggedInUserWithEnrolments =>
      Future.successful(Redirect(WhatIsYourEoriController.createForm(service)))
    }

  private def locationByAnswer(yesNoAnswer: YesNo, service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent],
    userWithEnrolments: LoggedInUserWithEnrolments
  ): Future[Result] = yesNoAnswer match {
    case theAnswer if theAnswer.isYes => emailJourneyService.continue(service, subscribeJourney)
    case _                            => Future(Redirect(WhatIsYourEmailController.createForm(service, subscribeJourney)))
  }

}
