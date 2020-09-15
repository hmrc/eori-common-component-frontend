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
import org.joda.time.DateTime
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.{Sub02Controller, _}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.RegisterWithEoriAndIdResponse._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.FormUtils.dateTimeFormat
import uk.gov.hmrc.eoricommoncomponent.frontend.logging.CdsLogger
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.{MatchingService, Reg06Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.error_template
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterWithEoriAndIdController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  cache: SessionCache,
  reg06Service: Reg06Service,
  matchingService: MatchingService,
  cdsSubscriber: CdsSubscriber,
  subscriptionStatusService: SubscriptionStatusService,
  subscriptionDetailsService: SubscriptionDetailsService,
  mcc: MessagesControllerComponents,
  sub01OutcomeProcessingView: sub01_outcome_processing,
  sub01OutcomeRejectedView: sub01_outcome_rejected,
  errorTemplateView: error_template,
  subscriptionOutcomePendingView: subscription_outcome_pending,
  subscriptionOutcomeFailView: subscription_outcome_fail,
  reg06EoriAlreadyLinked: reg06_eori_already_linked,
  taxEnrolmentsService: TaxEnrolmentsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def registerWithEoriAndId(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser =>
      sendRequest().flatMap {
        case true if isRow => handleRowResponse(journey)
        case true          => handleREG06Response(journey)
        case false =>
          CdsLogger.error("Reg01 BadRequest ROW")
          val formattedDate = dateTimeFormat.print(DateTime.now())
          Future.successful(Redirect(RegisterWithEoriAndIdController.fail(formattedDate)))
      }
    }

  private def sendRequest()(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments
  ): Future[Boolean] = {
    for {
      regDetails      <- cache.registrationDetails
      cachedCustomsId <- subscriptionDetailsService.cachedCustomsId
    } yield (regDetails, cachedCustomsId, isRow) match {
      case (_: RegistrationDetailsOrganisation, Some(_), true) =>
        reg06Service.sendOrganisationRequest
      case (_: RegistrationDetailsOrganisation, None, true) =>
        matchingService.sendOrganisationRequestForMatchingService
      case (_: RegistrationDetailsOrganisation, _, false) =>
        reg06Service.sendOrganisationRequest
      case (_: RegistrationDetailsIndividual, Some(_), true) =>
        reg06Service.sendIndividualRequest
      case (_: RegistrationDetailsIndividual, None, true) =>
        matchingService.sendIndividualRequestForMatchingService
      case _ => reg06Service.sendIndividualRequest
    }
  }.flatMap(identity)

  private def handleRowResponse(journey: Journey.Value)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] = subscriptionDetailsService.cachedCustomsId flatMap {
    case Some(_) => handleREG06Response(journey)
    case _       => handleREG01Response(journey)
  }

  private def handleREG01Response(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments) =
    cache.registrationDetails.flatMap { regDetails =>
      onRegistrationPassCheckSubscriptionStatus(
        journey,
        "taxPayerID",
        regDetails.sapNumber.mdgTaxPayerId,
        regDetails.safeId
      )
    }

  private def handleREG06Response(
    journey: Journey.Value
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments) =
    cache.registerWithEoriAndIdResponse.flatMap { resp =>
      resp.responseDetail.flatMap(_.outcome) match {
        case Some("PASS") =>
          val safeId = resp.responseDetail
            .flatMap(_.responseData.map(x => x.SAFEID))
            .getOrElse(throw new IllegalStateException("SafeId can't be none"))
          onRegistrationPassCheckSubscriptionStatus(journey, idType = "SAFE", id = safeId, SafeId(safeId))
        case Some("DEFERRED") =>
          val formattedDate =
            dateTimeFormat.print(resp.responseCommon.processingDate)
          Future.successful(Redirect(RegisterWithEoriAndIdController.pending(formattedDate)))
        case Some("FAIL") =>
          val formattedDate =
            dateTimeFormat.print(resp.responseCommon.processingDate)
          Future.successful(Redirect(RegisterWithEoriAndIdController.fail(formattedDate)))
        case None =>
          val statusText = resp.responseCommon.statusText
          handleErrorCodes(statusText)
        case _ =>
          CdsLogger.error("Unknown RegistrationDetailsOutCome ")
          throw new IllegalStateException("Unknown RegistrationDetailsOutCome")
      }
    }

  def processing: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name          <- cachedName
        processedDate <- cache.sub01Outcome.map(_.processedDate)
      } yield Ok(sub01OutcomeProcessingView(Some(name), processedDate))
  }

  def rejected: Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name          <- cachedName
        processedDate <- cache.sub01Outcome.map(_.processedDate)
      } yield Ok(sub01OutcomeRejectedView(Some(name), processedDate))
  }

  def pending(date: String): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        eori <- cache.subscriptionDetails.map(
          _.eoriNumber.getOrElse(throw new IllegalStateException("No EORI found in cache"))
        )
        name <- cache.subscriptionDetails.map(_.name)
        _    <- cache.remove
      } yield Ok(subscriptionOutcomePendingView(eori, date, name))
    }

  def fail(date: String): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cache.subscriptionDetails.map(_.name)
        _    <- cache.remove
      } yield Ok(subscriptionOutcomeFailView(date, name))
    }

  def eoriAlreadyLinked(): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cache.subscriptionDetails.map(_.name)
        date <- cache.registerWithEoriAndIdResponse.map(_.responseCommon.processingDate)
        _    <- cache.remove
      } yield Ok(reg06EoriAlreadyLinked(name, date))
    }

  def rejectedPreviously(): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cache.subscriptionDetails.map(_.name)
        date <- cache.registerWithEoriAndIdResponse.map(r => dateTimeFormat.print(r.responseCommon.processingDate))
        _    <- cache.remove
      } yield Ok(sub01OutcomeRejectedView(Some(name), date))
    }

  private def handleErrorCodes(statusText: Option[String])(implicit request: Request[AnyContent]): Future[Result] =
    statusText match {
      case _ if statusText.contains(EoriAlreadyLinked) =>
        CdsLogger.warn("Reg06 EoriAlreadyLinked")
        Future.successful(Redirect(RegisterWithEoriAndIdController.eoriAlreadyLinked()))
      case _ if statusText.contains(IDLinkedWithEori) =>
        CdsLogger.warn("Reg06 IDLinkedWithEori")
        Future.successful(Redirect(RegisterWithEoriAndIdController.eoriAlreadyLinked()))
      case _ if statusText.contains(RejectedPreviouslyAndRetry) =>
        Future.successful(Redirect(RegisterWithEoriAndIdController.rejectedPreviously()))
      case _ => Future.successful(ServiceUnavailable(errorTemplateView()))
    }

  private def onSuccessfulSubscriptionStatusSubscribe(journey: Journey.Value)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] = {
    val internalId = InternalId(loggedInUser.internalId)
    val groupId    = GroupId(loggedInUser.groupId)
    cdsSubscriber
      .subscribeWithCachedDetails(requestSessionData.userSelectedOrganisationType, journey)
      .flatMap {
        case _: SubscriptionSuccessful =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(Sub02Controller.migrationEnd()))
        case sp: SubscriptionPending =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(RegisterWithEoriAndIdController.pending(sp.processingDate)))
        case sf: SubscriptionFailed =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(RegisterWithEoriAndIdController.fail(sf.processingDate)))
      }
  }

  private def onRegistrationPassCheckSubscriptionStatus(
    journey: Journey.Value,
    idType: String,
    id: String,
    safeId: SafeId
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments, hc: HeaderCarrier) =
    subscriptionStatusService.getStatus(idType, id).flatMap {
      case NewSubscription | SubscriptionRejected =>
        onSuccessfulSubscriptionStatusSubscribe(journey)
      case SubscriptionProcessing =>
        Future.successful(Redirect(RegisterWithEoriAndIdController.processing()))
      case SubscriptionExists => handleExistingSubscription(safeId, journey)
    }

  private def handleExistingSubscription(safeId: SafeId, journey: Journey.Value)(implicit
    request: Request[AnyContent],
    hc: HeaderCarrier
  ): Future[Result] =
    taxEnrolmentsService.doesEnrolmentExist(safeId).map {
      case true => Redirect(SignInWithDifferentDetailsController.form(journey))
      case false =>
        Redirect(SubscriptionRecoveryController.complete(journey))
    }

  private def cachedName(implicit request: Request[AnyContent]) =
    if (isRow) cache.registrationDetails.map(_.name)
    else cache.subscriptionDetails.map(_.name)

  private def isRow(implicit request: Request[AnyContent]) =
    UserLocation.isRow(requestSessionData)

}
