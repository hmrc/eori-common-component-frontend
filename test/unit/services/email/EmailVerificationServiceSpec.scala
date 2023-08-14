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

package unit.services.email

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import base.UnitSpec
import org.mockito.Mockito._
import org.mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import play.api.http.Status.BAD_REQUEST
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.EmailVerificationConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.httpparsers.EmailVerificationRequestHttpParser.{
  EmailAlreadyVerified,
  EmailVerificationRequestFailure,
  EmailVerificationRequestResponse,
  EmailVerificationRequestSent
}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.httpparsers.EmailVerificationStateHttpParser.{
  EmailNotVerified,
  EmailVerificationStateErrorResponse,
  EmailVerificationStateResponse,
  EmailVerified
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailVerificationService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.ResponseError
import cats.data.EitherT
import uk.gov.hmrc.eoricommoncomponent.frontend.models.email.{EmailStatus, ResponseWithURI, VerificationStatusResponse, EmailVerificationStatus}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.email.EmailStatus._

import scala.concurrent.Future

class EmailVerificationServiceSpec
    extends AsyncWordSpec with Matchers with ScalaFutures with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach {
  private val mockConnector = mock[EmailVerificationConnector]

  implicit val hc: HeaderCarrier       = mock[HeaderCarrier]
  implicit val rq: Request[AnyContent] = mock[Request[AnyContent]]

  val service = new EmailVerificationService(mockConnector)

  private val email          = "test@example.com"
  private val differentEmail = "different@example.com"
  private val continueUrl    = "/customs-enrolment-services/test-continue-url"

  override protected def beforeEach(): Unit =
    reset(mockConnector)

  def mockGetVerificationStatus(credId: String)(response: EitherT[Future, ResponseError, VerificationStatusResponse]): Unit =
    when(
      mockConnector.getVerificationStatus(ArgumentMatchers.eq(credId))(ArgumentMatchers.any[HeaderCarrier])
    ) thenReturn response

  val credId = "123"
  
  "getVerificationStatus" should {

    "return Error when the connector returns an Error" in {

      val expected: Either[ResponseError, VerificationStatusResponse] = Left(ResponseError(500, "Something went wrong"))
      mockGetVerificationStatus(credId)(EitherT[Future, ResponseError, VerificationStatusResponse]{Future.successful(expected)})

      service.getVerificationStatus(email, credId).value.map{ res =>
        res shouldEqual expected
      }
    }

    "return Locked where the input email has locked=true" in {

      val expected = Right(EmailStatus.Locked)
      val response: Either[ResponseError, VerificationStatusResponse] = Right(VerificationStatusResponse(Seq(EmailVerificationStatus(emailAddress = email, verified = false, locked = true))))
      mockGetVerificationStatus(credId)(EitherT[Future, ResponseError, VerificationStatusResponse]{Future.successful(response)})

      service.getVerificationStatus(email, credId).value.map{ res =>
        res shouldEqual expected
      }
    }

    "return Verified where the input email has verified=true" in {

      val expected = Right(EmailStatus.Verified)
      val response: Either[ResponseError, VerificationStatusResponse] = Right(VerificationStatusResponse(Seq(EmailVerificationStatus(emailAddress = email, verified = true, locked = false))))
      mockGetVerificationStatus(credId)(EitherT[Future, ResponseError, VerificationStatusResponse]{Future.successful(response)})

      service.getVerificationStatus(email, credId).value.map{ res =>
        res shouldEqual expected
      }
    }

    "return Unverified where it doesn't exist but a different email has verified=true" in {

      val expected = Right(EmailStatus.Unverified)
      val response: Either[ResponseError, VerificationStatusResponse] = Right(VerificationStatusResponse(Seq(EmailVerificationStatus(emailAddress = differentEmail, verified = true, locked = false))))
      mockGetVerificationStatus(credId)(EitherT[Future, ResponseError, VerificationStatusResponse]{Future.successful(response)})

      service.getVerificationStatus(email, credId).value.map{ res =>
        res shouldEqual expected
      }
    }

    "return Unverified where it doesn't exist but a different email has locked=true" in {

      val expected = Right(EmailStatus.Unverified)
      val response: Either[ResponseError, VerificationStatusResponse] = Right(VerificationStatusResponse(Seq(EmailVerificationStatus(emailAddress = differentEmail, verified = false, locked = true))))
      mockGetVerificationStatus(credId)(EitherT[Future, ResponseError, VerificationStatusResponse]{Future.successful(response)})

      service.getVerificationStatus(email, credId).value.map{ res =>
        res shouldEqual expected
      }
    }

    "return Unverified where an empty list is returned" in {

      val expected = Right(EmailStatus.Unverified)
      val response: Either[ResponseError, VerificationStatusResponse] = Right(VerificationStatusResponse(Nil))
      mockGetVerificationStatus(credId)(EitherT[Future, ResponseError, VerificationStatusResponse]{Future.successful(response)})

      service.getVerificationStatus(email, credId).value.map{ res =>
        res shouldEqual expected
      }
    }

    "return Locked where the input email has locked=true and a different email exists" in {

      val expected = Right(EmailStatus.Locked)
      val sequence = Seq(EmailVerificationStatus(emailAddress = email, verified = false, locked = true), EmailVerificationStatus(emailAddress = differentEmail, verified = true, locked = false))
      val response: Either[ResponseError, VerificationStatusResponse] = Right(VerificationStatusResponse(sequence))
      mockGetVerificationStatus(credId)(EitherT[Future, ResponseError, VerificationStatusResponse]{Future.successful(response)})

      service.getVerificationStatus(email, credId).value.map{ res =>
        res shouldEqual expected
      }
    }

    "return Verified where the input email has verified=true and a different email exists" in {

      val expected = Right(EmailStatus.Verified)
      val sequence = Seq(EmailVerificationStatus(emailAddress = email, verified = true, locked = false), EmailVerificationStatus(emailAddress = differentEmail, verified = false, locked = true))
      val response: Either[ResponseError, VerificationStatusResponse] = Right(VerificationStatusResponse(sequence))      
      mockGetVerificationStatus(credId)(EitherT[Future, ResponseError, VerificationStatusResponse]{Future.successful(response)})

      service.getVerificationStatus(email, credId).value.map{ res =>
        res shouldEqual expected
      }
    }

  }

  
}
