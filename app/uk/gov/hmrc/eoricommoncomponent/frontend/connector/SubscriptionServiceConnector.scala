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

package uk.gov.hmrc.eoricommoncomponent.frontend.connector

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.{
  SubscriptionRequest,
  SubscriptionResponse
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.events.{Subscription, SubscriptionResult, SubscriptionSubmitted}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SubscriptionServiceConnector @Inject() (http: HttpClient, appConfig: AppConfig, audit: Auditable)(implicit
  ec: ExecutionContext
) {

  private val logger = Logger(this.getClass)
  private val url    = appConfig.getServiceUrl("subscribe")

  def subscribe(request: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[SubscriptionResponse] = {

    // $COVERAGE-OFF$Loggers
    logger.debug(s"[Subscribe SUB02: $url, body: $request and hc: $hc")
    // $COVERAGE-ON

    http.POST[SubscriptionRequest, SubscriptionResponse](url, request) map { response =>
      // $COVERAGE-OFF$Loggers
      logger.debug(s"[Subscribe SUB02: response: $response")
      // $COVERAGE-ON

      auditCall(url, request, response)
      response
    } recoverWith {
      case e: BadRequestException =>
        logger.warn(
          s"Subscribe SUB02 request failed for acknowledgementReference : ${request.subscriptionCreateRequest.requestCommon.acknowledgementReference}. Reason: $e"
        )
        Future.failed(e)
      case NonFatal(e) =>
        logger.warn(
          s"Subscribe SUB02 request failed for acknowledgementReference : ${request.subscriptionCreateRequest.requestCommon.acknowledgementReference}. Reason: $e"
        )
        Future.failed(e)
    }
  }

  private def auditCall(url: String, request: SubscriptionRequest, response: SubscriptionResponse)(implicit
    hc: HeaderCarrier
  ): Unit = {
    val subscriptionRequest  = SubscriptionSubmitted(request)
    val subscriptionResponse = SubscriptionResult(response)

    audit.sendExtendedDataEvent(
      transactionName = "ecc-subscription",
      path = url,
      details = Json.toJson(Subscription(subscriptionRequest, subscriptionResponse)),
      eventType = "Subscription"
    )
  }

}
