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
import org.joda.time.LocalDate
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{Action, _}
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Individual
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.matching.Organisation
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.utrForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.MatchingService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.match_organisation_utr
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DoYouHaveAUtrNumberController @Inject() (
  authAction: AuthAction,
  matchingService: MatchingService,
  mcc: MessagesControllerComponents,
  matchOrganisationUtrView: match_organisation_utr,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private val OrganisationModeDM = "organisation"

  def form(
    organisationType: String,
    service: Service,
    journey: Journey.Value,
    isInReviewMode: Boolean = false
  ): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(
        Ok(matchOrganisationUtrView(utrForm, organisationType, OrganisationModeDM, service, journey, isInReviewMode))
      )
    }

  def submit(
    organisationType: String,
    service: Service,
    journey: Journey.Value,
    isInReviewMode: Boolean = false
  ): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      utrForm.bindFromRequest.fold(
        formWithErrors => Future.successful(BadRequest(view(organisationType, formWithErrors, service, journey))),
        formData =>
          destinationsByAnswer(
            formData,
            organisationType,
            service,
            journey,
            isInReviewMode,
            InternalId(loggedInUser.internalId)
          )
      )
    }

  private def destinationsByAnswer(
    formData: UtrMatchModel,
    organisationType: String,
    service: Service,
    journey: Journey.Value,
    isInReviewMode: Boolean,
    internalId: InternalId
  )(implicit request: Request[AnyContent]): Future[Result] =
    formData.haveUtr match {
      case Some(true) =>
        matchBusinessOrIndividual(formData, service, journey, organisationType, internalId)
      case Some(false) =>
        subscriptionDetailsService.updateSubscriptionDetails
        noUtrDestination(organisationType, service, journey, isInReviewMode)
      case _ =>
        throw new IllegalArgumentException("Have UTR should be Some(true) or Some(false) but was None")
    }

  private def noUtrDestination(
    organisationType: String,
    service: Service,
    journey: Journey.Value,
    isInReviewMode: Boolean
  ): Future[Result] =
    organisationType match {
      case CdsOrganisationType.CharityPublicBodyNotForProfitId =>
        Future.successful(Redirect(VatRegisteredUkController.form(service)))
      case CdsOrganisationType.ThirdCountryOrganisationId =>
        noUtrThirdCountryOrganisationRedirect(isInReviewMode, organisationType, service, journey)
      case CdsOrganisationType.ThirdCountrySoleTraderId | CdsOrganisationType.ThirdCountryIndividualId =>
        noUtrThirdCountryIndividualsRedirect(service, journey)
      case _ =>
        Future.successful(Redirect(YouNeedADifferentServiceController.form(journey)))
    }

  private def noUtrThirdCountryOrganisationRedirect(
    isInReviewMode: Boolean,
    organisationType: String,
    service: Service,
    journey: Journey.Value
  ): Future[Result] =
    if (isInReviewMode)
      Future.successful(Redirect(DetermineReviewPageController.determineRoute(service, journey)))
    else
      Future.successful(
        Redirect(
          SixLineAddressController
            .showForm(isInReviewMode = false, organisationType, service, journey)
        )
      )

  private def noUtrThirdCountryIndividualsRedirect(service: Service, journey: Journey.Value): Future[Result] =
    Future.successful(Redirect(DoYouHaveNinoController.displayForm(service, journey)))

  private def matchBusiness(
    id: CustomsId,
    name: String,
    dateEstablished: Option[LocalDate],
    matchingServiceType: String,
    internalId: InternalId
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Boolean] =
    matchingService.matchBusiness(id, Organisation(name, matchingServiceType), dateEstablished, internalId)

  private def matchIndividual(id: CustomsId, internalId: InternalId)(implicit hc: HeaderCarrier): Future[Boolean] =
    subscriptionDetailsService.cachedNameDobDetails flatMap {
      case Some(details) =>
        matchingService.matchIndividualWithId(
          id,
          Individual.withLocalDate(details.firstName, details.middleName, details.lastName, details.dateOfBirth),
          internalId
        )
      case None => Future.successful(false)
    }

  private def view(organisationType: String, form: Form[UtrMatchModel], service: Service, journey: Journey.Value)(
    implicit request: Request[AnyContent]
  ): HtmlFormat.Appendable =
    matchOrganisationUtrView(form, organisationType, OrganisationModeDM, service, journey)

  private def matchBusinessOrIndividual(
    formData: UtrMatchModel,
    service: Service,
    journey: Journey.Value,
    organisationType: String,
    internalId: InternalId
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    (organisationType match {
      case CdsOrganisationType.ThirdCountrySoleTraderId | CdsOrganisationType.ThirdCountryIndividualId =>
        matchIndividual(Utr(formData.id.get), internalId)
      case orgType =>
        subscriptionDetailsService.cachedNameDetails.flatMap {
          case Some(NameOrganisationMatchModel(name)) =>
            matchBusiness(
              Utr(formData.id.get),
              name,
              None,
              EtmpOrganisationType(CdsOrganisationType(orgType)).toString,
              internalId
            )
          case None => Future.successful(false)
        }
    }).map {
      case true  => Redirect(ConfirmContactDetailsController.form(service, journey))
      case false => matchNotFoundBadRequest(organisationType, formData, service, journey)
    }

  private def matchNotFoundBadRequest(
    organisationType: String,
    formData: UtrMatchModel,
    service: Service,
    journey: Journey.Value
  )(implicit request: Request[AnyContent]): Result = {
    val errorMsg = organisationType match {
      case CdsOrganisationType.SoleTraderId | CdsOrganisationType.IndividualId |
          CdsOrganisationType.ThirdCountrySoleTraderId | CdsOrganisationType.ThirdCountryIndividualId =>
        Messages("cds.matching-error.individual-not-found")
      case _ => Messages("cds.matching-error-organisation.not-found")
    }
    val errorForm = utrForm.withGlobalError(errorMsg).fill(formData)
    BadRequest(view(organisationType, errorForm, service, journey))
  }

}
