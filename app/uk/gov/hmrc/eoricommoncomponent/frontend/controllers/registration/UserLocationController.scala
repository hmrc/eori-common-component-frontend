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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserLocationController @Inject() (
  authAction: AuthAction,
  requestSessionData: RequestSessionData,
  registrationDetailsService: RegistrationDetailsService,
  sessionCache: SessionCache,
  mcc: MessagesControllerComponents,
  userLocationView: user_location
)(implicit executionContext: ExecutionContext)
    extends CdsController(mcc) {

  def form(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => implicit user: LoggedInUserWithEnrolments =>
      sessionCache.userLocation.map { userLocationDetails =>
        val userLocForm =
          userLocationDetails.location.fold(userLocationForm)(_ => userLocationForm.fill(userLocationDetails))
        Ok(userLocationView(userLocForm, service, user.isOrganisation))
      }
    }

  def submit(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      val boundForm = userLocationForm.bindFromRequest()

      if (boundForm.hasErrors)
        Future.successful(BadRequest(userLocationView(boundForm, service, loggedInUser.isOrganisation)))
      else {
        val formUserLocation = boundForm.value.head
        registrationDetailsService.initialise(formUserLocation).map { _ =>
          Redirect(OrganisationTypeController.form(service))
            .withSession(
              requestSessionData.sessionWithUserLocationAdded(sessionInfoBasedOnJourney(formUserLocation.location))
            )
        }
      }
    }

  private def sessionInfoBasedOnJourney(location: Option[String]): String =
    location.getOrElse(throw new IllegalStateException("User Location not set"))

}
