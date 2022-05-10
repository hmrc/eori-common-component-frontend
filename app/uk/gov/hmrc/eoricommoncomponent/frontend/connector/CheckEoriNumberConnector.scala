/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.eoricommoncomponent.frontend.models.checkEori.{CheckEoriRequest, CheckEoriResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, UpstreamErrorResponse}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CheckEoriNumberConnectorImpl]) trait CheckEoriNumberConnector {
  def check(
    checkEoriRequest: CheckEoriRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[List[CheckEoriResponse]]]

}

class CheckEoriNumberConnectorImpl @Inject() (http: HttpClient, appConfig: AppConfig) extends CheckEoriNumberConnector {
  private val logger = Logger(this.getClass)

  def check(
    checkEoriRequest: CheckEoriRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[List[CheckEoriResponse]]] =
    http.GET[List[CheckEoriResponse]](url = s"${appConfig.eisUrl}/check-eori/${checkEoriRequest.eoriNumber}")
      .map(Some(_)).recoverWith {
        case e: UpstreamErrorResponse if e.statusCode == NOT_FOUND =>
          Future.successful(Some(List(CheckEoriResponse(checkEoriRequest.eoriNumber, valid = false, None))))
        case e: UpstreamErrorResponse =>  //log all upstream errors at error level and keep going
          logger.error("Upstream error from check-eori-number service,the user journey will continue anyway.", e)
          Future.successful(Some(List(CheckEoriResponse(checkEoriRequest.eoriNumber, valid = false, None))))
      }

}
