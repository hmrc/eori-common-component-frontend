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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.email

import play.api.Logging
import play.api.i18n.Messages
import play.api.mvc.Results.*
import play.api.mvc.*
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes as emailRoutes
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.First2LettersEoriController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service.cdsCode
import uk.gov.hmrc.eoricommoncomponent.frontend.models.email.{EmailVerificationStatus, ResponseWithURI}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney, Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.{ExistingEoriService, Save4LaterService}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  Error,
  UpdateEmailError,
  UpdateError,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{email_error_template, error_template}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailJourneyService @Inject() (
  emailVerificationService: EmailVerificationService,
  sessionCache: SessionCache,
  save4LaterService: Save4LaterService,
  updateVerifiedEmailService: UpdateVerifiedEmailService,
  emailErrorPage: email_error_template,
  errorPage: error_template,
  appConfig: AppConfig,
  existingEoriService: ExistingEoriService
)(implicit ec: ExecutionContext)
    extends Logging {

  def continue(service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent],
    user: LoggedInUserWithEnrolments,
    messages: Messages,
    hc: HeaderCarrier
  ): Future[Result] =
    save4LaterService.fetchEmailForService(service, subscribeJourney, GroupId(user.groupId)) flatMap {
      _.fold {
        // $COVERAGE-OFF$
        logger.info(s"emailStatus cache none ${user.internalId}")
        Future.successful(Redirect(emailRoutes.WhatIsYourEmailController.createForm(service, subscribeJourney)))
      } { cachedEmailStatus =>
        cachedEmailStatus.email match {
          case Some(email) =>
            if (cachedEmailStatus.isVerified)
              sessionCache.saveEmail(email) flatMap { _ =>
                verifiedInCache(service, subscribeJourney)
              }
            else
              checkWithEmailService(email, cachedEmailStatus, user.credId, service, subscribeJourney)
          case None =>
            Future.successful(Redirect(emailRoutes.WhatIsYourEmailController.createForm(service, subscribeJourney)))
        }
      }
    }

  private def checkWithEmailService(
    email: String,
    emailStatus: EmailStatus,
    credId: String,
    service: Service,
    subscribeJourney: SubscribeJourney
  )(implicit
    request: Request[AnyContent],
    userWithEnrolments: LoggedInUserWithEnrolments,
    messages: Messages,
    hc: HeaderCarrier
  ): Future[Result] =
    emailVerificationService.getVerificationStatus(email, credId).foldF(
      _ => Future.successful(InternalServerError(errorPage(service))),
      {
        case EmailVerificationStatus.Verified =>
          onVerifiedEmail(subscribeJourney, service, email, emailStatus, GroupId(userWithEnrolments.groupId))
        case EmailVerificationStatus.Unverified =>
          // $COVERAGE-OFF$
          logger.info("Email address was not verified")
          // $COVERAGE-ON$
          submitNewDetails(email, service, subscribeJourney, credId)
        case EmailVerificationStatus.Locked =>
          // $COVERAGE-OFF$
          logger.warn("Email address is locked")
          // $COVERAGE-ON$
          Future.successful(Redirect(emailRoutes.LockedEmailController.onPageLoad(service)))
      }
    )

  private def onVerifiedEmail(
    subscribeJourney: SubscribeJourney,
    service: Service,
    email: String,
    emailStatus: EmailStatus,
    groupId: GroupId
  )(implicit request: Request[AnyContent], messages: Messages, hc: HeaderCarrier) =
    (subscribeJourney match {
      case SubscribeJourney(AutoEnrolment) if service.enrolmentKey == Service.cds.enrolmentKey =>
        for {
          maybeEori <- sessionCache.eori
          verifiedEmailStatus <- maybeEori.fold(
            Future.successful(Left(Error("No EORI in cache")): Either[UpdateError, Unit])
          ) {
            eori => updateVerifiedEmailService.updateVerifiedEmail(None, email, eori)
          }
        } yield verifiedEmailStatus
      case _ =>
        // if it's a Long Journey or Short journey for other services than cds we do not update email.
        Future.successful(Right((): Unit))
    }).flatMap {
      case Right(_) =>
        for {
          _ <- save4LaterService.saveEmailForService(emailStatus.copy(isVerified = true))(
            service,
            subscribeJourney,
            groupId
          )
          _ <- sessionCache.saveEmail(email)
        } yield Redirect(emailRoutes.CheckYourEmailController.emailConfirmed(service, subscribeJourney))
      case Left(UpdateEmailError(_)) =>
        // $COVERAGE-OFF$
        logger.warn("Update Verified Email failed with user-retriable error. Redirecting to error page.")
        // $COVERAGE-ON$
        Future.successful(Ok(emailErrorPage(service)))
      case Left(_) => throw new IllegalArgumentException("Update Verified Email failed with non-retriable error")
    }

  private def submitNewDetails(
    email: String,
    service: Service,
    subscribeJourney: SubscribeJourney,
    credId: String
  )(implicit request: Request[AnyContent], messages: Messages, hc: HeaderCarrier): Future[Result] =
    emailVerificationService.startVerificationJourney(credId, service, email, subscribeJourney).fold(
      _ => InternalServerError(errorPage(service)),
      (responseWithUri: ResponseWithURI) =>
        Redirect(s"${appConfig.emailVerificationFrontendBaseUrl}${responseWithUri.redirectUri}")
    )

  private def verifiedInCache(service: Service, subscribeJourney: SubscribeJourney)(implicit
    request: Request[AnyContent],
    user: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] =
    subscribeJourney match {
      case SubscribeJourney(AutoEnrolment) => existingEoriService.onEnrol(service)
      case SubscribeJourney(LongJourney) =>
        (appConfig.euEoriEnabled && service.code == cdsCode) match {
          case true =>
            Future.successful(Redirect(First2LettersEoriController.form(service)))
          case false => Future.successful(Redirect(routes.WhatIsYourEoriController.createForm(service)))
        }
    }

}
