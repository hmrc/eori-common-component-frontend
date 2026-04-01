/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.Logger
import play.api.mvc.*
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, GroupEnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.*
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.{
  ApplicationController,
  EnrolmentAlreadyExistsController
}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.{
  Sub02Controller,
  WeNeedToMakeChecksController
}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{CdsController, MissingGroupId}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.*
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType.*
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.RegisterWithEoriAndIdResponse.*
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubmissionCompleteData
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm.EoriRegion
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.{
  MatchingService,
  Reg06Service,
  RegisterWithoutIdService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.*
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.error_template
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.*
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegistrationController @Inject() (
  authAction: AuthAction,
  requestSessionData: RequestSessionData,
  cache: SessionCache,
  reg06Service: Reg06Service,
  matchingService: MatchingService,
  cdsSubscriber: CdsSubscriber,
  subscriptionStatusService: SubscriptionStatusService,
  subscriptionDetailsService: SubscriptionDetailsService,
  registerWithoutIdService: RegisterWithoutIdService,
  mcc: MessagesControllerComponents,
  sub01OutcomeProcessingView: sub01_outcome_processing,
  sub01OutcomeRejectedView: sub01_outcome_rejected,
  errorTemplateView: error_template,
  subscriptionOutcomePendingView: subscription_outcome_pending,
  subscriptionOutcomeFailCompanyView: subscription_outcome_fail_company,
  subscriptionOutcomeFailPartnershipView: subscription_outcome_fail_partnership,
  subscriptionOutcomeFailLlpView: subscription_outcome_fail_llp,
  subscriptionOutcomeFailOrganisationView: subscription_outcome_fail_organisation,
  subscriptionOutcomeFailSoloAndIndividualView: subscription_outcome_fail_solo_and_individual,
  subscriptionOutcomeFailRowUtrOrgView: subscription_outcome_fail_row_utr_organisation,
  subscriptionOutcomeFailRowView: subscription_outcome_fail_row,
  subscriptionOutcomeFailEuEoriView: subscription_outcome_fail_eu_eori,
  reg06EoriAlreadyLinked: reg06_eori_already_linked,
  reg06IdAlreadyLinked: reg06_id_already_linked,
  groupEnrolment: GroupEnrolmentExtractor,
  notifyRcmService: NotifyRcmService,
  appConfig: AppConfig
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
            isEuEori.flatMap { isEuEoriFlow =>
              if (isEuEoriFlow) {
                (for {
                  registerWithoutIDResponse <- sendEuEoriRequest(request, loggedInUser, service)
                  result <- registerWithoutIDResponse.responseDetail match {
                    case Some(detail) =>
                      onRegistrationPassCheckSubscriptionStatus(idType = CustomsId.SAFE, detail.SAFEID)
                    case None =>
                      logger.error(s"Error during EU EORI registration: REG02 returned None")
                      Future.failed(new Exception("No response detail available from REG02"))
                  }
                } yield result).recover {
                  case e: Throwable =>
                    logger.error(s"Error during EU EORI registration: ${e.getMessage}", e)
                    Redirect(RegistrationController.fail(service))
                }
              } else
                subscriptionDetailsService.cachedCustomsId.flatMap { cachedCustomsId =>
                  sendRequest(cachedCustomsId).flatMap {
                    case true if isRow && cachedCustomsId.isEmpty =>
                      handleREG01Response
                    case true =>
                      handleREG06Response
                    case false =>
                      logger.error("Reg01 BadRequest ROW")
                      Future.successful(Redirect(RegistrationController.fail(service)))
                  }
                }
            }
      }
    }

  private def isEuEori(implicit service: Service, request: Request[AnyContent]): Future[Boolean] =
    for {
      first2LettersOfEori <- cache.getFirst2LettersEori
    } yield first2LettersOfEori.contains(EoriRegion.EU) && appConfig.euEoriEnabled && service.code == Service.cdsCode

  private def sendEuEoriRequest(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    service: Service
  ): Future[RegisterWithoutIDResponse] =
    for {
      registrationDetails <- cache.registrationDetails
      subscriptionDetails <- cache.subscriptionDetails
      orgType = subscriptionDetails.formData.organisationType
      regResponse <- if (requestSessionData.userSubscriptionFlow.isIndividualFlow)
        registerWithoutIdService.registerIndividual(
          subscriptionDetails,
          loggedInUser,
          orgType
        )
      else
        registerWithoutIdService.registerOrganisation(
          subscriptionDetails,
          loggedInUser,
          orgType
        )
    } yield regResponse

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
  ): Future[Result] =
    cache.registrationDetails.flatMap { regDetails =>
      onRegistrationPassCheckSubscriptionStatus(CustomsId.taxPayerID, regDetails.sapNumber.mdgTaxPayerId)
    }

  private def handleREG06Response(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    service: Service
  ): Future[Result] =
    cache.registerWithEoriAndIdResponse.flatMap { resp =>
      resp.responseDetail.flatMap(_.outcome) match {
        case Some("PASS") =>
          val safeId = resp.responseDetail
            .flatMap(_.responseData.map(x => x.SAFEID))
            .getOrElse(throw new IllegalStateException("SafeId can't be none"))
          onRegistrationPassCheckSubscriptionStatus(idType = CustomsId.SAFE, id = safeId)
        case Some("DEFERRED") =>
          notifyRcmService.notifyRcm(service).map { _ =>
            Redirect(RegistrationController.pending(service))
          }
        case Some("FAIL") =>
          Future.successful(Redirect(RegistrationController.fail(service)))
        case None =>
          handleErrorCodes(service, resp)
        case _ =>
          logger.error("Unknown RegistrationDetailsOutCome")
          throw new IllegalStateException("Unknown RegistrationDetailsOutCome")
      }
    }

  def processing(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => (_: LoggedInUserWithEnrolments) =>
      for {
        name          <- cachedName
        processedDate <- cache.sub01Outcome.map(_.processedDate)
      } yield Ok(sub01OutcomeProcessingView(Some(name), processedDate, service))
  }

  def rejected(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => (_: LoggedInUserWithEnrolments) =>
      Future.successful(Ok(sub01OutcomeRejectedView(service)))
  }

  def pending(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => (_: LoggedInUserWithEnrolments) =>
      for {
        submissionCompleteData <- cache.submissionCompleteDetails
        _                      <- cache.journeyCompleted
      } yield displaySubscriptionOutcomePendingView(submissionCompleteData, service)
  }

  /** it turns out that the eori number & processing date are more verification rather than display purposes
    */
  private def displaySubscriptionOutcomePendingView(submissionCompleteData: SubmissionCompleteData, service: Service)(
    implicit request: Request[_]
  ): Result = {
    val result = for {
      details        <- submissionCompleteData.subscriptionDetails
      processingDate <- submissionCompleteData.processingDate
      eoriNumber     <- details.eoriNumber
    } yield Ok(subscriptionOutcomePendingView(service))

    result.getOrElse {
      logger.warn("Subscription Complete Data not found for this session")
      Redirect(ApplicationController.startSubscription(service))
    }
  }

  def fail(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => (_: LoggedInUserWithEnrolments) =>
      for {
        subscriptionDetails <- cache.subscriptionDetails
        isEuEori            <- isEuEori(service, request)
        orgType   = subscriptionDetails.formData.organisationType
        customsId = subscriptionDetails.customsId
        _ <- cache.journeyCompleted
      } yield {
        val isUk = requestSessionData.selectedUserLocation.forall(_ == UserLocation.Uk)
        val view = determineFailView(service, orgType, customsId.nonEmpty, isUk, isEuEori)
        Ok(view)
      }

    }

  def determineFailView(
    service: Service,
    orgType: Option[CdsOrganisationType],
    isCustomsIdPopulated: Boolean,
    isUk: Boolean,
    isEuEori: Boolean
  )(implicit request: Request[_]): HtmlFormat.Appendable =
    if (isEuEori) {
      subscriptionOutcomeFailEuEoriView(service)
    } else if (isUk)
      orgType match {
        case Some(Company)                         => subscriptionOutcomeFailCompanyView(service)
        case Some(CdsOrganisationType.Partnership) => subscriptionOutcomeFailPartnershipView(service)
        case Some(LimitedLiabilityPartnership)     => subscriptionOutcomeFailLlpView(service)
        case Some(CharityPublicBodyNotForProfit)   => subscriptionOutcomeFailOrganisationView(service)
        case Some(SoleTrader) | Some(Individual)   => subscriptionOutcomeFailSoloAndIndividualView(service)
        case _                                     => subscriptionOutcomeFailSoloAndIndividualView(service)
      }
    else
      orgType match {
        case Some(Company) =>
          if (isCustomsIdPopulated) subscriptionOutcomeFailRowUtrOrgView(service)
          else subscriptionOutcomeFailRowView(service, isOrganisation = true)
        case _ =>
          if (isCustomsIdPopulated) subscriptionOutcomeFailSoloAndIndividualView(service)
          else subscriptionOutcomeFailRowView(service, isOrganisation = false)

      }

  def eoriAlreadyLinked(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => (_: LoggedInUserWithEnrolments) =>
      for {
        maybeEori                 <- cache.subscriptionDetails.map(_.eoriNumber)
        maybeExistingEori         <- cache.subscriptionDetails.map(_.existingEoriNumber)
        customsId                 <- cache.subscriptionDetails.map(_.customsId)
        nameIdOrganisationDetails <- cache.subscriptionDetails.map(_.nameIdOrganisationDetails)
        response                  <- cache.registerWithEoriAndIdResponse
        email                     <- cache.email
        _                         <- cache.journeyCompleted
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
        Ok(reg06EoriAlreadyLinked(eoriNumber, service, isIndividual, customsId, nameIdOrganisationDetails, email))
      }
    }

  def idAlreadyLinked(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => (_: LoggedInUserWithEnrolments) =>
      for {
        maybeEori                 <- cache.subscriptionDetails.map(_.eoriNumber)
        maybeExistingEori         <- cache.subscriptionDetails.map(_.existingEoriNumber)
        customsId                 <- cache.subscriptionDetails.map(_.customsId)
        nameIdOrganisationDetails <- cache.subscriptionDetails.map(_.nameIdOrganisationDetails)
        email                     <- cache.email
        response                  <- cache.registerWithEoriAndIdResponse
        _                         <- cache.journeyCompleted
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
        Ok(reg06IdAlreadyLinked(eoriNumber, service, isIndividual, hasUtr, customsId, nameIdOrganisationDetails, email))
      }
    }

  def rejectedPreviously(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => (_: LoggedInUserWithEnrolments) =>
      cache.journeyCompleted.map(_ => Ok(sub01OutcomeRejectedView(service)))
    }

  private def handleErrorCodes(service: Service, response: RegisterWithEoriAndIdResponse)(implicit
    request: Request[AnyContent]
  ): Future[Result] = {
    val statusText = response.responseCommon.statusText

    statusText match {
      case _ if statusText.exists(_.equalsIgnoreCase(EoriAlreadyLinked)) =>
        logger.warn("Reg06 EoriAlreadyLinked")
        Future.successful(Redirect(RegistrationController.eoriAlreadyLinked(service)))
      case _ if statusText.exists(_.equalsIgnoreCase(IDLinkedWithEori)) =>
        logger.warn("Reg06 IDLinkedWithEori")
        Future.successful(Redirect(RegistrationController.idAlreadyLinked(service)))
      case _ if statusText.exists(_.equalsIgnoreCase(RejectedPreviouslyAndRetry)) =>
        logger.warn("REG06 Rejected previously")
        Future.successful(Redirect(RegistrationController.rejectedPreviously(service)))
      case _ if statusText.exists(_.equalsIgnoreCase(RequestCouldNotBeProcessed)) =>
        logger.warn("REG06 Request could not be processed")
        Future.successful(Redirect(RegistrationController.fail(service)))
      case _ => Future.successful(InternalServerError(errorTemplateView(service)))
    }
  }

  private def onSuccessfulSubscriptionStatusSubscribe(service: Service)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier
  ): Future[Result] = {
    val internalId = InternalId(loggedInUser.internalId)
    val groupId    = GroupId(loggedInUser.groupId)

    isEuEori(service, request).flatMap { cdsEuUser =>
      cdsSubscriber
        .subscribeWithCachedDetails(service)
        .flatMap {
          case _: SubscriptionSuccessful =>
            subscriptionDetailsService
              .saveKeyIdentifiers(groupId, internalId, service)
              .map(_ => Redirect(Sub02Controller.migrationEnd(service)))
          case _: SubscriptionPending if cdsEuUser =>
            subscriptionDetailsService
              .saveKeyIdentifiers(groupId, internalId, service)
              .map(_ => Redirect(WeNeedToMakeChecksController.displayPage(service)))
          case _: SubscriptionPending =>
            subscriptionDetailsService
              .saveKeyIdentifiers(groupId, internalId, service)
              .map(_ => Redirect(RegistrationController.pending(service)))
          case _: SubscriptionFailed =>
            subscriptionDetailsService
              .saveKeyIdentifiers(groupId, internalId, service)
              .map(_ => Redirect(RegistrationController.fail(service)))
        }
    }
  }

  private def onRegistrationPassCheckSubscriptionStatus(idType: String, id: String)(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    hc: HeaderCarrier,
    service: Service
  ): Future[Result] =
    subscriptionStatusService.getStatus(idType, id).flatMap {
      case NewSubscription | SubscriptionRejected =>
        onSuccessfulSubscriptionStatusSubscribe(service)
      case SubscriptionProcessing =>
        Future.successful(Redirect(RegistrationController.processing(service)))
      case SubscriptionExists => Future.successful(Redirect(SubscriptionRecoveryController.complete(service)))
    }

  private def cachedName(implicit request: Request[AnyContent]): Future[String] =
    if (isRow) cache.registrationDetails.map(_.name)
    else cache.subscriptionDetails.map(_.name)

  private def isRow(implicit request: Request[AnyContent]) =
    UserLocation.isRow(requestSessionData)

}
