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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription

import org.joda.time.format.ISODateTimeFormat
import play.api.Logger
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.httpparsers.VerifiedEmailRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.httpparsers.VerifiedEmailResponse.RequestCouldNotBeProcessed
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.{
  UpdateCustomsDataStoreConnector,
  UpdateVerifiedEmailConnector
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.email.{DateTimeUtil, RequestDetail, UpdateVerifiedEmailRequest}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.MessagingServiceParam
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.CustomsDataStoreRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RequestCommonGenerator
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

sealed trait UpdateError
case object RetriableError    extends UpdateError
case object NonRetriableError extends UpdateError

class UpdateVerifiedEmailService @Inject() (
  reqCommonGenerator: RequestCommonGenerator,
  updateVerifiedEmailConnector: UpdateVerifiedEmailConnector,
  customsDataStoreConnector: UpdateCustomsDataStoreConnector,
  audit: Auditable,
  appConfig: AppConfig
)(implicit ec: ExecutionContext) {

  private val url: String = appConfig.getServiceUrl("update-verified-email")
  private val logger      = Logger(this.getClass)

  def updateVerifiedEmail(currentEmail: Option[String] = None, newEmail: String, eori: String)(implicit
    hc: HeaderCarrier
  ): Future[Either[UpdateError, Unit]] = {

    val requestDetail = RequestDetail(
      IDType = "EORI",
      IDNumber = eori,
      emailAddress = newEmail,
      emailVerificationTimestamp = DateTimeUtil.dateTime
    )
    val request = VerifiedEmailRequest(UpdateVerifiedEmailRequest(reqCommonGenerator.generate(), requestDetail))
    val customsDataStoreRequest = CustomsDataStoreRequest(
      eori,
      newEmail,
      requestDetail.emailVerificationTimestamp.toString(ISODateTimeFormat.dateTimeNoMillis().withZoneUTC())
    )
    updateVerifiedEmailConnector.updateVerifiedEmail(request).flatMap {
      case Right(res)
          if res.getParameters.exists(
            params => params.headOption.map(_.paramName).contains(MessagingServiceParam.formBundleIdParamName)
          ) =>
        auditRequest(currentEmail, newEmail, eori, "changeEmailAddressConfirmed", res.getStatus)
        logger.info("[UpdateVerifiedEmailService][updateVerifiedEmail] - successfully updated verified email")
        customsDataStoreConnector.updateCustomsDataStore(customsDataStoreRequest).map(_ => Right((): Unit))

      case Right(res) if res.getStatus.exists(_.equalsIgnoreCase(RequestCouldNotBeProcessed)) =>
        val status = res.getStatus.getOrElse("Status text empty")
        logger.warn(
          "[UpdateVerifiedEmailService][updateVerifiedEmail]" +
            s" - updating verified email unsuccessful with business error/status code: ${status}"
        )
        auditRequest(currentEmail, newEmail, eori, "changeEmailAddressCouldNotBeProcessed", res.getStatus)
        Future.successful(Left(RetriableError))

      case Right(res) =>
        logger.warn(
          "[UpdateVerifiedEmailService][updateVerifiedEmail]" +
            s" - updating verified email unsuccessful with business error/status code: ${res.getStatus.getOrElse("Status text empty")}"
        )
        Future.successful(Left(NonRetriableError))

      case Left(res) =>
        logger.warn(
          s"[UpdateVerifiedEmailService][updateVerifiedEmail] - updating verified email unsuccessful with response: $res"
        )
        Future.successful(Left(NonRetriableError))
    }
  }

  private def auditRequest(
    currentEmail: Option[String],
    newEmail: String,
    eoriNumber: String,
    auditType: String,
    status: Option[String]
  )(implicit hc: HeaderCarrier): Unit =
    currentEmail.fold(
      audit.sendDataEvent(
        transactionName = "UpdateVerifiedEmailRequestSubmitted",
        path = url,
        detail = Map("newEmailAddress" -> newEmail, "eori" -> eoriNumber) ++ status.map("status" -> _),
        eventType = auditType
      )
    )(
      emailAddress =>
        audit.sendDataEvent(
          transactionName = "UpdateVerifiedEmailRequestSubmitted",
          path = url,
          detail = Map(
            "currentEmailAddress"  -> emailAddress,
            "newEmailAddress"      -> newEmail,
            "eori"                 -> eoriNumber
          ) ++ status.map("status" -> _),
          eventType = auditType
        )
    )

}
