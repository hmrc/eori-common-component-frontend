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

package uk.gov.hmrc.eoricommoncomponent.frontend.connector

import play.api.Logger
import play.api.http.HeaderNames._
import play.api.libs.json.Json
import play.mvc.Http.MimeTypes
import play.mvc.Http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.NotifyRcmRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.models.events.NotifyRcm
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class NotifyRcmConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig, audit: Auditable)(implicit
  ec: ExecutionContext
) {

  private val logger = Logger(this.getClass)

  def notifyRCM(request: NotifyRcmRequest)(implicit hc: HeaderCarrier): Future[Unit] = {
    val url = url"${appConfig.handleSubscriptionBaseUrl}/notify/rcm"

    val httpRequest = httpClient
      .post(url)
      .withBody(Json.toJson(request))
      .setHeader(ACCEPT -> "application/vnd.hmrc.1.0+json")
      .setHeader(CONTENT_TYPE -> MimeTypes.JSON)
      .setHeader(AUTHORIZATION -> appConfig.internalAuthToken)

    httpRequest.execute[HttpResponse] map { response =>
      auditCallResponse(url.toString, request, response)
      response.status match {
        case OK | NO_CONTENT => ()
        case _               => throw new BadRequestException(s"Status:${response.status}")
      }
    } recoverWith {
      case e: BadRequestException =>
        // $COVERAGE-OFF$Loggers
        logger.warn(s"request failed with BAD_REQUEST status for call to $url: ${e.getMessage}", e)
        // $COVERAGE-ON
        Future.failed(e)
      case NonFatal(e) =>
        // $COVERAGE-OFF$Loggers
        logger.warn(s"request failed for call to $url: ${e.getMessage}", e)
        // $COVERAGE-ON
        Future.failed(e)
    }
  }

  private def auditCallResponse(url: String, request: NotifyRcmRequest, response: HttpResponse)(implicit
    hc: HeaderCarrier
  ): Unit =
    Future.successful {
      audit.sendExtendedDataEvent(
        transactionName = "customs-rcm-email",
        path = url,
        details = Json.toJson(NotifyRcm(request, response)),
        eventType = "RcmEmailConfirmation"
      )
    }

}
