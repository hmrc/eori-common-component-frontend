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
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.{
  SubscriptionDisplayResponse,
  SubscriptionDisplayResponseHolder
}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SUB09SubscriptionDisplayConnector @Inject() (http: HttpClient, appConfig: AppConfig, audit: Auditable)(implicit
  ec: ExecutionContext
) {

  private val logger = Logger(this.getClass)
  private val url    = appConfig.getServiceUrl("subscription-display")

  def subscriptionDisplay(
    sub09Request: Seq[(String, String)]
  )(implicit hc: HeaderCarrier): Future[Either[EoriHttpResponse, SubscriptionDisplayResponse]] = {
    auditCallRequest(url, sub09Request)
    http.GET[SubscriptionDisplayResponseHolder](url, sub09Request) map { resp =>
      logger.info(s"subscription-display SUB09 successful. url: $url")
      auditCallResponse(url, resp)
      Right(resp.subscriptionDisplayResponse)
    } recover {
      case NonFatal(e) =>
        logger.error(s"subscription-display SUB09 failed. url: $url, error: $e")
        Left(ServiceUnavailableResponse)
    }
  }

  private def auditCallRequest(url: String, request: Seq[(String, String)])(implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(
      transactionName = "customs-subscription-display",
      path = url,
      detail = request.toMap,
      eventType = "SubscriptionDisplaySubmitted"
    )

  private def auditCallResponse(url: String, response: SubscriptionDisplayResponseHolder)(implicit
    hc: HeaderCarrier
  ): Unit =
    audit.sendDataEvent(
      transactionName = "customs-subscription-display",
      path = url,
      detail = response.subscriptionDisplayResponse
        .keyValueMap(),
      eventType = "SubscriptionDisplayResult"
    )

}
