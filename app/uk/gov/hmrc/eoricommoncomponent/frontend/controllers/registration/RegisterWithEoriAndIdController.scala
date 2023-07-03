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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, GroupEnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.EnrolmentAlreadyExistsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.Sub02Controller
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{CdsController, MissingGroupId}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.RegisterWithEoriAndIdResponse._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.{MatchingService, Reg06Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.error_template
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.language.LanguageUtils
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.DataUnavailableException

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
  reg06IdAlreadyLinked: reg06_id_already_linked,
  groupEnrolment: GroupEnrolmentExtractor,
  languageUtils: LanguageUtils,
  notifyRcmService: NotifyRcmService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private val logger = Logger(this.getClass)

  def registerWithEoriAndId(implicit service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser =>
      groupEnrolment.hasGroupIdEnrolmentTo(loggedInUser.groupId.getOrElse(throw MissingGroupId()), service).flatMap {
        groupIdEnrolmentExists =>
          if (groupIdEnrolmentExists)
            Future.successful(Redirect(EnrolmentAlreadyExistsController.enrolmentAlreadyExistsForGroup(service)))
          else
            subscriptionDetailsService.cachedCustomsId.flatMap { cachedCustomsId =>
              sendRequest(cachedCustomsId).flatMap {
                case true if isRow && cachedCustomsId.isEmpty => handleREG01Response
                case true                                     => handleREG06Response
                case false =>
                  logger.error("Reg01 BadRequest ROW")
                  val formattedDate = languageUtils.Dates.formatDate(LocalDate.now())
                  Future.successful(Redirect(RegisterWithEoriAndIdController.fail(service, formattedDate)))
              }
            }
      }
    }

  private def sendRequest(cachedCustomsId: Option[CustomsId])(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    originatingService: Service
  ): Future[Boolean] =
    cache.registrationDetails.flatMap { regDetails =>
      (regDetails, cachedCustomsId, isRow) match {
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
    }

  private def handleREG01Response(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    service: Service
  ) =
    cache.registrationDetails.flatMap { regDetails =>
      onRegistrationPassCheckSubscriptionStatus(CustomsId.taxPayerID, regDetails.sapNumber.mdgTaxPayerId)
    }

  private def handleREG06Response(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    service: Service
  ) =
    cache.registerWithEoriAndIdResponse.flatMap { resp =>
      resp.responseDetail.flatMap(_.outcome) match {
        case Some("PASS") =>
          val safeId = resp.responseDetail
            .flatMap(_.responseData.map(x => x.SAFEID))
            .getOrElse(throw new IllegalStateException("SafeId can't be none"))
          onRegistrationPassCheckSubscriptionStatus(idType = "SAFE", id = safeId)
        case Some("DEFERRED") =>
          notifyRcmService.notifyRcm(service).map { _ =>
            Redirect(RegisterWithEoriAndIdController.pending(service))
          }
        case Some("FAIL") =>
          val formattedDate = languageUtils.Dates.formatDate(resp.responseCommon.processingDate.toLocalDate)
          Future.successful(Redirect(RegisterWithEoriAndIdController.fail(service, formattedDate)))
        case None =>
          handleErrorCodes(service, resp)
        case _ =>
          logger.error("Unknown RegistrationDetailsOutCome")
          throw new IllegalStateException("Unknown RegistrationDetailsOutCome")
      }
    }

  def processing(): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
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
      } yield Ok(sub01OutcomeRejectedView(Some(name), processedDate, service))
  }

  def pending(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        subscriptionDetails <- cache.subscriptionDetails
        _                   <- cache.saveSubscriptionDetails(subscriptionDetails)
        processedDate <- cache.registerWithEoriAndIdResponse.map(
          resp => languageUtils.Dates.formatDate(resp.responseCommon.processingDate.toLocalDate)
        )
        _ <- cache.remove
      } yield Ok(
        subscriptionOutcomePendingView(
          subscriptionDetails.eoriNumber.getOrElse(throw DataUnavailableException("No EORI found in cache")),
          processedDate,
          subscriptionDetails.name,
          service
        )
      ).withSession(newUserSession)
    }

  def fail(service: Service, date: String): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cache.subscriptionDetails.map(_.name)
        _    <- cache.remove
      } yield Ok(subscriptionOutcomeFailView(date, name, service)).withSession(newUserSession)
    }

  def eoriAlreadyLinked(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name                      <- cache.subscriptionDetails.map(_.name)
        maybeEori                 <- cache.subscriptionDetails.map(_.eoriNumber)
        maybeExistingEori         <- cache.subscriptionDetails.map(_.existingEoriNumber)
        customsId                 <- cache.subscriptionDetails.map(_.customsId)
        nameIdOrganisationDetails <- cache.subscriptionDetails.map(_.nameIdOrganisationDetails)
        response                  <- cache.registerWithEoriAndIdResponse
        email                     <- cache.email
        _                         <- cache.remove
      } yield {
        val eoriNumber = (maybeEori, maybeExistingEori) match {
          case (_, Some(eori)) => eori.id
          case (Some(eori), _) => eori
          case _               => ""
        }
        val (hasUtr, isIndividual) = response.additionalInformation match {
          case Some(info) =>
            (info.id, info.isIndividual) match {
              case (_: Utr, true) => (true, true)
              case (_, true)      => (false, true)
              case (_, _)         => (false, false)
            }
          case _ => (false, false)
        }
        Ok(
          reg06EoriAlreadyLinked(
            name,
            eoriNumber,
            service,
            isIndividual,
            hasUtr,
            customsId,
            nameIdOrganisationDetails,
            email
          )
        ).withSession(newUserSession)
      }
    }

  def idAlreadyLinked(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name                      <- cache.subscriptionDetails.map(_.name)
        maybeEori                 <- cache.subscriptionDetails.map(_.eoriNumber)
        maybeExistingEori         <- cache.subscriptionDetails.map(_.existingEoriNumber)
        customsId                 <- cache.subscriptionDetails.map(_.customsId)
        nameIdOrganisationDetails <- cache.subscriptionDetails.map(_.nameIdOrganisationDetails)
        email                     <- cache.email
        response                  <- cache.registerWithEoriAndIdResponse
        _                         <- cache.remove
      } yield {
        val eoriNumber = (maybeEori, maybeExistingEori) match {
          case (_, Some(eori)) => eori.id
          case (Some(eori), _) => eori
          case _               => ""
        }
        val (hasUtr, isIndividual) = response.additionalInformation match {
          case Some(info) =>
            (info.id, info.isIndividual) match {
              case (_: Utr, true) => (true, true)
              case (_, true)      => (false, true)
              case (_, _)         => (false, false)
            }
          case _ => (false, false)
        }
        Ok(
          reg06IdAlreadyLinked(
            name,
            eoriNumber,
            service,
            isIndividual,
            hasUtr,
            customsId,
            nameIdOrganisationDetails,
            email
          )
        ).withSession(newUserSession)
      }
    }

  def rejectedPreviously(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name <- cache.subscriptionDetails.map(_.name)
        date <- cache.registerWithEoriAndIdResponse.map(
          r => languageUtils.Dates.formatDate(r.responseCommon.processingDate.toLocalDate)
        )
        _ <- cache.remove
      } yield Ok(sub01OutcomeRejectedView(Some(name), date, service)).withSession(newUserSession)
    }

  private def handleErrorCodes(service: Service, response: RegisterWithEoriAndIdResponse)(implicit
    request: Request[AnyContent]
  ): Future[Result] = {
    val statusText = response.responseCommon.statusText

    statusText match {
      case _ if statusText.exists(_.equalsIgnoreCase(EoriAlreadyLinked)) =>
        logger.warn("Reg06 EoriAlreadyLinked")
        Future.successful(Redirect(RegisterWithEoriAndIdController.eoriAlreadyLinked(service)))
      case _ if statusText.exists(_.equalsIgnoreCase(IDLinkedWithEori)) =>
        logger.warn("Reg06 IDLinkedWithEori")
        Future.successful(Redirect(RegisterWithEoriAndIdController.idAlreadyLinked(service)))
      case _ if statusText.exists(_.equalsIgnoreCase(RejectedPreviouslyAndRetry)) =>
        logger.warn("REG06 Rejected previously")
        Future.successful(Redirect(RegisterWithEoriAndIdController.rejectedPreviously(service)))
      case _ if statusText.exists(_.equalsIgnoreCase(RequestCouldNotBeProcessed)) =>
        logger.warn("REG06 Request could not be processed")
        val formattedDate = languageUtils.Dates.formatDate(LocalDate.now())
        Future.successful(Redirect(RegisterWithEoriAndIdController.fail(service, formattedDate)))
      case _ => Future.successful(InternalServerError(errorTemplateView()))
    }
  }

  private def onSuccessfulSubscriptionStatusSubscribe(service: Service)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] = {
    val internalId = InternalId(loggedInUser.internalId)
    val groupId    = GroupId(loggedInUser.groupId)
    cdsSubscriber
      .subscribeWithCachedDetails(service)
      .flatMap {
        case _: SubscriptionSuccessful =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId, service)
            .map(_ => Redirect(Sub02Controller.migrationEnd(service)))
        case _: SubscriptionPending =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId, service)
            .map(_ => Redirect(RegisterWithEoriAndIdController.pending(service)))
        case sf: SubscriptionFailed =>
          subscriptionDetailsService
            .saveKeyIdentifiers(groupId, internalId, service)
            .map(_ => Redirect(RegisterWithEoriAndIdController.fail(service, sf.processingDate)))
      }
  }

  private def onRegistrationPassCheckSubscriptionStatus(idType: String, id: String)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier,
    service: Service
  ) =
    subscriptionStatusService.getStatus(idType, id).flatMap {
      case NewSubscription | SubscriptionRejected =>
        onSuccessfulSubscriptionStatusSubscribe(service)
      case SubscriptionProcessing =>
        Future.successful(Redirect(RegisterWithEoriAndIdController.processing()))
      case SubscriptionExists => Future.successful(Redirect(SubscriptionRecoveryController.complete(service)))
    }

  private def cachedName(implicit request: Request[AnyContent]) =
    if (isRow) cache.registrationDetails.map(_.name)
    else cache.subscriptionDetails.map(_.name)

  private def isRow(implicit request: Request[AnyContent]) =
    UserLocation.isRow(requestSessionData)

}
