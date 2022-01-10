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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.organisationTypeDetailsForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.organisation_type

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationTypeController @Inject() (
  authAction: AuthAction,
  subscriptionFlowManager: SubscriptionFlowManager,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  organisationTypeView: organisation_type,
  registrationDetailsService: RegistrationDetailsService,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        subscriptionDetailsService.cachedOrganisationType map { orgType =>
          def filledForm = orgType.map(organisationTypeDetailsForm.fill(_)).getOrElse(organisationTypeDetailsForm)
          requestSessionData.selectedUserLocation match {
            case Some(_) =>
              Ok(organisationTypeView(filledForm, requestSessionData.selectedUserLocation, service))
            case None => Ok(organisationTypeView(filledForm, Some("uk"), service))
          }
        }
    }

  def submit(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request =>
        def startSubscription: CdsOrganisationType => Future[Result] = { organisationType =>
          subscriptionFlowManager.startSubscriptionFlow(cdsOrganisationType = organisationType, service = service) map {
            case (page, newSession) =>
              val session = requestSessionData.sessionWithOrganisationTypeAdded(newSession, organisationType)
              Redirect(page.url(service)).withSession(session)
          }
        }

        _: LoggedInUserWithEnrolments =>
          organisationTypeDetailsForm.bindFromRequest.fold(
            formWithErrors => {
              val userLocation = requestSessionData.selectedUserLocation
              Future.successful(BadRequest(organisationTypeView(formWithErrors, userLocation, service)))
            },
            organisationType =>
              registrationDetailsService.initialiseCacheWithRegistrationDetails(organisationType) flatMap { ok =>
                if (ok) startSubscription(organisationType)
                else throw new IllegalStateException(s"Unable to save $organisationType registration in cache")
              }
          )
    }

}
