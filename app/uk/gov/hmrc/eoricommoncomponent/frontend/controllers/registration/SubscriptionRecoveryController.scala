/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.i18n.Messages
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.SUB09SubscriptionDisplayConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.RegistrationInfoRequest.EORI
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.SubscriptionDisplayResponse
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.RecipientDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RandomUUIDGenerator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{
  DataUnavailableException,
  RequestSessionData,
  SessionCache
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  HandleSubscriptionService,
  SubscriptionDetailsService,
  TaxEnrolmentsService,
  UpdateEmailError,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{email_error_template, error_template}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDate, LocalDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

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
  subscriptionDetailsService: SubscriptionDetailsService,
  updateVerifiedEmailService: UpdateVerifiedEmailService,
  emailErrorPage: email_error_template
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  // End of subscription recovery journey
  def complete(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithServiceAction {
      implicit request => (_: LoggedInUserWithEnrolments) =>
        val isRowF           = Future.successful(UserLocation.isRow(requestSessionData))
        val cachedCustomsIdF = subscriptionDetailsService.cachedCustomsId
        val result =
          for {
            isRow    <- isRowF
            customId <- if (isRow) cachedCustomsIdF else Future.successful(None)
          } yield (isRow, customId) match {
            case (true, Some(_)) => subscribeForCDS(service)    // UK journey
            case (true, None)    => subscribeForCDSROW(service) // subscribeForCDSROW //ROW
            case (false, _)      => subscribeForCDS(service)    // UK Journey
          }
        result.flatMap(identity)
    }

  private def subscribeForCDS(
    service: Service
  )(implicit ec: ExecutionContext, request: Request[AnyContent], hc: HeaderCarrier): Future[Result] = {
    val result =
      for {
        subscriptionDetails <- sessionCache.subscriptionDetails
        eori = subscriptionDetails.eoriNumber.getOrElse(throw DataUnavailableException("no eori found in the cache"))
        registerWithEoriAndIdResponse <- sessionCache.registerWithEoriAndIdResponse
        safeId = registerWithEoriAndIdResponse.responseDetail
          .flatMap(_.responseData.map(_.SAFEID))
          .getOrElse(throw new IllegalStateException("no SAFEID found in the response"))
        queryParameters = (EORI -> eori) :: buildQueryParams
        sub09Result  <- SUB09Connector.subscriptionDisplay(queryParameters, service.code)
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
            getDateOfBirthOrDateOfEstablishment(
              subscriptionDisplayResponse,
              subscriptionDetails.dateEstablished,
              subscriptionDetails.nameDobDetails.map(_.dateOfBirth)
            ),
            service
          )(Redirect(Sub02Controller.migrationEnd(service)))
        case Left(_) =>
          Future.successful(InternalServerError(errorTemplateView(service)))
      }
    result.flatMap(identity)
  }

  private def subscribeForCDSROW(
    service: Service
  )(implicit ec: ExecutionContext, request: Request[AnyContent]): Future[Result] = {
    val result =
      for {
        subscriptionDetails <- sessionCache.subscriptionDetails
        registrationDetails <- sessionCache.registrationDetails
        eori   = subscriptionDetails.eoriNumber.getOrElse(throw DataUnavailableException("no eori found in the cache"))
        safeId = registrationDetails.safeId.id
        queryParameters = (EORI -> eori) :: buildQueryParams
        sub09Result  <- SUB09Connector.subscriptionDisplay(queryParameters, service.code)
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
            getDateOfBirthOrDateOfEstablishment(
              subscriptionDisplayResponse,
              subscriptionDetails.dateEstablished,
              subscriptionDetails.nameDobDetails.map(_.dateOfBirth)
            ),
            service
          )(Redirect(Sub02Controller.migrationEnd(service)))
        case Left(_) =>
          Future.successful(InternalServerError(errorTemplateView(service)))
      }
    result.flatMap(identity)
  }

  private def buildQueryParams: List[(String, String)] =
    List("regime" -> "CDS", "acknowledgementReference" -> uuidGenerator.generateUUIDAsString)

  private case class SubscriptionInformation(
    processedDate: String,
    email: String,
    emailVerificationTimestamp: Option[LocalDateTime],
    formBundleId: String,
    recipientFullName: String,
    name: String,
    eori: Eori,
    safeId: SafeId,
    dateOfEstablishment: Option[LocalDate]
  )

  private def onSUB09Success(
    processedDate: String,
    email: String,
    safeId: String,
    eori: Eori,
    subscriptionDisplayResponse: SubscriptionDisplayResponse,
    dateOfEstablishment: Option[LocalDate],
    service: Service
  )(redirect: => Result)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    val formBundleId =
      subscriptionDisplayResponse.responseCommon.returnParameters
        .flatMap(_.find(_.paramName.equals("ETMPFORMBUNDLENUMBER")).map(_.paramValue))
        .getOrElse(throw new IllegalStateException("NO ETMPFORMBUNDLENUMBER specified"))

    // As the result of migration person of contact is likely to be empty use string Customer
    val recipientFullName =
      subscriptionDisplayResponse.responseDetail.contactInformation.flatMap(_.personOfContact).getOrElse("Customer")
    val name = subscriptionDisplayResponse.responseDetail.CDSFullName
    val emailVerificationTimestamp =
      subscriptionDisplayResponse.responseDetail.contactInformation.flatMap(_.emailVerificationTimestamp)

    /*
     * When subscribing for CDS as a safeguard we're using historical CDS formBundleId enrichment.
     * See: https://github.com/hmrc/customs-rosm-frontend/blob/477a1e1432938b004c463444ed851e69fc6214b5/app/uk/gov/hmrc/customs/rosmfrontend/controllers/registration/SubscriptionRecoveryController.scala#L220
     *
     * For other non-cds services we can use existing enrichment algorithm.
     * */
    def enrichFormBundleId(serviceCode: String, formBundleId: String) =
      if (Service.cds.code.equalsIgnoreCase(serviceCode))
        s"$formBundleId${Random.nextInt(1000)}$serviceCode"
      else
        formBundleId + service.code + "-" + (100000 + Random.nextInt(900000)).toString

    val subscriptionInformation = SubscriptionInformation(
      processedDate,
      email,
      emailVerificationTimestamp,
      enrichFormBundleId(service.code, formBundleId),
      recipientFullName,
      name,
      eori,
      SafeId(safeId),
      dateOfEstablishment
    )

    completeEnrolment(service, subscriptionInformation)(redirect)
  }

  private def completeEnrolment(service: Service, subscriptionInformation: SubscriptionInformation)(
    redirect: => Result
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    (for {
      // Update Recovered Subscription Information
      _ <- updateSubscription(subscriptionInformation)
      // Update Email
      _ <- updateEmail(subscriptionInformation, service)
      // Subscribe Call for enrolment
      _ <- subscribe(service, subscriptionInformation)
      // Issuer Call for enrolment
      res <- issue(service, subscriptionInformation)
    } yield res match {
      case NO_CONTENT => redirect
      case _          => throw new IllegalArgumentException("Tax Enrolment issuer call failed")
    }) recover {
      case UpdateEmailRetryException => Ok(emailErrorPage(service))
    }

  private def updateEmail(subscriptionInformation: SubscriptionInformation, service: Service)(implicit
    hc: HeaderCarrier
  ): Future[Boolean] =
    if (service.enrolmentKey == Service.cds.enrolmentKey)
      updateVerifiedEmailService
        .updateVerifiedEmail(newEmail = subscriptionInformation.email, eori = subscriptionInformation.eori.id)
        .flatMap {
          case Right(_)                  => Future.successful(true)
          case Left(UpdateEmailError(_)) => Future.failed(UpdateEmailRetryException)
          case Left(error) => Future.failed(new Exception(s"UpdateEmail failed with status: ${error.message}"))
        }
    else Future.successful(false)

  private def updateSubscription(subscriptionInformation: SubscriptionInformation)(implicit request: Request[_]) =
    sessionCache.saveSub02Outcome(
      Sub02Outcome(
        subscriptionInformation.processedDate,
        subscriptionInformation.name,
        Some(subscriptionInformation.eori.id)
      )
    )

  private def subscribe(service: Service, subscriptionInformation: SubscriptionInformation)(implicit
    hc: HeaderCarrier,
    messages: Messages
  ): Future[Unit] =
    handleSubscriptionService
      .handleSubscription(
        subscriptionInformation.formBundleId,
        RecipientDetails(
          service,
          subscriptionInformation.email,
          subscriptionInformation.recipientFullName,
          Some(subscriptionInformation.name),
          Some(subscriptionInformation.processedDate)
        ),
        TaxPayerId(subscriptionInformation.safeId.id),
        Some(subscriptionInformation.eori),
        subscriptionInformation.emailVerificationTimestamp,
        subscriptionInformation.safeId
      )

  private def issue(service: Service, subscriptionInformation: SubscriptionInformation)(implicit
    hc: HeaderCarrier
  ): Future[Int] =
    taxEnrolmentService.issuerCall(
      subscriptionInformation.formBundleId,
      subscriptionInformation.eori,
      subscriptionInformation.dateOfEstablishment,
      service
    )

  private def getDateOfBirthOrDateOfEstablishment(
    response: SubscriptionDisplayResponse,
    dateOfEstablishmentCaptured: Option[LocalDate],
    dateOfBirthCaptured: Option[LocalDate]
  )(implicit request: Request[AnyContent]): Option[LocalDate] = {
    val isIndividualOrSoleTrader = requestSessionData.isIndividualOrSoleTrader
    val dateOfEstablishment      = response.responseDetail.dateOfEstablishment // Date we hold
    (isIndividualOrSoleTrader, dateOfEstablishment, dateOfEstablishmentCaptured, dateOfBirthCaptured) match {
      case (_, Some(date), _, _)     => Some(date)
      case (false, _, Some(date), _) => Some(date)
      case (true, _, _, Some(date))  => Some(date)
      case _                         => throw MissingDateException()
    }
  }

}

case class MissingDateException(msg: String = "Missing date of enrolment or birth") extends Exception(msg)

case object UpdateEmailRetryException
    extends Exception("Email update failed due to a downstream batch process. User can retry later.")
