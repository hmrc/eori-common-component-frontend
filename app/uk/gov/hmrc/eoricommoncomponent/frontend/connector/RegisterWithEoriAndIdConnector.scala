/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{
  RegisterWithEoriAndIdRequest,
  RegisterWithEoriAndIdRequestHolder,
  RegisterWithEoriAndIdResponse,
  RegisterWithEoriAndIdResponseHolder
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.models.events.{Registration, RegistrationResult, RegistrationSubmitted}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterWithEoriAndIdConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig, audit: Auditable)(
  implicit ec: ExecutionContext
) {

  private val logger = Logger(this.getClass)
  private val url    = url"${appConfig.getServiceUrl("register-with-eori-and-id")}"

  def register(
    request: RegisterWithEoriAndIdRequest
  )(implicit hc: HeaderCarrier, originatingService: Service): Future[RegisterWithEoriAndIdResponse] = {

    // $COVERAGE-OFF$
    logger.debug(s"REG06 Register: $url, requestCommon: ${request.requestCommon} and hc: $hc")
    // $COVERAGE-ON$

    val httpRequest = httpClient
      .post(url)
      .withBody(Json.toJson(RegisterWithEoriAndIdRequestHolder(request)))
      .setHeader(AUTHORIZATION -> appConfig.internalAuthToken)

    httpRequest.execute[RegisterWithEoriAndIdResponseHolder] map { resp =>
      // $COVERAGE-OFF$
      logger.debug(s"REG06 Register: responseCommon: ${resp.registerWithEORIAndIDResponse.responseCommon}")
      // $COVERAGE-ON$

      auditCall(url.toString, request, resp)
      resp.registerWithEORIAndIDResponse.withAdditionalInfo(request.requestDetail.registerModeID)
    } recover {
      case e: Throwable =>
        logger.warn(
          s"REG06 Register failed. postUrl: $url, acknowledgement ref: ${request.requestCommon.acknowledgementReference}, error: $e"
        )
        throw e
    }
  }

  private def auditCall(
    url: String,
    request: RegisterWithEoriAndIdRequest,
    response: RegisterWithEoriAndIdResponseHolder
  )(implicit hc: HeaderCarrier, originatingService: Service): Unit = {
    val registrationSubmitted = RegistrationSubmitted(request, originatingService.code)
    val registrationResult    = RegistrationResult(response)

    audit.sendExtendedDataEvent(
      transactionName = "ecc-registration",
      path = url,
      details = Json.toJson(Registration(registrationSubmitted, registrationResult)),
      eventType = "Registration"
    )
  }

}
