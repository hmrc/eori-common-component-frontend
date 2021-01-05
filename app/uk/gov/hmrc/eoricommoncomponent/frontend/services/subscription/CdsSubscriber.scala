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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{RecipientDetails, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CdsSubscriber @Inject() (
  subscriptionService: SubscriptionService,
  sessionCache: SessionCache,
  handleSubscriptionService: HandleSubscriptionService,
  subscriptionDetailsService: SubscriptionDetailsService,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext) {

  private val logger = Logger(this.getClass)

  def subscribeWithCachedDetails(
    cdsOrganisationType: Option[CdsOrganisationType],
    service: Service,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent], messages: Messages): Future[SubscriptionResult] = {
    def migrationEoriUK: Future[SubscriptionResult] =
      for {
        subscriptionDetailsHolder <- sessionCache.subscriptionDetails
        eori = subscriptionDetailsHolder.eoriNumber.getOrElse(
          throw new IllegalStateException("Eori not found in cache")
        )
        email               <- sessionCache.email
        registrationDetails <- sessionCache.registerWithEoriAndIdResponse(hc)
        subscriptionResult  <- subscriptionService.existingReg(registrationDetails, Eori(eori), email, service)
        _ <- onSubscriptionResultForUKSubscribe(
          subscriptionResult,
          registrationDetails,
          subscriptionDetailsHolder,
          email,
          service
        )
      } yield subscriptionResult

    def subscribeEori: Future[SubscriptionResult] =
      for {
        registrationDetails <- sessionCache.registrationDetails
        (subscriptionResult, maybeSubscriptionDetails) <- fetchOtherDetailsFromCacheAndSubscribe(
          registrationDetails,
          cdsOrganisationType,
          journey,
          service
        )
        _ <- onSubscriptionResult(subscriptionResult, registrationDetails, maybeSubscriptionDetails, service)
      } yield subscriptionResult

    def migrationEoriROW: Future[SubscriptionResult] =
      for {
        registrationDetails <- sessionCache.registrationDetails(hc)
        subscriptionDetails <- sessionCache.subscriptionDetails
        email               <- sessionCache.email
        subscriptionResult <- subscriptionService.subscribeWithMandatoryOnly(
          registrationDetails,
          subscriptionDetails,
          journey,
          service
        )
        _ <- onSubscriptionResultForRowSubscribe(
          subscriptionResult,
          registrationDetails,
          subscriptionDetails,
          email,
          service
        )
      } yield subscriptionResult

    val isRowF           = Future.successful(UserLocation.isRow(requestSessionData))
    val journeyF         = Future.successful(journey)
    val cachedCustomsIdF = subscriptionDetailsService.cachedCustomsId

    val result = for {
      isRow    <- isRowF
      journey  <- journeyF
      customId <- if (isRow) cachedCustomsIdF else Future.successful(None)
    } yield (journey, isRow, customId) match {
      case (Journey.Subscribe, true, Some(_)) => migrationEoriUK  //Has NINO/UTR as identifier UK journey
      case (Journey.Subscribe, true, None)    => migrationEoriROW //ROW
      case (Journey.Subscribe, false, _)      => migrationEoriUK  //UK Journey
      case _                                  => subscribeEori    //Journey Get An EORI
    }
    result.flatMap(identity)
  }

  private def fetchOtherDetailsFromCacheAndSubscribe(
    registrationDetails: RegistrationDetails,
    mayBeCdsOrganisationType: Option[CdsOrganisationType],
    journey: Journey.Value,
    service: Service
  )(implicit hc: HeaderCarrier): Future[(SubscriptionResult, Option[SubscriptionDetails])] =
    for {
      subscriptionDetailsHolder <- sessionCache.subscriptionDetails
      subscriptionResult <- subscriptionService.subscribe(
        registrationDetails,
        subscriptionDetailsHolder,
        mayBeCdsOrganisationType,
        journey,
        service
      )
    } yield (subscriptionResult, Some(subscriptionDetailsHolder))

  private def onSubscriptionResult(
    subscriptionResult: SubscriptionResult,
    registrationDetails: RegistrationDetails,
    subscriptionDetails: Option[SubscriptionDetails],
    service: Service
  )(implicit hc: HeaderCarrier, messages: Messages): Future[Unit] = {

    val sapNumber = registrationDetails.sapNumber
    val safeId    = registrationDetails.safeId

    def recipientDetails(formBundleId: String, processingDate: String) = {
      val contactDetails = subscriptionDetails
        .flatMap(_.contactDetails.map(_.contactDetails))
        .getOrElse(
          throw new IllegalStateException(s"No contact details available to save for formBundleId $formBundleId")
        )
      RecipientDetails(
        service,
        Journey.Register,
        contactDetails.emailAddress,
        contactDetails.fullName,
        Some(registrationDetails.name),
        Some(processingDate)
      )
    }
    subscriptionResult match {

      case success: SubscriptionSuccessful =>
        sessionCache.saveSub02Outcome(
          Sub02Outcome(success.processingDate, registrationDetails.name, Some(success.eori.id))
        )
        val formBundleId = success.formBundleId
        handleSubscriptionService.handleSubscription(
          formBundleId,
          recipientDetails(formBundleId, success.processingDate),
          sapNumber,
          Some(success.eori),
          success.emailVerificationTimestamp,
          safeId
        )

      case pending: SubscriptionPending =>
        sessionCache.saveSub02Outcome(Sub02Outcome(pending.processingDate, registrationDetails.name))
        val formBundleId = pending.formBundleId
        handleSubscriptionService.handleSubscription(
          formBundleId,
          recipientDetails(formBundleId, pending.processingDate),
          sapNumber,
          eori = None,
          pending.emailVerificationTimestamp,
          safeId
        )

      case failed: SubscriptionFailed =>
        sessionCache.saveSub02Outcome(Sub02Outcome(failed.processingDate, registrationDetails.name))
        Future.successful(())
    }
  }

  private def onSubscriptionResultForRowSubscribe(
    subscriptionResult: SubscriptionResult,
    regDetails: RegistrationDetails,
    subDetails: SubscriptionDetails,
    email: String,
    service: Service
  )(implicit hc: HeaderCarrier, messages: Messages): Future[Unit] = {

    val completionDate = subscriptionResult match {
      case success: SubscriptionSuccessful => Some(success.processingDate)
      case _                               => None
    }

    subscriptionResult match {
      case success: SubscriptionSuccessful =>
        sessionCache.saveSub02Outcome(Sub02Outcome(success.processingDate, regDetails.name, Some(success.eori.id)))
      case _ =>
    }

    callHandle(
      subscriptionResult,
      RecipientDetails(
        service,
        Journey.Subscribe,
        email,
        subDetails.contactDetails.map(_.fullName).getOrElse(""),
        Some(regDetails.name),
        completionDate
      ),
      regDetails.sapNumber,
      subDetails.eoriNumber.map(Eori),
      regDetails.safeId
    )
  }

  private def onSubscriptionResultForUKSubscribe(
    subscriptionResult: SubscriptionResult,
    regDetails: RegisterWithEoriAndIdResponse,
    subDetails: SubscriptionDetails,
    email: String,
    service: Service
  )(implicit hc: HeaderCarrier, messages: Messages): Future[Unit] = {

    val taxPayerId  = regDetails.responseDetail.flatMap(_.responseData.map(r => TaxPayerId(r.SAFEID)))
    val contactName = regDetails.responseDetail.flatMap(_.responseData.flatMap(_.contactDetail.map(_.contactName)))

    val completionDate = Some(subscriptionResult.processingDate)

    subscriptionResult match {
      case success: SubscriptionSuccessful =>
        sessionCache.saveSub02Outcome(
          Sub02Outcome(success.processingDate, "", Some(success.eori.id))
        ) //TODO  name is blank
      case _ => //TODO needs clarification
    }

    (contactName, taxPayerId) match {
      case (name, Some(id)) =>
        if (name.isEmpty) logger.warn("ContactName missing")
        val orgName = Some(subDetails.name)
        callHandle(
          subscriptionResult,
          RecipientDetails(service, Journey.Subscribe, email, name.getOrElse(""), orgName, completionDate),
          id,
          subDetails.eoriNumber.map(Eori),
          SafeId(id.id)
        )
      case _ =>
        logger.error("No contact details available to save")
        Future.failed(throw new IllegalStateException("No contact details available to save)"))
    }
  }

  private def callHandle(
    subscriptionResult: SubscriptionResult,
    recipientDetails: RecipientDetails,
    sapNumber: TaxPayerId,
    eori: Option[Eori],
    safeId: SafeId
  )(implicit hc: HeaderCarrier) =
    subscriptionResult match {
      case s: SubscriptionSuccessful =>
        handleSubscriptionService.handleSubscription(
          s.formBundleId,
          recipientDetails,
          sapNumber,
          eori,
          s.emailVerificationTimestamp,
          safeId
        )
      case p: SubscriptionPending =>
        handleSubscriptionService.handleSubscription(
          p.formBundleId,
          recipientDetails,
          sapNumber,
          eori,
          p.emailVerificationTimestamp,
          safeId
        )
      case _ => Future.successful(())
    }

}
