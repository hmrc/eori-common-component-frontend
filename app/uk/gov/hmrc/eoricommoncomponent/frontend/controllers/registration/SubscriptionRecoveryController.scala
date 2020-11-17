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
import play.api.i18n.Messages
import play.api.mvc.{Action, _}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.SUB09SubscriptionDisplayConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.SubscriptionDisplayResponse
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.RecipientDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RandomUUIDGenerator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  HandleSubscriptionService,
  SubscriptionDetailsService,
  TaxEnrolmentsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.error_template
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionRecoveryController @Inject() (
  authAction: AuthAction,
  handleSubscriptionService: HandleSubscriptionService,
  taxEnrolmentService: TaxEnrolmentsService,
  sessionCache: SessionCache,
  SUB09Connector: SUB09SubscriptionDisplayConnector,
  mcc: MessagesControllerComponents,
  errorTemplateView: error_template,
  uuidGenerator: RandomUUIDGenerator,
  requestSessionData: RequestSessionData,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  // End of subscription recovery journey
  def complete(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithServiceAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        val isRowF           = Future.successful(UserLocation.isRow(requestSessionData))
        val journeyF         = Future.successful(journey)
        val cachedCustomsIdF = subscriptionDetailsService.cachedCustomsId
        val result = for {
          isRow    <- isRowF
          journey  <- journeyF
          customId <- if (isRow) cachedCustomsIdF else Future.successful(None)
        } yield (journey, isRow, customId) match {
          case (Journey.Subscribe, true, Some(_)) => subscribeForCDS(service)    // UK journey
          case (Journey.Subscribe, true, None)    => subscribeForCDSROW(service) // subscribeForCDSROW //ROW
          case (Journey.Subscribe, false, _)      => subscribeForCDS(service)    // UK Journey
          case _                                  => subscribeGetAnEori(service) // Journey Get An EORI
        }
        result.flatMap(identity)
    }

  private def subscribeGetAnEori(
    service: Service
  )(implicit ec: ExecutionContext, request: Request[AnyContent]): Future[Result] = {
    val result = for {
      registrationDetails <- sessionCache.registrationDetails
      safeId          = registrationDetails.safeId.id
      queryParameters = ("taxPayerID" -> safeId) :: buildQueryParams
      sub09Result  <- SUB09Connector.subscriptionDisplay(queryParameters)
      sub01Outcome <- sessionCache.sub01Outcome
    } yield sub09Result match {
      case Right(subscriptionDisplayResponse) =>
        val email = subscriptionDisplayResponse.responseDetail.contactInformation
          .flatMap(_.emailAddress)
          .getOrElse(throw new IllegalStateException("Register Journey: No email address available."))
        val eori = subscriptionDisplayResponse.responseDetail.EORINo
          .getOrElse(throw new IllegalStateException("no eori found in the response"))
        onSUB09Success(
          sub01Outcome.processedDate,
          email,
          safeId,
          Eori(eori),
          subscriptionDisplayResponse,
          service,
          Journey.Register
        )(Redirect(Sub02Controller.end(service)))
      case Left(_) =>
        Future.successful(ServiceUnavailable(errorTemplateView()))
    }
    result.flatMap(identity)
  }

  private def subscribeForCDS(
    service: Service
  )(implicit ec: ExecutionContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Result] = {
    val result = for {
      subscriptionDetails <- sessionCache.subscriptionDetails
      eori = subscriptionDetails.eoriNumber.getOrElse(throw new IllegalStateException("no eori found in the cache"))
      registerWithEoriAndIdResponse <- sessionCache.registerWithEoriAndIdResponse
      safeId = registerWithEoriAndIdResponse.responseDetail
        .flatMap(_.responseData.map(_.SAFEID))
        .getOrElse(throw new IllegalStateException("no SAFEID found in the response"))
      queryParameters = ("EORI" -> eori) :: buildQueryParams
      sub09Result  <- SUB09Connector.subscriptionDisplay(queryParameters)
      sub01Outcome <- sessionCache.sub01Outcome
      email        <- sessionCache.email
    } yield sub09Result match {
      case Right(subscriptionDisplayResponse) =>
        onSUB09Success(
          sub01Outcome.processedDate,
          email,
          safeId,
          Eori(eori),
          subscriptionDisplayResponse,
          service,
          Journey.Subscribe
        )(Redirect(Sub02Controller.migrationEnd(service)))
      case Left(_) =>
        Future.successful(ServiceUnavailable(errorTemplateView()))
    }
    result.flatMap(identity)
  }

  private def subscribeForCDSROW(
    service: Service
  )(implicit ec: ExecutionContext, request: Request[AnyContent]): Future[Result] = {
    val result = for {
      subscriptionDetails <- sessionCache.subscriptionDetails
      registrationDetails <- sessionCache.registrationDetails
      eori            = subscriptionDetails.eoriNumber.getOrElse(throw new IllegalStateException("no eori found in the cache"))
      safeId          = registrationDetails.safeId.id
      queryParameters = ("EORI" -> eori) :: buildQueryParams
      sub09Result  <- SUB09Connector.subscriptionDisplay(queryParameters)
      sub01Outcome <- sessionCache.sub01Outcome
      email        <- sessionCache.email
    } yield sub09Result match {
      case Right(subscriptionDisplayResponse) =>
        onSUB09Success(
          sub01Outcome.processedDate,
          email,
          safeId,
          Eori(eori),
          subscriptionDisplayResponse,
          service,
          Journey.Subscribe
        )(Redirect(Sub02Controller.migrationEnd(service)))
      case Left(_) =>
        Future.successful(ServiceUnavailable(errorTemplateView()))
    }
    result.flatMap(identity)
  }

  private def buildQueryParams: List[(String, String)] =
    List("regime" -> "CDS", "acknowledgementReference" -> uuidGenerator.generateUUIDAsString)

  private def onSUB09Success(
    processedDate: String,
    email: String,
    safeId: String,
    eori: Eori,
    subscriptionDisplayResponse: SubscriptionDisplayResponse,
    service: Service,
    journey: Journey.Value
  )(redirect: => Result)(implicit headerCarrier: HeaderCarrier, messages: Messages): Future[Result] = {
    val formBundleId =
      subscriptionDisplayResponse.responseCommon.returnParameters
        .flatMap(_.find(_.paramName.equals("ETMPFORMBUNDLENUMBER")).map(_.paramValue))
        .getOrElse(throw new IllegalStateException("NO ETMPFORMBUNDLENUMBER specified"))

    //As the result of migration person of contact is likely to be empty use string Customer
    val recipientFullName =
      subscriptionDisplayResponse.responseDetail.contactInformation.flatMap(_.personOfContact).getOrElse("Customer")
    val name = subscriptionDisplayResponse.responseDetail.CDSFullName
    val emailVerificationTimestamp =
      subscriptionDisplayResponse.responseDetail.contactInformation.flatMap(_.emailVerificationTimestamp)

    sessionCache
      .saveSub02Outcome(Sub02Outcome(processedDate, name, subscriptionDisplayResponse.responseDetail.EORINo))
      .flatMap(
        _ =>
          handleSubscriptionService
            .handleSubscription(
              formBundleId,
              RecipientDetails(service, journey, email, recipientFullName, Some(name), Some(processedDate)),
              TaxPayerId(safeId),
              Some(eori),
              emailVerificationTimestamp,
              SafeId(safeId)
            )
            .flatMap { _ =>
              if (journey == Journey.Subscribe)
                issuerCall(eori, formBundleId, subscriptionDisplayResponse, service)(redirect)
              else
                Future.successful(redirect)
            }
      )
  }

  private def issuerCall(
    eori: Eori,
    formBundleId: String,
    subscriptionDisplayResponse: SubscriptionDisplayResponse,
    service: Service
  )(redirect: => Result)(implicit headerCarrier: HeaderCarrier): Future[Result] = {
    val dateOfEstablishment = subscriptionDisplayResponse.responseDetail.dateOfEstablishment
    taxEnrolmentService.issuerCall(formBundleId, eori, dateOfEstablishment, service).map {
      case NO_CONTENT => redirect
      case _          => throw new IllegalArgumentException("Tax enrolment call failed")
    }

  }

}
