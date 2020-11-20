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

import javax.inject.Inject
import play.api.Logger
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.registration._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class RegistrationDisplayConnector @Inject() (http: HttpClient, appConfig: AppConfig, audit: Auditable) {

  private val logger = Logger(this.getClass)

  protected val url = appConfig.getServiceUrl("registration-display")

  def registrationDisplay(
    request: RegistrationDisplayRequestHolder
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[EoriHttpResponse, RegistrationDisplayResponse]] = {
    auditCallRequest(url, request)

    // $COVERAGE-OFF$Loggers
    logger.debug(
      s"RegistrationDisplay: $url, requestCommon: ${request.registrationDisplayRequest.requestCommon} and hc: $hc"
    )
    // $COVERAGE-ON

    http.POST[RegistrationDisplayRequestHolder, RegistrationDisplayResponseHolder](url, request) map { resp =>
      // $COVERAGE-OFF$Loggers
      logger.debug(s"[RegistrationDisplay: response: $resp")
      // $COVERAGE-ON

      auditCallResponse(url, resp)
      Right(resp.registrationDisplayResponse)
    } recover {
      case NonFatal(e) =>
        logger.warn(s"registration-display failed. url: $url, error: $e")
        Left(ServiceUnavailableResponse)
    }
  }

  private def auditCallRequest(url: String, request: RegistrationDisplayRequestHolder)(implicit
    hc: HeaderCarrier
  ): Unit =
    audit.sendDataEvent(
      transactionName = "customs-registration-display",
      path = url,
      detail =
        request.registrationDisplayRequest.requestCommon
          .keyValueMap(),
      eventType = "RegistrationDisplaySubmitted"
    )

  private def auditCallResponse(url: String, response: RegistrationDisplayResponseHolder)(implicit
    hc: HeaderCarrier
  ): Unit =
    audit.sendDataEvent(
      transactionName = "customs-registration-display",
      path = url,
      detail = response.registrationDisplayResponse
        .keyValueMap(),
      eventType = "RegistrationDisplayResult"
    )

}
