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
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{CdsController, FeatureFlags}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.registration.RegistrationDisplayResponse
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDisplayService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{NewSubscription, _}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.error_template
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.{sub01_outcome_processing, sub01_outcome_rejected}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserLocationController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  save4LaterService: Save4LaterService,
  subscriptionStatusService: SubscriptionStatusService,
  taxEnrolmentsService: TaxEnrolmentsService,
  sessionCache: SessionCache,
  registrationDisplayService: RegistrationDisplayService,
  mcc: MessagesControllerComponents,
  userLocationView: user_location,
  sub01OutcomeProcessing: sub01_outcome_processing,
  sub01OutcomeRejected: sub01_outcome_rejected,
  errorTemplate: error_template
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with FeatureFlags {

  private def isAffinityOrganisation(affinityGroup: Option[AffinityGroup]): Boolean =
    affinityGroup.contains(AffinityGroup.Organisation)

  private def continue(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    Future.successful(Ok(userLocationView(userLocationForm, journey, isAffinityOrganisation(user.affinityGroup))))

  def form(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user: LoggedInUserWithEnrolments =>
      continue(journey)
    }

  private def forRow(journey: Journey.Value, internalId: InternalId, location: String)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier
  ) =
    subscriptionStatusBasedOnSafeId(internalId).map {
      case (NewSubscription | SubscriptionRejected, Some(safeId)) => {
        registrationDisplayService
          .requestDetails(safeId)
          .flatMap(cacheAndRedirect(journey, location))
      }
      case (status, _) =>
        subscriptionStatus(status, internalId, journey, Some(location))
    }.flatMap(identity)

  def submit(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      userLocationForm.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(
            BadRequest(userLocationView(formWithErrors, journey, isAffinityOrganisation(loggedInUser.affinityGroup)))
          )
        },
        details =>
          (journey, details.location, loggedInUser.internalId) match {
            case (_, Some(UserLocation.Iom), Some(_)) =>
              Future.successful(Redirect(YouNeedADifferentServiceIomController.form(journey)))
            case (Journey.Register, Some(location), Some(id)) if UserLocation.isRow(location) =>
              forRow(journey, InternalId(id), location)
            case _ =>
              Future.successful(
                Redirect(OrganisationTypeController.form(journey))
                  .withSession(
                    requestSessionData
                      .sessionWithUserLocationAdded(sessionInfoBasedOnJourney(journey, details.location))
                  )
              )
        }
      )
    }

  private def sessionInfoBasedOnJourney(journey: Journey.Value, location: Option[String]): String =
    journey match {
      case Journey.Register =>
        location match {
          case Some(UserLocation.ThirdCountry) => "third-country"
          case Some(UserLocation.Eu)           => "eu"
          case Some(UserLocation.Iom)          => "iom"
          case Some(UserLocation.Islands)      => "islands"
          case Some(UserLocation.Uk)           => "uk"
          case _                               => throw new IllegalStateException("User Location not set")
        }
      case _ =>
        location.getOrElse(throw new IllegalStateException("User Location not set"))

    }

  private def subscriptionStatusBasedOnSafeId(internalId: InternalId)(implicit hc: HeaderCarrier) =
    for {
      mayBeSafeId <- save4LaterService.fetchSafeId(internalId)
      preSubscriptionStatus <- mayBeSafeId match {
        case Some(safeId) =>
          subscriptionStatusService.getStatus("SAFE", safeId.id)
        case None => Future.successful(NewSubscription)
      }
    } yield (preSubscriptionStatus, mayBeSafeId)

  private def handleExistingSubscription(
    internalId: InternalId
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    save4LaterService
      .fetchSafeId(internalId)
      .flatMap(
        safeId =>
          sessionCache
            .saveRegistrationDetails(RegistrationDetails.rdSafeId(safeId.get))
            .flatMap { _ =>
              taxEnrolmentsService.doesEnrolmentExist(safeId.get).map {
                case true =>
                  Redirect(
                    SignInWithDifferentDetailsController
                      .form(Journey.Register)
                  )
                case false =>
                  Redirect(
                    SubscriptionRecoveryController
                      .complete(Journey.Register)
                  )
              }
          }
      )

  def subscriptionStatus(
    preSubStatus: PreSubscriptionStatus,
    internalId: InternalId,
    journey: Journey.Value,
    location: Option[String]
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] =
    preSubStatus match {
      case SubscriptionProcessing =>
        Future.successful(Redirect(UserLocationController.processing()))
      case SubscriptionExists => handleExistingSubscription(internalId)
      case NewSubscription | SubscriptionRejected =>
        Future.successful(
          Redirect(OrganisationTypeController.form(journey))
            .withSession(requestSessionData.sessionWithUserLocationAdded(sessionInfoBasedOnJourney(journey, location)))
        )
    }

  def cacheAndRedirect(journey: Journey.Value, location: String)(
    implicit request: Request[AnyContent],
    hc: HeaderCarrier
  ): Either[_, RegistrationDisplayResponse] => Future[Result] = {

    case rResponse @ Right(RegistrationDisplayResponse(_, Some(_))) =>
      registrationDisplayService.cacheDetails(rResponse.b).flatMap { _ =>
        Future.successful(
          Redirect(BusinessDetailsRecoveryController.form(journey)).withSession(
            requestSessionData.sessionWithUserLocationAdded(sessionInfoBasedOnJourney(journey, Some(location)))
          )
        )
      }
    case _ => Future.successful(ServiceUnavailable(errorTemplate()))
  }

  def processing: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      sessionCache.sub01Outcome
        .map(_.processedDate)
        .map(processedDate => Ok(sub01OutcomeProcessing(None, processedDate)))
  }

  def rejected: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      sessionCache.sub01Outcome
        .map(_.processedDate)
        .map(processedDate => Ok(sub01OutcomeRejected(None, processedDate)))
  }
}
