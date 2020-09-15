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
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.logging.CdsLogger
import uk.gov.hmrc.eoricommoncomponent.frontend.models.enrolmentRequest.GovernmentGatewayEnrolmentRequest
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentsConnector @Inject() (http: HttpClient, appConfig: AppConfig, audit: Auditable)(implicit
  ec: ExecutionContext
) extends CaseClassAuditHelper {

  private val baseUrl        = appConfig.taxEnrolmentsBaseUrl
  val serviceContext: String = appConfig.taxEnrolmentsServiceContext

  private val loggerComponentId = "TaxEnrolmentsConnector"

  def getEnrolments(safeId: String)(implicit hc: HeaderCarrier): Future[List[TaxEnrolmentsResponse]] = {
    val url = s"$baseUrl/$serviceContext/businesspartners/$safeId/subscriptions"
    http.GET[List[TaxEnrolmentsResponse]](url) map { resp =>
      CdsLogger.info(s"[$loggerComponentId] tax-enrolments. url: $url")
      resp
    } recover {
      case e: Throwable =>
        CdsLogger.error(s"[$loggerComponentId][status] tax-enrolments failed. url: $url, error: $e", e)
        throw e
    }
  }

  /**
    *
    * @param request
    * @param formBundleId
    * @param hc
    * @param e
    * @return
    *  This is a issuer call which ETMP makes but we will do this for migrated users
    *  when subscription status((SUB02 Api CALL)) is 04 (SubscriptionExists)
    */
  def enrol(request: TaxEnrolmentsRequest, formBundleId: String)(implicit hc: HeaderCarrier): Future[Int] = {
    val loggerId = s"[$loggerComponentId]"
    val url      = s"$baseUrl/$serviceContext/subscriptions/$formBundleId/issuer"

    CdsLogger.info(s"$loggerId putUrl: $url")
    http.doPut[TaxEnrolmentsRequest](url, request) map { response: HttpResponse =>
      auditCall(url, request, response)
      response.status match {
        case s @ BAD_REQUEST =>
          CdsLogger.error(s"$loggerId tax enrolment request failed with BAD_REQUEST status")
          s
        case s =>
          CdsLogger.info(s"$loggerId tax enrolment complete. Status:$s")
          s
      }
    }
  }

  // Service name need to be enrolment Key - e.g. HMRC-CUS-ORG, HMRC-ATAR-ORG
  def enrolAndActivate(enrolmentKey: String, request: GovernmentGatewayEnrolmentRequest)(implicit
    hc: HeaderCarrier
  ): Future[HttpResponse] =
    http.PUT[GovernmentGatewayEnrolmentRequest, HttpResponse](
      url = s"$baseUrl/$serviceContext/service/$enrolmentKey/enrolment",
      body = request
    )

  private def auditCall(url: String, request: TaxEnrolmentsRequest, response: HttpResponse)(implicit
    hc: HeaderCarrier
  ): Unit =
    audit.sendExtendedDataEvent(
      transactionName = "Issuer-Call",
      path = url,
      details = Json.toJson(RequestResponse(Json.toJson(request), Json.toJson(response.status))),
      eventType = "IssuerCall"
    )

}
