/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json.Json
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.EmailVerificationKeys._
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.httpparsers.EmailVerificationRequestHttpParser.EmailVerificationRequestResponse
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.httpparsers.EmailVerificationStateHttpParser.EmailVerificationStateResponse
import uk.gov.hmrc.eoricommoncomponent.frontend.models.email.{
  ResponseWithURI,
  VerificationStatusResponse,
  StartVerificationJourneyEmail,
  StartVerificationJourneyRequest
}
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import java.net.URL
import play.api.Logging
import cats.data.EitherT
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{SubscribeJourney, Service}
import play.mvc.Http.Status.{CREATED, OK}
import play.api.i18n.Messages
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailVerificationConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig)(implicit ec: ExecutionContext) extends HandleResponses {

  private[connector] lazy val checkVerifiedEmailUrl: String =
    s"${appConfig.emailVerificationBaseUrl}/${appConfig.emailVerificationServiceContext}/verified-email-check"

  private[connector] lazy val createEmailVerificationRequestUrl: String =
    s"${appConfig.emailVerificationBaseUrl}/${appConfig.emailVerificationServiceContext}/verification-requests"

  def getEmailVerificationState(
    emailAddress: String
  )(implicit hc: HeaderCarrier): Future[EmailVerificationStateResponse] = Future.failed(new Exception("Woops"))

  def createEmailVerificationRequest(emailAddress: String, continueUrl: String)(implicit
    hc: HeaderCarrier
  ): Future[EmailVerificationRequestResponse] =  Future.failed(new Exception("Woops"))

  def startVerificationJourney(credId: String, service: Service, email: String, subscribeJourney: SubscribeJourney)(implicit
    hc: HeaderCarrier, messages: Messages
  ): EitherT[Future, ResponseError, ResponseWithURI] = EitherT {

    lazy val verifyEmailUrl: URL =
      url"${appConfig.emailVerificationBaseUrl}/${appConfig.emailVerificationServiceContext}/verify-email"

    val request = StartVerificationJourneyRequest(
      credId = credId,
      continueUrl = controllers.routes.EmailController.form(service, subscribeJourney).url,
      origin = "ecc",
      deskproServiceName = "eori-common-component",
      accessibilityStatementUrl = service.accessibilityUrl,
      pageTitle = messages(s"cds.banner.subscription.${service.code}"),
      lang = messages.lang.code,
      email = Some(
        StartVerificationJourneyEmail(
          address = email,
          enterUrl = controllers.email.routes.WhatIsYourEmailController.createForm(service, subscribeJourney).url
        )
      )
    )

    httpClient.post(verifyEmailUrl)
      .withBody(Json.toJson(request))
      .execute
      .map { response =>
        response.status match {
          case CREATED => handleResponse[ResponseWithURI](response)
          case _ => 
            val error = s"Unexpected response from verify-email: ${response.body}"
            logger.error(error)
            Left(ResponseError(response.status, error))
        }
      }

  }

  def getVerificationStatus(credId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, ResponseError, VerificationStatusResponse] = EitherT {

    lazy val url: URL =
      url"${appConfig.emailVerificationBaseUrl}/${appConfig.emailVerificationServiceContext}/verification-status/$credId"

    httpClient.get(url)
      .execute
      .map { response =>
        response.status match {
          case OK => handleResponse[VerificationStatusResponse](response)
          case _ => 
            val error = s"Unexpected response from verification-status: ${response.body}"
            logger.error(error)
            Left(ResponseError(response.status, error))
        }
      }

  }

  def passcodes(implicit hc: HeaderCarrier) = {
    httpClient.get(url"http://localhost:9891/test-only/passcodes")
  }

}

object EmailVerificationKeys {
  val EmailKey              = "email"
  val TemplateIdKey         = "templateId"
  val TemplateParametersKey = "templateParameters"
  val LinkExpiryDurationKey = "linkExpiryDuration"
  val ContinueUrlKey        = "continueUrl"
}
