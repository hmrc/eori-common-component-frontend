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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers

import play.api.i18n.MessagesApi
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.start_subscribe

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ApplicationController @Inject() (
  authorise: AuthAction,
  mcc: MessagesControllerComponents,
  viewStartSubscribe: start_subscribe,
  cache: SessionCache,
  enrolmentJourneyService: EnrolmentJourneyService,
  appConfig: AppConfig
)(implicit override val messagesApi: MessagesApi, ec: ExecutionContext)
    extends CdsController(mcc) {

  def startSubscription(service: Service): Action[AnyContent] = authorise.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => implicit loggedInUser: LoggedInUserWithEnrolments =>
      val groupId = loggedInUser.groupId.getOrElse(throw MissingGroupId())
      enrolmentJourneyService
        .getJourney(loggedInUser, groupId, service).map {
          case Right(AutoEnrolment) =>
            Redirect(routes.HasExistingEoriController.displayPage(service))
          case Right(LongJourney) =>
            val headingAndTitleMessage = s"ecc.subscription.information.titleAndHeading.${service.code}"
            Ok(viewStartSubscribe(service, headingAndTitleMessage))
          case Left(EnrolmentExistsUser) =>
            Redirect(routes.YouCannotUseServiceController.unableToUseIdPage(service))
          case Left(EnrolmentExistsGroup) =>
            Redirect(routes.EnrolmentAlreadyExistsController.enrolmentAlreadyExistsForGroup(service))
        }
  }

  def logout(service: Service): Action[AnyContent] =
    authorise.ggAuthorisedUserAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        cache.remove map { _ =>
          Redirect(appConfig.feedbackUrl(service)).withNewSession
        }
    }

  def keepAlive: Action[AnyContent] = Action.async { implicit request =>
    cache.keepAlive map { _ =>
      Ok("Ok")
    }
  }

}

case class MissingGroupId() extends Exception(s"User doesn't have groupId")
