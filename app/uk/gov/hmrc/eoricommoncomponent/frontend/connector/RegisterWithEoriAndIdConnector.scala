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
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{
  RegisterWithEoriAndIdRequest,
  RegisterWithEoriAndIdRequestHolder,
  RegisterWithEoriAndIdResponse,
  RegisterWithEoriAndIdResponseHolder
}
import uk.gov.hmrc.eoricommoncomponent.frontend.logging.CdsLogger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterWithEoriAndIdConnector @Inject() (http: HttpClient, appConfig: AppConfig, audit: Auditable)(implicit
  ec: ExecutionContext
) {

  private val url               = appConfig.getServiceUrl("register-with-eori-and-id")
  private val loggerComponentId = "RegisterWithEoriAndIdConnector"

  def register(
    request: RegisterWithEoriAndIdRequest
  )(implicit hc: HeaderCarrier): Future[RegisterWithEoriAndIdResponse] = {
    auditCallRequest(url, request)
    http.POST[RegisterWithEoriAndIdRequestHolder, RegisterWithEoriAndIdResponseHolder](
      url,
      RegisterWithEoriAndIdRequestHolder(request)
    ) map { resp =>
      CdsLogger.info(
        s"[$loggerComponentId][register] REG06 successful. postUrl $url, acknowledgement ref: ${request.requestCommon.acknowledgementReference}, response status: ${resp.registerWithEORIAndIDResponse.responseCommon.statusText}"
      )
      auditCallResponse(url, resp)
      resp.registerWithEORIAndIDResponse
    } recover {
      case e: Throwable =>
        CdsLogger.debug(
          s"[$loggerComponentId][register] REG06 failed. postUrl: $url, acknowledgement ref: ${request.requestCommon.acknowledgementReference}, error: $e"
        )
        throw e
    }
  }

  private def auditCallRequest(url: String, request: RegisterWithEoriAndIdRequest)(implicit hc: HeaderCarrier): Unit =
    audit.sendDataEvent(
      transactionName = "customs-registration",
      path = url,
      detail = Map("txName" -> "CustomsRegistrationSubmitted") ++ request.keyValueMap(),
      eventType = "CustomsRegistrationSubmitted"
    )

  private def auditCallResponse(url: String, response: RegisterWithEoriAndIdResponseHolder)(implicit
    hc: HeaderCarrier
  ): Unit =
    audit.sendDataEvent(
      transactionName = "customs-registration",
      path = url,
      detail = Map("txName" -> "CustomsRegistrationResult") ++ response.registerWithEORIAndIDResponse.keyValueMap(),
      eventType = "CustomsRegistrationResult"
    )

}
