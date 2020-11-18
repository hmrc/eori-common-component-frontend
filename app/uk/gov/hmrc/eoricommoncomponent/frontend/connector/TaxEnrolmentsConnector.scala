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
import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.enrolmentRequest.GovernmentGatewayEnrolmentRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.util.HttpStatusCheck
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxEnrolmentsConnector @Inject() (http: HttpClient, appConfig: AppConfig)(implicit ec: ExecutionContext)
    extends CaseClassAuditHelper {

  private val logger         = Logger(this.getClass)
  private val baseUrl        = appConfig.taxEnrolmentsBaseUrl
  val serviceContext: String = appConfig.taxEnrolmentsServiceContext

  def getEnrolments(safeId: String)(implicit hc: HeaderCarrier): Future[List[TaxEnrolmentsResponse]] = {
    val url = s"$baseUrl/$serviceContext/businesspartners/$safeId/subscriptions"

    // $COVERAGE-OFF$Loggers
    logger.debug(s"[GetEnrolments: $url and hc: $hc")
    // $COVERAGE-ON

    http.GET[List[TaxEnrolmentsResponse]](url) map { resp =>
      // $COVERAGE-OFF$Loggers
      logger.debug(s"[GetEnrolments: response: $resp")
      // $COVERAGE-ON
      resp
    } recover {
      case e: Throwable =>
        logger.warn(s"GetEnrolments failed. url: $url, error: $e", e)
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
    val url = s"$baseUrl/$serviceContext/subscriptions/$formBundleId/issuer"

    // $COVERAGE-OFF$Loggers
    logger.debug(s"[Enrol: $url, body: $request and hc: $hc")
    // $COVERAGE-ON

    http.doPut[TaxEnrolmentsRequest](url, request) map { response: HttpResponse =>
      logResponse("Enrol", response)
      response.status
    }
  }

  def enrolAndActivate(enrolmentKey: String, request: GovernmentGatewayEnrolmentRequest)(implicit
    hc: HeaderCarrier
  ): Future[HttpResponse] = {
    val url = s"$baseUrl/$serviceContext/service/$enrolmentKey/enrolment"

    // $COVERAGE-OFF$Loggers
    logger.debug(s"[EnrolAndActivate: $url, body: $request and hc: $hc")
    // $COVERAGE-ON

    http.PUT[GovernmentGatewayEnrolmentRequest, HttpResponse](url = url, body = request) map { response: HttpResponse =>
      logResponse("EnrolAndActivate", response)
      response
    }
  }

  private def logResponse(service: String, response: HttpResponse): Unit =
    if (HttpStatusCheck.is2xx(response.status))
      logger.debug(s"$service request is successful")
    else
      logger.warn(s"$service request is failed with response $response")

}
