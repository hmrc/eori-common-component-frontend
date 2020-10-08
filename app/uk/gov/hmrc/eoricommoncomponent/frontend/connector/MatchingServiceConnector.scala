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
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.matching._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MatchingServiceConnector @Inject() (http: HttpClient, appConfig: AppConfig, audit: Auditable)(implicit
  ec: ExecutionContext
) {

  private val logger = Logger(this.getClass)

  private val url  = appConfig.getServiceUrl("register-with-id")
  val NoMatchFound = "002 - No Match Found"

  def handleResponse(response: MatchingResponse): Option[MatchingResponse] = {
    val statusTxt = response.registerWithIDResponse.responseCommon.statusText
    if (statusTxt.exists(_.equalsIgnoreCase(NoMatchFound))) None
    else Some(response)
  }

  def lookup(req: MatchingRequestHolder)(implicit hc: HeaderCarrier): Future[Option[MatchingResponse]] = {
    logger.info(
      s"[lookup] REG01 postUrl: $url,  acknowledgement ref: ${req.registerWithIDRequest.requestCommon.acknowledgementReference}"
    )
    auditCallRequest(url, req)
    http.POST[MatchingRequestHolder, MatchingResponse](url, req) map { resp =>
      logger.info(
        s"[lookup] REG01 business match found for acknowledgement ref: ${req.registerWithIDRequest.requestCommon.acknowledgementReference}"
      )
      auditCallResponse(url, resp)
      handleResponse(resp)
    } recover {
      case e: Throwable =>
        logger.info(
          s"[lookup] REG01 Match request failed for acknowledgement ref: ${req.registerWithIDRequest.requestCommon.acknowledgementReference}. Reason: $e"
        )
        throw e
    }

  }

  private def auditCallRequest(url: String, request: MatchingRequestHolder)(implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(
      transactionName = "customs-registration-with-id",
      path = url,
      detail = request.keyValueMap(),
      eventType = "customsRegistrationWithIdSubmitted"
    )

  private def auditCallResponse(url: String, response: MatchingResponse)(implicit hc: HeaderCarrier): Unit =
    audit.sendExtendedDataEvent(
      transactionName = "customs-registration-with-id",
      path = url,
      details = response.jsObject(),
      eventType = "customsRegistrationWithIdConfirmation"
    )

}
