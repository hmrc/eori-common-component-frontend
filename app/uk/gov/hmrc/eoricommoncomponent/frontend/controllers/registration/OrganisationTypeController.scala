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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType.{Company, Partnership, _}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.organisationTypeDetailsForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.organisation_type

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationTypeController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionFlowManager: SubscriptionFlowManager,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  organisationTypeView: organisation_type,
  registrationDetailsService: RegistrationDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def nameIdOrganisationMatching(orgType: String, journey: Journey.Value): Call =
    NameIdOrganisationController.form(orgType, journey)

  private def whatIsYourOrgNameMatching(orgType: String, journey: Journey.Value): Call =
    WhatIsYourOrgNameController.showForm(false, orgType, journey)

  private def individualMatching(orgType: String, journey: Journey.Value): Call =
    NameDobController.form(orgType, journey)

  private def thirdCountryIndividualMatching(orgType: String, journey: Journey.Value): Call =
    RowIndividualNameDateOfBirthController.form(orgType, journey)

  private def organisationWhatIsYourOrgName(orgType: String, journey: Journey.Value): Call =
    WhatIsYourOrgNameController.showForm(false, orgType, journey)

  private def matchingDestinations(journey: Journey.Value): Map[CdsOrganisationType, Call] =
    Map[CdsOrganisationType, Call](
      Company                       -> nameIdOrganisationMatching(CompanyId, journey),
      SoleTrader                    -> individualMatching(SoleTraderId, journey),
      Individual                    -> individualMatching(IndividualId, journey),
      Partnership                   -> nameIdOrganisationMatching(PartnershipId, journey),
      LimitedLiabilityPartnership   -> nameIdOrganisationMatching(LimitedLiabilityPartnershipId, journey),
      CharityPublicBodyNotForProfit -> whatIsYourOrgNameMatching(CharityPublicBodyNotForProfitId, journey),
      ThirdCountryOrganisation      -> organisationWhatIsYourOrgName(ThirdCountryOrganisationId, journey),
      ThirdCountrySoleTrader        -> thirdCountryIndividualMatching(ThirdCountrySoleTraderId, journey),
      ThirdCountryIndividual        -> thirdCountryIndividualMatching(ThirdCountryIndividualId, journey)
    )

  def form(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(requestSessionData.selectedUserLocation match {
        case Some(_) =>
          Ok(organisationTypeView(organisationTypeDetailsForm, requestSessionData.selectedUserLocation, journey))
        case None => Ok(organisationTypeView(organisationTypeDetailsForm, Some("uk"), journey))
      })
  }

  def submit(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction { implicit request =>
    def startSubscription: CdsOrganisationType => Future[Result] = { organisationType =>
      subscriptionFlowManager.startSubscriptionFlow(journey) map {
        case (page, newSession) =>
          val session = requestSessionData.sessionWithOrganisationTypeAdded(newSession, organisationType)
          Redirect(page.url).withSession(session)
      }
    }

    _: LoggedInUserWithEnrolments =>
      organisationTypeDetailsForm.bindFromRequest.fold(
        formWithErrors => {
          val userLocation = requestSessionData.selectedUserLocation
          Future.successful(BadRequest(organisationTypeView(formWithErrors, userLocation, journey)))
        },
        organisationType =>
          journey match {
            case Journey.Subscribe =>
              registrationDetailsService.initialiseCacheWithRegistrationDetails(organisationType) flatMap { ok =>
                if (ok) startSubscription(organisationType)
                else throw new IllegalStateException(s"Unable to save $organisationType registration in cache")
              }
            case Journey.Register =>
              registrationDetailsService.initialiseCacheWithRegistrationDetails(organisationType) flatMap { ok =>
                if (ok)
                  Future.successful(
                    Redirect(matchingDestinations(journey)(organisationType))
                      .withSession(requestSessionData.sessionWithOrganisationTypeAdded(organisationType))
                  )
                else throw new IllegalStateException(s"Unable to save $organisationType registration in cache")
              }
          }
      )
  }

}
