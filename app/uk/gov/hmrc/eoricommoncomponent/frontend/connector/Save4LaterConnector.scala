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
import play.api.libs.json._
import play.mvc.Http.Status._
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.http.{BadRequestException, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class Save4LaterConnector @Inject() (http: HttpClient, appConfig: AppConfig, audit: Auditable)(implicit
  ec: ExecutionContext
) {

  private val logger = Logger(this.getClass)

  private def logSuccess(method: String, url: String)(implicit hc: HeaderCarrier) =
    logger.debug(s"$method complete for call to $url and headers ${hc.headers}")

  private def logError(method: String, url: String, e: Throwable)(implicit hc: HeaderCarrier) =
    logger.error(s"$method request failed for call to $url and headers ${hc.headers}: ${e.getMessage}", e)

  def get[T](id: String, key: String)(implicit hc: HeaderCarrier, reads: Reads[T]): Future[Option[T]] = {
    val url = s"${appConfig.handleSubscriptionBaseUrl}/save4later/$id/$key"

    // $COVERAGE-OFF$Loggers
    logger.debug(s"[Get: $url and hc: $hc")
    // $COVERAGE-ON

    http.GET[HttpResponse](url) map { response =>
      logSuccess("Get", url)

      response.status match {
        case OK =>
          Some(response.json.as[T])
        case NOT_FOUND =>
          None
        case _ => throw new BadRequestException(s"Status:${response.status}")
      }
    } recoverWith {
      case NonFatal(e) =>
        logError("Get", url, e)
        Future.failed(e)
    }
  }

  def put[T](id: String, key: String, payload: JsValue)(implicit hc: HeaderCarrier): Future[Unit] = {
    val url = s"${appConfig.handleSubscriptionBaseUrl}/save4later/$id/$key"

    // $COVERAGE-OFF$Loggers
    logger.debug(s"[Put: $url, body: $payload and hc: $hc")
    // $COVERAGE-ON

    http.PUT[JsValue, HttpResponse](url, payload) map { response =>
      logSuccess("Put", url)
      response.status match {
        case NO_CONTENT | CREATED | OK => ()
        case _                         => throw new BadRequestException(s"Status:${response.status}")
      }
    } recoverWith {
      case NonFatal(e) =>
        logError("Put", url, e)
        Future.failed(e)
    }
  }

  def delete[T](id: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    val url = s"${appConfig.handleSubscriptionBaseUrl}/save4later/$id"

    // $COVERAGE-OFF$Loggers
    logger.debug(s"[Delete: $url and hc: $hc")
    // $COVERAGE-ON

    http.DELETE[HttpResponse](url) map { response =>
      logSuccess("Delete", url)
      response.status match {
        case NO_CONTENT => ()
        case _          => throw new BadRequestException(s"Status:${response.status}")
      }
    } recoverWith {
      case NonFatal(e) =>
        logError("Delete", url, e)
        Future.failed(e)
    }
  }

}
