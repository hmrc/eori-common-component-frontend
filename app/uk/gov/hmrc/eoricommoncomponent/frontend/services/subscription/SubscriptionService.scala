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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.SubscriptionServiceConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.MessagingServiceParam
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.SubscriptionCreateResponse._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.logging.CdsLogger
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionService @Inject()(connector: SubscriptionServiceConnector)(implicit ec: ExecutionContext) {

  def subscribe(
    registration: RegistrationDetails,
    subscription: SubscriptionDetails,
    cdsOrganisationType: Option[CdsOrganisationType],
    journey: Journey.Value
  )(implicit hc: HeaderCarrier): Future[SubscriptionResult] =
    subscribeWithConnector(createRequest(registration, subscription, cdsOrganisationType))

  def subscribeWithMandatoryOnly(
    registration: RegistrationDetails,
    subscription: SubscriptionDetails,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier): Future[SubscriptionResult] = {
    val email =
      if (journey == Journey.Register) subscription.contactDetails.map(_.emailAddress) else subscription.email
    val request = SubscriptionRequest(SubscriptionCreateRequest(registration, subscription, email))
    subscribeWithConnector(request)
  }

  def existingReg(registerWithEoriAndIdResponse: RegisterWithEoriAndIdResponse, eori: Eori, capturedEmail: String)(
    implicit hc: HeaderCarrier
  ): Future[SubscriptionResult] =
    registerWithEoriAndIdResponse.responseDetail.flatMap(_.responseData) match {
      case Some(data) =>
        val request = SubscriptionRequest(SubscriptionCreateRequest(data, eori, capturedEmail))
        subscribeWithConnector(request)
      case _ =>
        val err = "REGO6 ResponseData is non existent. This is required to populate subscription request"
        CdsLogger.warn(err)
        Future.successful(throw new IllegalStateException(err))
    }

  def createRequest(
    reg: RegistrationDetails,
    subscription: SubscriptionDetails,
    cdsOrgType: Option[CdsOrganisationType]
  ): SubscriptionRequest =
    reg match {
      case individual: RegistrationDetailsIndividual =>
        val dob = subscription.dateOfBirth getOrElse individual.dateOfBirth
        SubscriptionCreateRequest(individual, subscription, cdsOrgType, dob)

      case org: RegistrationDetailsOrganisation =>
        val doe = subscription.dateEstablished
        SubscriptionCreateRequest(
          org,
          subscription,
          cdsOrgType,
          doe.getOrElse(
            throw new IllegalStateException("Date Established must be present for an organisation subscription")
          )
        ) ensuring (subscription.sicCode.isDefined, "SicCode/Principal Economic Activity must be present for an organisation subscription")
      case _ => throw new IllegalStateException("Incomplete cache cannot complete journey")
    }

  private def subscribeWithConnector(
    request: SubscriptionRequest
  )(implicit hc: HeaderCarrier): Future[SubscriptionResult] =
    connector.subscribe(request) map { response =>
      val responseCommon = response.subscriptionCreateResponse.responseCommon
      val processingDate = responseCommon.processingDate.toString("d MMM y")
      val emailVerificationTimestamp =
        request.subscriptionCreateRequest.requestDetail.contactInformation.flatMap(_.emailVerificationTimestamp)

      extractPosition(responseCommon.returnParameters) match {
        case MessagingServiceParam.Generate | MessagingServiceParam.Link =>
          SubscriptionSuccessful(
            Eori(response.subscriptionCreateResponse.responseDetail.get.EORINo),
            formBundleId(response),
            processingDate,
            emailVerificationTimestamp
          )
        case MessagingServiceParam.Pending =>
          SubscriptionPending(formBundleId(response), processingDate, emailVerificationTimestamp)
        case MessagingServiceParam.Fail if responseCommon.statusText.contains(EoriAlreadyExists) =>
          SubscriptionFailed(EoriAlreadyExists, processingDate)
        case MessagingServiceParam.Fail if responseCommon.statusText.contains(RequestNotProcessed) =>
          SubscriptionFailed(RequestNotProcessed, processingDate)
        case MessagingServiceParam.Fail if responseCommon.statusText.contains(EoriAlreadyAssociated) =>
          SubscriptionFailed(EoriAlreadyAssociated, processingDate)
        case MessagingServiceParam.Fail if responseCommon.statusText.contains(SubscriptionInProgress) =>
          SubscriptionFailed(SubscriptionInProgress, processingDate)
        case MessagingServiceParam.Fail =>
          SubscriptionFailed("Response status of FAIL returned for a SUB02: Create Subscription.", processingDate)
      }
    }

  private def extractPosition: List[MessagingServiceParam] => String = { params =>
    extractValueFromMessageParams(MessagingServiceParam.positionParamName, params)
  }

  private def formBundleId: SubscriptionResponse => String = { resp =>
    val params = resp.subscriptionCreateResponse.responseCommon.returnParameters
    extractValueFromMessageParams(MessagingServiceParam.formBundleIdParamName, params)
  }

  private def extractValueFromMessageParams: (String, List[MessagingServiceParam]) => String = { (name, params) =>
    params
      .find(_.paramName == name)
      .fold(throw new IllegalStateException(s"$name parameter is missing in subscription create response"))(
        _.paramValue
      )
  }
}
