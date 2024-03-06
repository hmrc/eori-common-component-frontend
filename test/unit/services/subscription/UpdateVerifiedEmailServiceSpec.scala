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

package unit.services.subscription

import base.UnitSpec
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.audit.Auditable
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.httpparsers.{
  UnhandledException,
  VerifiedEmailRequest,
  VerifiedEmailResponse
}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.{
  UpdateCustomsDataStoreConnector,
  UpdateVerifiedEmailConnector
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.email.DateTimeUtil.dateTime
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.email.UpdateVerifiedEmailResponse
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.CustomsDataStoreRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.{MessagingServiceParam, ResponseCommon}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RequestCommonGenerator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  Error,
  UpdateEmailError,
  UpdateVerifiedEmailService
}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UpdateVerifiedEmailServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfter {

  private val mockRequestCommonGenerator            = mock[RequestCommonGenerator]
  private val mockUpdateVerifiedEmailConnector      = mock[UpdateVerifiedEmailConnector]
  private val mockUpdateCustomsDataStoreConnector   = mock[UpdateCustomsDataStoreConnector]
  private val mockAudit                             = mock[Auditable]
  private val config                                = mock[AppConfig]
  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  private val service = new UpdateVerifiedEmailService(
    mockRequestCommonGenerator,
    mockUpdateVerifiedEmailConnector,
    mockUpdateCustomsDataStoreConnector,
    mockAudit,
    config
  )

  before {
    reset(mockRequestCommonGenerator)
    reset(mockUpdateVerifiedEmailConnector)
    reset(mockUpdateCustomsDataStoreConnector)
    reset(mockAudit)
    reset(config)
  }

  private val verifiedEmailResponse = VerifiedEmailResponse(
    UpdateVerifiedEmailResponse(
      ResponseCommon(
        "OK",
        None,
        LocalDateTime.from(dateTime),
        Some(List(MessagingServiceParam("ETMPFORMBUNDLENUMBER", "077063075008")))
      )
    )
  )

  private val verifiedEmailResponse003 = VerifiedEmailResponse(
    UpdateVerifiedEmailResponse(
      ResponseCommon("OK", Some("003 - Request could not be processed"), LocalDateTime.from(dateTime), Some(List()))
    )
  )

  private val verifiedEmailResponseOtherError = VerifiedEmailResponse(
    UpdateVerifiedEmailResponse(
      ResponseCommon("OK", Some("Something went wrong"), LocalDateTime.from(dateTime), Some(List()))
    )
  )

  private val verifiedEmailResponseError = VerifiedEmailResponse(
    UpdateVerifiedEmailResponse(ResponseCommon("KO", Some("Bad Request"), LocalDateTime.from(dateTime), Some(List())))
  )

  "UpdateVerifiedEmailService" should {

    "Update verified Email successfully" in {
      when(
        mockUpdateVerifiedEmailConnector
          .updateVerifiedEmail(any[VerifiedEmailRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Right(verifiedEmailResponse)))

      when(
        mockUpdateCustomsDataStoreConnector
          .updateCustomsDataStore(any[CustomsDataStoreRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))
      doNothing().when(mockAudit).sendDataEvent(any(), any(), any(), any(), any())(any[HeaderCarrier])

      await(service.updateVerifiedEmail(None, "newemail@email.email", "GB0123456789")) shouldBe Right((): Unit)
      verify(mockAudit, times(1)).sendDataEvent(any(), any(), any(), any(), any())(any[HeaderCarrier])
    }

    "Update verified Email successfully with a currentEmail" in {
      when(
        mockUpdateVerifiedEmailConnector
          .updateVerifiedEmail(any[VerifiedEmailRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Right(verifiedEmailResponse)))

      when(
        mockUpdateCustomsDataStoreConnector
          .updateCustomsDataStore(any[CustomsDataStoreRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))
      doNothing().when(mockAudit).sendDataEvent(any(), any(), any(), any(), any())(any[HeaderCarrier])

      await(
        service.updateVerifiedEmail(Some("oldemail@email.com"), "newemail@email.email", "GB0123456789")
      ) shouldBe Right((): Unit)
      verify(mockAudit, times(1)).sendDataEvent(any(), any(), any(), any(), any())(any[HeaderCarrier])
    }

    "fail with Retriable Failure when Email Update returns 003 status" in {
      when(
        mockUpdateVerifiedEmailConnector
          .updateVerifiedEmail(any[VerifiedEmailRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Right(verifiedEmailResponse003)))

      when(
        mockUpdateCustomsDataStoreConnector
          .updateCustomsDataStore(any[CustomsDataStoreRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))
      doNothing().when(mockAudit).sendDataEvent(any(), any(), any(), any(), any())(any[HeaderCarrier])

      await(service.updateVerifiedEmail(None, "newemail@email.email", "GB0123456789")) shouldBe Left(
        UpdateEmailError("003 - Request could not be processed")
      )
      verify(mockAudit, times(1)).sendDataEvent(any(), any(), any(), any(), any())(any[HeaderCarrier])
    }

    "fail with Non Retriable Failure when Email Update fails with a different status reason" in {
      when(
        mockUpdateVerifiedEmailConnector
          .updateVerifiedEmail(any[VerifiedEmailRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Right(verifiedEmailResponseOtherError)))

      when(
        mockUpdateCustomsDataStoreConnector
          .updateCustomsDataStore(any[CustomsDataStoreRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      await(service.updateVerifiedEmail(None, "newemail@email.email", "GB0123456789")) shouldBe Left(
        Error("Something went wrong")
      )
    }

    "fail with Non Retriable Failure when Email Update fails" in {
      when(
        mockUpdateVerifiedEmailConnector
          .updateVerifiedEmail(any[VerifiedEmailRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Right(verifiedEmailResponseError)))

      when(
        mockUpdateCustomsDataStoreConnector
          .updateCustomsDataStore(any[CustomsDataStoreRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      await(service.updateVerifiedEmail(None, "newemail@email.email", "GB0123456789")) shouldBe Left(
        Error("Bad Request")
      )
    }

    "fail when Email Update fails with Left(response)" in {
      when(
        mockUpdateVerifiedEmailConnector
          .updateVerifiedEmail(any[VerifiedEmailRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(Left(UnhandledException)))

      when(
        mockUpdateCustomsDataStoreConnector
          .updateCustomsDataStore(any[CustomsDataStoreRequest])(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      await(service.updateVerifiedEmail(None, "newemail@email.email", "GB0123456789")) shouldBe Left(
        Error("Unknown error status")
      )
    }
  }
}
