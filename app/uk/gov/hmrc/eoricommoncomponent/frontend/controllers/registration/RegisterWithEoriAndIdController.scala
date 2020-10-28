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
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, GroupEnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.EnrolmentAlreadyExistsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.{Sub02Controller, _}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{CdsController, MissingGroupId}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.RegisterWithEoriAndIdResponse._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.FormUtils.dateTimeFormat
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.{MatchingService, Reg06Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.error_template
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterWithEoriAndIdController @Inject() (
  authAction: AuthAction,
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
  taxEnrolmentsService: TaxEnrolmentsService,
  groupEnrolment: GroupEnrolmentExtractor
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private val logger = Logger(this.getClass)

  def registerWithEoriAndId(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser =>
      groupEnrolment.hasGroupIdEnrolmentTo(loggedInUser.groupId.getOrElse(throw MissingGroupId()), service).flatMap {
        groupIdEnrolmentExists =>
          if (groupIdEnrolmentExists)
            Future.successful(
              Redirect(EnrolmentAlreadyExistsController.enrolmentAlreadyExistsForGroup(service, journey))
            )
          else
            sendRequest().flatMap {
              case true if isRow => handleRowResponse(service, journey)
              case true          => handleREG06Response(service, journey)
              case false =>
                logger.error("Reg01 BadRequest ROW")
                val formattedDate = dateTimeFormat.print(DateTime.now())
                Future.successful(Redirect(RegisterWithEoriAndIdController.fail(service, formattedDate)))
            }
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

  private def handleRowResponse(service: Service, journey: Journey.Value)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] = subscriptionDetailsService.cachedCustomsId flatMap {
    case Some(_) => handleREG06Response(service, journey)
    case _       => handleREG01Response(service, journey)
  }

  private def handleREG01Response(service: Service, journey: Journey.Value)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments
  ) =
    cache.registrationDetails.flatMap { regDetails =>
      onRegistrationPassCheckSubscriptionStatus(
        service,
        journey,
        "taxPayerID",
        regDetails.sapNumber.mdgTaxPayerId,
        regDetails.safeId
      )
    }

  private def handleREG06Response(service: Service, journey: Journey.Value)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments
  ) =
    cache.registerWithEoriAndIdResponse.flatMap { resp =>
      resp.responseDetail.flatMap(_.outcome) match {
        case Some("PASS") =>
          val safeId = resp.responseDetail
            .flatMap(_.responseData.map(x => x.SAFEID))
            .getOrElse(throw new IllegalStateException("SafeId can't be none"))
          onRegistrationPassCheckSubscriptionStatus(service, journey, idType = "SAFE", id = safeId, SafeId(safeId))
        case Some("DEFERRED") =>
          val formattedDate =
            dateTimeFormat.print(resp.responseCommon.processingDate)
          Future.successful(Redirect(RegisterWithEoriAndIdController.pending(service, formattedDate)))
        case Some("FAIL") =>
          val formattedDate =
            dateTimeFormat.print(resp.responseCommon.processingDate)
          Future.successful(Redirect(RegisterWithEoriAndIdController.fail(service, formattedDate)))
        case None =>
          val statusText = resp.responseCommon.statusText
          handleErrorCodes(service, statusText)
        case _ =>
          logger.error("Unknown RegistrationDetailsOutCome")
          throw new IllegalStateException("Unknown RegistrationDetailsOutCome")
      }
    }

  def processing(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name          <- cachedName
        processedDate <- cache.sub01Outcome.map(_.processedDate)
      } yield Ok(sub01OutcomeProcessingView(Some(name), processedDate))
  }

  def rejected(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name          <- cachedName
        processedDate <- cache.sub01Outcome.map(_.processedDate)
      } yield Ok(sub01OutcomeRejectedView(Some(name), processedDate))
  }

  def pending(service: Service, date: String): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        eori <- cache.subscriptionDetails.map(
          _.eoriNumber.getOrElse(throw new IllegalStateException("No EORI found in cache"))
        )
        name <- cache.subscriptionDetails.map(_.name)
        _    <- cache.remove
      } yield Ok(subscriptionOutcomePendingView(eori, date, name))
    }

  def fail(service: Service, date: String): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cache.subscriptionDetails.map(_.name)
        _    <- cache.remove
      } yield Ok(subscriptionOutcomeFailView(date, name))
    }

  def eoriAlreadyLinked(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cache.subscriptionDetails.map(_.name)
        date <- cache.registerWithEoriAndIdResponse.map(_.responseCommon.processingDate)
        _    <- cache.remove
      } yield Ok(reg06EoriAlreadyLinked(name, date))
    }

  def rejectedPreviously(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cache.subscriptionDetails.map(_.name)
        date <- cache.registerWithEoriAndIdResponse.map(r => dateTimeFormat.print(r.responseCommon.processingDate))
        _    <- cache.remove
      } yield Ok(sub01OutcomeRejectedView(Some(name), date))
    }

  private def handleErrorCodes(service: Service, statusText: Option[String])(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    statusText match {
      case _ if statusText.contains(EoriAlreadyLinked) =>
        logger.warn("Reg06 EoriAlreadyLinked")
        Future.successful(Redirect(RegisterWithEoriAndIdController.eoriAlreadyLinked(service)))
      case _ if statusText.contains(IDLinkedWithEori) =>
        logger.warn("Reg06 IDLinkedWithEori")
        Future.successful(Redirect(RegisterWithEoriAndIdController.eoriAlreadyLinked(service)))
      case _ if statusText.contains(RejectedPreviouslyAndRetry) =>
        Future.successful(Redirect(RegisterWithEoriAndIdController.rejectedPreviously(service)))
      case _ => Future.successful(ServiceUnavailable(errorTemplateView()))
    }

  private def onSuccessfulSubscriptionStatusSubscribe(service: Service, journey: Journey.Value)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] = {
    val internalId = InternalId(loggedInUser.internalId)
    val groupId    = GroupId(loggedInUser.groupId)
    cdsSubscriber
      .subscribeWithCachedDetails(requestSessionData.userSelectedOrganisationType, service, journey)
      .flatMap {
        case _: SubscriptionSuccessful =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(Sub02Controller.migrationEnd(service)))
        case sp: SubscriptionPending =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(RegisterWithEoriAndIdController.pending(service, sp.processingDate)))
        case sf: SubscriptionFailed =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId)
            .map(_ => Redirect(RegisterWithEoriAndIdController.fail(service, sf.processingDate)))
      }
  }

  private def onRegistrationPassCheckSubscriptionStatus(
    service: Service,
    journey: Journey.Value,
    idType: String,
    id: String,
    safeId: SafeId
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments, hc: HeaderCarrier) =
    subscriptionStatusService.getStatus(idType, id).flatMap {
      case NewSubscription | SubscriptionRejected =>
        onSuccessfulSubscriptionStatusSubscribe(service, journey)
      case SubscriptionProcessing =>
        Future.successful(Redirect(RegisterWithEoriAndIdController.processing(service)))
      case SubscriptionExists => handleExistingSubscription(safeId, service, journey)
    }

  private def handleExistingSubscription(safeId: SafeId, service: Service, journey: Journey.Value)(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    taxEnrolmentsService.doesEnrolmentExist(safeId).map {
      case true => Redirect(SignInWithDifferentDetailsController.form(service, journey))
      case false =>
        Redirect(SubscriptionRecoveryController.complete(service, journey))
    }

  private def cachedName(implicit request: Request[AnyContent]) =
    if (isRow) cache.registrationDetails.map(_.name)
    else cache.subscriptionDetails.map(_.name)

  private def isRow(implicit request: Request[AnyContent]) =
    UserLocation.isRow(requestSessionData)

}
