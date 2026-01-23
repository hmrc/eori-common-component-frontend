/*
 * Copyright 2026 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import play.api.Logger
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.models.checkEori.CheckEoriResponse
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.net.URI
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[CheckEoriNumberConnectorImpl]) trait CheckEoriNumberConnector {

  def check(eori: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]]

}

class CheckEoriNumberConnectorImpl @Inject() (http: HttpClientV2, appConfig: AppConfig)
    extends CheckEoriNumberConnector {
  private val logger = Logger(this.getClass)

  def check(eori: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    http.get(new URI(s"${appConfig.checkEoriNumberUrl}/check-eori/$eori").toURL).execute[List[CheckEoriResponse]]
      .map(response => response.headOption.map(_.valid))
      .recoverWith {
        case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND =>
          Future.successful(Some(false))
        case NonFatal(e) =>
          // log all upstream errors at error level and keep going
          logger.error(s"Upstream error from check-eori-number service for EORI $eori.", e)
          Future.successful(Some(true))
      }

}
