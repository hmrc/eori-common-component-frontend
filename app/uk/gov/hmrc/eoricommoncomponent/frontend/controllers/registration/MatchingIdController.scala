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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.EnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{CdsController, FeatureFlags}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.MatchingService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MatchingIdController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  matchingService: MatchingService,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext) extends CdsController(mcc) with EnrolmentExtractor with FeatureFlags {

  def matchWithIdOnly(): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      matchLoggedInUserAndRedirect(loggedInUser) {
        Redirect(UserLocationController.form(Journey.Register))
      } {
        Redirect(ConfirmContactDetailsController.form(Journey.Register))
      }
  }

  def matchWithIdOnlyForExistingReg(): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(Redirect(UserLocationController.form(Journey.Subscribe)))
  }

  private def matchLoggedInUserAndRedirect(loggedInUser: LoggedInUserWithEnrolments)(
    redirectOrganisationTypePage: => Result
  )(redirectToConfirmationPage: => Result)(implicit hc: HeaderCarrier, request: Request[AnyContent]) =
    if (matchingEnabled) {
      lazy val ctUtr = enrolledCtUtr(loggedInUser)
      lazy val saUtr = enrolledSaUtr(loggedInUser)
      lazy val nino = enrolledNino(loggedInUser)

      (ctUtr orElse saUtr orElse nino).fold(ifEmpty = Future.successful(redirectOrganisationTypePage)) { utrOrNino =>
        matchingService.matchBusinessWithIdOnly(utrOrNino, loggedInUser) map {
          case true  => redirectToConfirmationPage
          case false => redirectOrganisationTypePage
        }
      }
    } else {
      Future.successful(redirectOrganisationTypePage)
    }
}
