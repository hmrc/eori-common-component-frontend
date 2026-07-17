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

package uk.gov.hmrc.eoricommoncomponent.frontend.connector

import play.api.Logger
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.libs.json.*
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.{SubscriptionDisplayResponse, SubscriptionDisplayResponseBusinessErrorWrapper, SubscriptionDisplayResponseHolder}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.events.{SubscriptionDisplay, SubscriptionDisplayResult, SubscriptionDisplaySubmitted}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import java.net.{URI, URL, URLEncoder}
import java.time.LocalDateTime.ofInstant
import java.time.ZoneId.of
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class SUB09SubscriptionDisplayConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig, audit: Auditable)(
  implicit ec: ExecutionContext
) {

  private val logger  = Logger(this.getClass)
  private val baseUrl = s"${appConfig.getServiceUrl("subscription-display")}"

  private type Subscription09Response =
    SubscriptionDisplayResponseHolder | SubscriptionDisplayResponseBusinessErrorWrapper

  given Reads[Subscription09Response] with

    def reads(json: JsValue): JsResult[Subscription09Response] =
      json.validate[SubscriptionDisplayResponseHolder]
        .orElse(json.validate[SubscriptionDisplayResponseBusinessErrorWrapper])

  def subscriptionDisplay(sub09Request: Seq[(String, String)], originatingService: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[EoriHttpResponse, SubscriptionDisplayResponse]] = {

    val url = URI.create(s"$baseUrl?${makeQueryString(sub09Request)}").toURL

    logRequest(sub09Request, hc, url)

    val httpRequest: RequestBuilder = httpClient
      .get(url)
      .transform(_.addHttpHeaders(sub09Request: _*))
      .setHeader(AUTHORIZATION -> appConfig.internalAuthToken)

    httpRequest.execute[Subscription09Response] map {
      case sub09DispRespHolder: SubscriptionDisplayResponseHolder =>
        logResponse(sub09DispRespHolder)
        auditCall(url.toString, sub09Request, originatingService, sub09DispRespHolder)
        Right(sub09DispRespHolder.subscriptionDisplayResponse)
      case businessErrorWrapper: SubscriptionDisplayResponseBusinessErrorWrapper =>
        logBusinessErrorResponse(url, businessErrorWrapper)
        Left(BusinessErrorResponse)
    } recover {
      case NonFatal(e) =>
        logError(url, e)
        Left(ServiceUnavailableResponse)
    }
  }

  // $COVERAGE-OFF$
  private def logRequest(sub09Request: Seq[(String, String)], hc: HeaderCarrier, url: URL): Unit =
    logger.debug(s"SubscriptionDisplay SUB09: $url, body: $sub09Request and hc: $hc")
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  private def logResponse(resp: SubscriptionDisplayResponseHolder): Unit =
    logger.debug(s"SubscriptionDisplay SUB09: responseCommon: ${resp.subscriptionDisplayResponse.responseCommon}")
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  private def logError(url: URL, e: Throwable): Unit =
    logger.error(s"SubscriptionDisplay SUB09 failed. url: $url, error: $e")
  // $COVERAGE-ON$

  // $COVERAGE-OFF$
  private def logBusinessErrorResponse(url: URL, error: SubscriptionDisplayResponseBusinessErrorWrapper): Unit =
    logger.error(s"SubscriptionDisplay SUB09 failed. url: $url," +
      s"error: ${error.subscriptionDisplayResponse.responseCommon.statusText}, " +
      s"processing date: ${ofInstant(error.subscriptionDisplayResponse.responseCommon.processingDate, of("Europe/London"))}")
  // $COVERAGE-ON$

  private def auditCall(
    url: String,
    request: Seq[(String, String)],
    serviceName: String,
    response: SubscriptionDisplayResponseHolder
  )(implicit hc: HeaderCarrier): Unit = {
    val auditRequest                 = request :+ ("originatingService" -> serviceName)
    val subscriptionDisplaySubmitted = SubscriptionDisplaySubmitted.applyAndAlignKeys(auditRequest.toMap)
    val subscriptionDisplayResult    = SubscriptionDisplayResult(response)

    audit.sendExtendedDataEvent(
      transactionName = "ecc-subscription-display",
      path = url,
      details = Json.toJson(SubscriptionDisplay(subscriptionDisplaySubmitted, subscriptionDisplayResult)),
      eventType = "SubscriptionDisplay"
    )
  }

  private def makeQueryString(queryParams: Seq[(String, String)]) = {
    val paramPairs = queryParams.map { case (k, v) => s"$k=${URLEncoder.encode(v, "utf-8")}" }
    if (paramPairs.isEmpty) "" else paramPairs.mkString("&")
  }

}
