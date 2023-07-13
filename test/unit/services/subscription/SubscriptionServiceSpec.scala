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
import org.mockito.Mockito.when
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.Checkers
import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.SubscriptionServiceConnector

import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.{
  SubscriptionRequest,
  SubscriptionResponse
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionServiceSpec
    extends UnitSpec with MockitoSugar with BeforeAndAfterAll with Checkers with SubscriptionServiceTestData {
  private val mockHeaderCarrier = mock[HeaderCarrier]

  override def beforeAll(): Unit =
    super.beforeAll()

  private def subscriptionSuccessResultIgnoreTimestamp(
    expected: SubscriptionSuccessful,
    request: SubscriptionRequest
  ): SubscriptionResult = {
    val timestamp =
      request.subscriptionCreateRequest.requestDetail.contactInformation.flatMap(_.emailVerificationTimestamp)
    expected.copy(emailVerificationTimestamp = timestamp)
  }

  private def subscriptionPendingResultIgnoreTimestamp(
    expected: SubscriptionPending,
    request: SubscriptionRequest
  ): SubscriptionResult = {
    val timestamp =
      request.subscriptionCreateRequest.requestDetail.contactInformation.flatMap(_.emailVerificationTimestamp)
    expected.copy(emailVerificationTimestamp = timestamp)
  }

  "Calling Subscribe" should {

    "call connector with correct values when only matching individual details with EORI are given to expect a successful subscription" in {
      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsIndividual(
          Some(eori),
          TaxPayerId(sapNumber),
          SafeId("safe-id"),
          individualName,
          address,
          dateOfBirth
        ),
        subscriptionGenerateResponse
      )
      assertSameJson(Json.toJson(result.actualConnectorRequest), individualAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionSuccessResultIgnoreTimestamp(
        subscriptionSuccessResult,
        result.actualConnectorRequest
      )
    }

    "call connector with captured email for Subscription journey" in {
      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsIndividual(
          Some(eori),
          TaxPayerId(sapNumber),
          SafeId("safe-id"),
          individualName,
          address,
          dateOfBirth
        ),
        subscriptionGenerateResponse
      )
      assertSameJson(Json.toJson(result.actualConnectorRequest), individualAutomaticSubscriptionRequestJson)
    }

    "throw IllegalStateException if connector returns invalid response" in {
      intercept[IllegalStateException] {
        makeSubscribeWhenAutoAllowed(
          RegistrationDetailsIndividual(
            Some(eori),
            TaxPayerId(sapNumber),
            SafeId("safe-id"),
            individualName,
            address,
            dateOfBirth
          ),
          subscriptionGenerateInvalidResponse
        )
      }
    }

    "call connector with correct values when only organisation matching details with EORI and date of establishment are given" in {
      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsOrganisation(
          Some(eori),
          TaxPayerId(sapNumber),
          safeId = SafeId("safe-id"),
          businessName,
          address,
          Some(dateOfEstablishment),
          Some(CorporateBody)
        ),
        subscriptionGenerateResponse
      )

      assertSameJson(Json.toJson(result.actualConnectorRequest), organisationAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionSuccessResultIgnoreTimestamp(
        subscriptionSuccessResult,
        result.actualConnectorRequest
      )
    }

    "send a request using a partial REG06 response and captured email address" in {
      val result =
        makeExistingRegistrationRequest(stubRegisterWithPartialResponse(), subscriptionGenerateResponse, contactEmail)

      assertSameJson(
        Json.toJson(result.actualConnectorRequest),
        organisationAutomaticExistingRegistrationRequestJson(contactEmail)
      )

      result.actualServiceCallResult shouldBe subscriptionSuccessResultIgnoreTimestamp(
        subscriptionSuccessResult,
        result.actualConnectorRequest
      )
    }
    "send a request without and captured email address" in {
      intercept[IllegalStateException] {
        makeExistingRegistrationRequest(stubRegisterWithoutResponseData(), subscriptionGenerateResponse, contactEmail)
      }.getMessage shouldBe "REGO6 ResponseData is non existent. This is required to populate subscription request"
    }

    "send a request using a complete REG06 response and captured email address" in {
      val result =
        makeExistingRegistrationRequest(stubRegisterWithCompleteResponse(), subscriptionGenerateResponse, contactEmail)

      assertSameJson(
        Json.toJson(result.actualConnectorRequest),
        existingRegistrationSubcriptionRequestJson(contactEmail)
      )

      result.actualServiceCallResult shouldBe subscriptionSuccessResultIgnoreTimestamp(
        subscriptionSuccessResult,
        result.actualConnectorRequest
      )
    }

    "throw an exception when doe/ dob is missing when subscribing" in {
      val service = constructService(_ => None)
      the[IllegalArgumentException] thrownBy {
        service.existingReg(
          stubRegisterWithPartialResponseWithNoDoe(),
          fullyPopulatedSubscriptionDetails,
          "",
          atarService
        )(mockHeaderCarrier)
      } should have message "requirement failed"
    }
  }

  "Calling Subscribe with service name feature disabled" should {

    "call connector with without service name" in {

      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsOrganisation(
          Some(eori),
          TaxPayerId(sapNumber),
          safeId = SafeId("safe-id"),
          businessName,
          address,
          Some(dateOfEstablishment),
          Some(CorporateBody)
        ),
        subscriptionGenerateResponse
      )

      assertSameJson(Json.toJson(result.actualConnectorRequest), organisationAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionSuccessResultIgnoreTimestamp(
        subscriptionSuccessResult,
        result.actualConnectorRequest
      )
    }
  }

  "Calling Subscribe with service name returns Subscription failed if enrolment already exists" should {

    "call connector with without service name" in {

      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsOrganisation(
          Some(eori),
          TaxPayerId(sapNumber),
          safeId = SafeId("safe-id"),
          businessName,
          address,
          Some(dateOfEstablishment),
          Some(CorporateBody)
        ),
        subscriptionFailedResponseForEnrolmentAlreadyExists
      )

      assertSameJson(Json.toJson(result.actualConnectorRequest), organisationAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionFailureResult
    }
  }

  "Calling Subscribe with service name returns Subscription failed if request not processed" should {

    "call connector with without service name" in {

      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsOrganisation(
          Some(eori),
          TaxPayerId(sapNumber),
          safeId = SafeId("safe-id"),
          businessName,
          address,
          Some(dateOfEstablishment),
          Some(CorporateBody)
        ),
        subscriptionFailedResponseForRequestNotProcessed
      )

      assertSameJson(Json.toJson(result.actualConnectorRequest), organisationAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionFailureResultRequestNotProcessed
    }
  }

  "Calling Subscribe with service name returns Subscription failed if eori already associated" should {

    "call connector with without service name" in {

      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsOrganisation(
          Some(eori),
          TaxPayerId(sapNumber),
          safeId = SafeId("safe-id"),
          businessName,
          address,
          Some(dateOfEstablishment),
          Some(CorporateBody)
        ),
        subscriptionFailedResponseForEoriAlreadyAssociated
      )

      assertSameJson(Json.toJson(result.actualConnectorRequest), organisationAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionFailureResultEoriAlreadyAssociated
    }
  }

  "Calling Subscribe with service name returns Subscription failed if subscription in progress" should {

    "call connector with without service name" in {

      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsOrganisation(
          Some(eori),
          TaxPayerId(sapNumber),
          safeId = SafeId("safe-id"),
          businessName,
          address,
          Some(dateOfEstablishment),
          Some(CorporateBody)
        ),
        subscriptionFailedResponseForSubscriptionInProgress
      )

      assertSameJson(Json.toJson(result.actualConnectorRequest), organisationAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionFailureResultSubscriptionInProgress
    }
  }

  "Calling Subscribe with service name returns Subscription failed if the enrolment failed" should {

    "call connector with without service name" in {

      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsOrganisation(
          Some(eori),
          TaxPayerId(sapNumber),
          safeId = SafeId("safe-id"),
          businessName,
          address,
          Some(dateOfEstablishment),
          Some(CorporateBody)
        ),
        subscriptionFailedResponse
      )

      assertSameJson(Json.toJson(result.actualConnectorRequest), organisationAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionFailureResultSubscriptionFailed
    }
  }

  "Calling Subscribe with service name returns Subscription Pending response if the subscription is pending" should {

    "call connector with without service name" in {

      val result = makeSubscribeWhenAutoAllowed(
        RegistrationDetailsOrganisation(
          Some(eori),
          TaxPayerId(sapNumber),
          safeId = SafeId("safe-id"),
          businessName,
          address,
          Some(dateOfEstablishment),
          Some(CorporateBody)
        ),
        subscriptionPendingResponse
      )

      assertSameJson(Json.toJson(result.actualConnectorRequest), organisationAutomaticSubscriptionRequestJson)
      result.actualServiceCallResult shouldEqual subscriptionPendingResultIgnoreTimestamp(
        subscriptionPendingResult,
        result.actualConnectorRequest
      )
    }
  }

  private case class SubscriptionCallResult(
    actualServiceCallResult: SubscriptionResult,
    actualConnectorRequest: SubscriptionRequest
  )

  private def makeSubscribeWhenAutoAllowed(
    registrationDetails: RegistrationDetails,
    subscriptionResponse: SubscriptionResponse
  ): SubscriptionCallResult = {
    val subscribeDataCaptor = ArgumentCaptor.forClass(classOf[SubscriptionRequest])
    val service = constructService(
      connectorMock =>
        when(connectorMock.subscribe(subscribeDataCaptor.capture())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(subscriptionResponse))
    )

    val actualServiceCallResult = await(
      service
        .subscribeWithMandatoryOnly(registrationDetails, fullyPopulatedSubscriptionDetails, atarService, None)(
          mockHeaderCarrier
        )
    )
    val actualConnectorRequest = subscribeDataCaptor.getValue
    SubscriptionCallResult(actualServiceCallResult, actualConnectorRequest)
  }

  private def makeExistingRegistrationRequest(
    registerWithEoriAndIdResponse: RegisterWithEoriAndIdResponse,
    subscriptionResponse: SubscriptionResponse,
    email: String
  ): SubscriptionCallResult = {
    val subscribeDataCaptor = ArgumentCaptor.forClass(classOf[SubscriptionRequest])
    val service = constructService(
      connectorMock =>
        when(connectorMock.subscribe(subscribeDataCaptor.capture())(ArgumentMatchers.any()))
          .thenReturn(Future.successful(subscriptionResponse))
    )

    val actualServiceCallResult = await(
      service.existingReg(registerWithEoriAndIdResponse, fullyPopulatedSubscriptionDetails, email, atarService)(
        mockHeaderCarrier
      )
    )
    val actualConnectorRequest = subscribeDataCaptor.getValue
    SubscriptionCallResult(actualServiceCallResult, actualConnectorRequest)
  }

  private def constructService(setupServiceConnector: SubscriptionServiceConnector => Unit) = {
    val mockSubscriptionServiceConnector = mock[SubscriptionServiceConnector]
    setupServiceConnector(mockSubscriptionServiceConnector)
    new SubscriptionService(mockSubscriptionServiceConnector)
  }

  private def assertSameJson(json: JsValue, expectedJson: JsValue) = {
    def assertSameRequestCommon = {
      val commonJson = (json \ "subscriptionCreateRequest" \ "requestCommon")
        .as[JsObject] - "receiptDate" - "acknowledgementReference"
      val expectedCommonJson = (expectedJson \ "subscriptionCreateRequest" \ "requestCommon")
        .as[JsObject] - "receiptDate" - "acknowledgementReference"
      commonJson shouldEqual expectedCommonJson
    }

    def assertSameRequestDetail = {
      val detailJson = (json \ "subscriptionCreateRequest" \ "requestDetail").as[JsObject] - "contactInformation"
      val expectedDetailJson = (expectedJson \ "subscriptionCreateRequest" \ "requestDetail")
        .as[JsObject] - "contactInformation"
      detailJson shouldEqual expectedDetailJson
    }

    assertSameRequestCommon
    assertSameRequestDetail
  }

}
