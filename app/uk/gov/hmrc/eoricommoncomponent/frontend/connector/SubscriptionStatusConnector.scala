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
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.logging.CdsLogger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionStatusConnector @Inject()(http: HttpClient, appConfig: AppConfig, audit: Auditable)(implicit ec: ExecutionContext)
    extends CaseClassAuditHelper {

  private val url = appConfig.getServiceUrl("subscription-status")
  private val loggerComponentId = "SubscriptionStatusConnector"

  def status(request: SubscriptionStatusQueryParams)(implicit hc: HeaderCarrier): Future[SubscriptionStatusResponse] =
    http.GET[SubscriptionStatusResponseHolder](url, request.queryParams) map { resp =>
      CdsLogger.info(
        s"[$loggerComponentId][status] SUB01 successful. url: $url, response status: ${resp.subscriptionStatusResponse.responseCommon.status}"
      )
      auditCall(url, request, resp)
      resp.subscriptionStatusResponse
    } recover {
      case e: Throwable =>
        CdsLogger.error(s"[$loggerComponentId][status] SUB01 failed. url: $url, error: $e", e)
        throw e
    }

  private def auditCall(
    url: String,
    request: SubscriptionStatusQueryParams,
    response: SubscriptionStatusResponseHolder
  )(implicit hc: HeaderCarrier): Unit =
    audit.sendExtendedDataEvent(
      transactionName = "customs-subscription-status",
      path = url,
      details = Json.toJson(RequestResponse(request.jsObject(), response.jsObject())),
      eventType = "displayCustomsSubscriptionStatus"
    )
}
