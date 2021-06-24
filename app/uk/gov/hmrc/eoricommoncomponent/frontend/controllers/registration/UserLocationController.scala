/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.mvc._
import uk.gov.hmrc.auth.core.AffinityGroup
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration._

import scala.concurrent.Future

@Singleton
class UserLocationController @Inject() (
  authAction: AuthAction,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  userLocationView: user_location
) extends CdsController(mcc) {

  private def isAffinityOrganisation(affinityGroup: Option[AffinityGroup]): Boolean =
    affinityGroup.contains(AffinityGroup.Organisation)

  private def continue(
    service: Service
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    Future.successful(Ok(userLocationView(userLocationForm, service, isAffinityOrganisation(user.affinityGroup))))

  def form(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      continue(service)
    }

  def submit(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      userLocationForm.bindFromRequest.fold(
        formWithErrors =>
          Future.successful(
            BadRequest(userLocationView(formWithErrors, service, isAffinityOrganisation(loggedInUser.affinityGroup)))
          ),
        details =>
          Future.successful(
            Redirect(OrganisationTypeController.form(service))
              .withSession(
                requestSessionData
                  .sessionWithUserLocationAdded(sessionInfoBasedOnJourney(details.location))
              )
          )
      )
    }

  private def sessionInfoBasedOnJourney(location: Option[String]): String =
    location.getOrElse(throw new IllegalStateException("User Location not set"))

}
