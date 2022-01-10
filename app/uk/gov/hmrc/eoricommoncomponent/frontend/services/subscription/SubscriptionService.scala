/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.format.DateTimeFormatter

import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.SubscriptionServiceConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.FeatureFlags
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.MessagingServiceParam
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.SubscriptionCreateResponse._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionService @Inject() (connector: SubscriptionServiceConnector, featureFlags: FeatureFlags)(implicit
  ec: ExecutionContext
) {

  private val logger = Logger(this.getClass)

  private def maybe(service: Service): Option[Service] = if (featureFlags.sub02UseServiceName) Some(service) else None

  def subscribeWithMandatoryOnly(
    registration: RegistrationDetails,
    subscription: SubscriptionDetails,
    service: Service,
    cachedEmail: Option[String]
  )(implicit hc: HeaderCarrier): Future[SubscriptionResult] = {
    val request = SubscriptionRequest(
      SubscriptionCreateRequest(registration, subscription, cachedEmail, maybe(service))
    )
    subscribeWithConnector(request)
  }

  def existingReg(
    registerWithEoriAndIdResponse: RegisterWithEoriAndIdResponse,
    subscriptionDetails: SubscriptionDetails,
    capturedEmail: String,
    service: Service
  )(implicit hc: HeaderCarrier): Future[SubscriptionResult] =
    registerWithEoriAndIdResponse.responseDetail.flatMap(_.responseData) match {
      case Some(data) =>
        val request = SubscriptionRequest(
          SubscriptionCreateRequest(data, subscriptionDetails, capturedEmail, maybe(service))
        )
        subscribeWithConnector(request)
      case _ =>
        val err = "REGO6 ResponseData is non existent. This is required to populate subscription request"
        logger.warn(err)
        Future.successful(throw new IllegalStateException(err))
    }

  private def subscribeWithConnector(
    request: SubscriptionRequest
  )(implicit hc: HeaderCarrier): Future[SubscriptionResult] =
    connector.subscribe(request) map { response =>
      val responseCommon = response.subscriptionCreateResponse.responseCommon
      val processingDate = DateTimeFormatter.ofPattern("d MMM y").format(responseCommon.processingDate)
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
        case MessagingServiceParam.Fail if responseCommon.statusText.exists(_.equalsIgnoreCase(EoriAlreadyExists)) =>
          SubscriptionFailed(EoriAlreadyExists, processingDate)
        case MessagingServiceParam.Fail if responseCommon.statusText.exists(_.equalsIgnoreCase(RequestNotProcessed)) =>
          SubscriptionFailed(RequestNotProcessed, processingDate)
        case MessagingServiceParam.Fail
            if responseCommon.statusText.exists(_.equalsIgnoreCase(EoriAlreadyAssociated)) =>
          SubscriptionFailed(EoriAlreadyAssociated, processingDate)
        case MessagingServiceParam.Fail
            if responseCommon.statusText.exists(_.equalsIgnoreCase(SubscriptionInProgress)) =>
          SubscriptionFailed(SubscriptionInProgress, processingDate)
        case MessagingServiceParam.Fail =>
          val message =
            s"Response status of FAIL returned for a SUB02: Create Subscription.${responseCommon.statusText.map(
              text => s" $text"
            ).getOrElse("")}"
          logger.error(message)
          SubscriptionFailed(message, processingDate)
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
