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
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.mvc.Http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EnrolmentResponse, EnrolmentStoreProxyResponse}
import uk.gov.hmrc.eoricommoncomponent.frontend.logging.CdsLogger
import uk.gov.hmrc.eoricommoncomponent.frontend.models.enrolmentRequest.{KnownFacts, KnownFactsQuery}
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

class EnrolmentStoreProxyConnector @Inject()(http: HttpClient, appConfig: AppConfig, audit: Auditable)(implicit ec: ExecutionContext) {

  private val baseUrl = appConfig.enrolmentStoreProxyBaseUrl
  private val serviceContext = appConfig.enrolmentStoreProxyServiceContext

  private val loggerComponentId = "EnrolmentStoreProxyConnector"

  def getEnrolmentByGroupId(
    groupId: String
  )(implicit hc: HeaderCarrier, reads: Reads[EnrolmentStoreProxyResponse]): Future[EnrolmentStoreProxyResponse] = {
    val url =
      s"$baseUrl/$serviceContext/enrolment-store/groups/$groupId/enrolments?type=principal&service=HMRC-CUS-ORG"
    http.GET[HttpResponse](url) map { resp =>
      auditCall(url, resp)
      CdsLogger.info(s"[$loggerComponentId] enrolment-store-proxy. url: $url")
      resp.status match {
        case OK => resp.json.as[EnrolmentStoreProxyResponse]
        case NO_CONTENT =>
          EnrolmentStoreProxyResponse(enrolments = List.empty[EnrolmentResponse])
        case _ =>
          throw new BadRequestException(s"Enrolment Store Proxy Status : ${resp.status}")
      }
    } recover {
      case e: Throwable =>
        CdsLogger.error(s"[$loggerComponentId][status] enrolment-store-proxy failed. url: $url, error: $e", e)
        throw e
    }
  }

  private def auditCall(url: String, response: HttpResponse)(implicit hc: HeaderCarrier): Unit = {
    import AuditHelp._
    audit.sendExtendedDataEvent(
      transactionName = "Enrolment-Store-Proxy-Call",
      path = url,
      details = response,
      eventType = "EnrolmentStoreProxyCall"
    )
  }

  def queryKnownFactsByIdentifiers(knownFactsQuery: KnownFactsQuery)(implicit hc: HeaderCarrier): Future[Option[KnownFacts]] =
    http.POST[KnownFactsQuery, Option[KnownFacts]](
      s"$baseUrl/$serviceContext/enrolment-store/enrolments",
      knownFactsQuery
    )

  object AuditHelp {
    implicit def httpResponseToJsvalue(httpResponse: HttpResponse): JsValue =
      new Writes[HttpResponse] {
        override def writes(o: HttpResponse): JsValue =
          if (o.body.nonEmpty)
            Json.obj("status" -> o.status, "body" -> o.json)
          else Json.obj("status" -> o.status)
      }.writes(httpResponse)

  }

}
