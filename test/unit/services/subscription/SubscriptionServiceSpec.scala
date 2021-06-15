/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.FeatureFlags
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.{
  SubscriptionRequest,
  SubscriptionResponse
}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.EtmpTypeOfPerson
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionServiceSpec
    extends UnitSpec with MockitoSugar with BeforeAndAfterAll with Checkers with SubscriptionServiceTestData {
  private val mockHeaderCarrier = mock[HeaderCarrier]
  private val mockConfig        = mock[FeatureFlags]

  override def beforeAll() = {
    super.beforeAll()
    when(mockConfig.sub02UseServiceName).thenReturn(true)
  }

  private def subscriptionSuccessResultIgnoreTimestamp(
    expected: SubscriptionSuccessful,
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

      when(mockConfig.sub02UseServiceName).thenReturn(false)

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

      assertSameJson(
        Json.toJson(result.actualConnectorRequest),
        organisationAutomaticSubscriptionRequestWithoutServiceNameJson
      )
      result.actualServiceCallResult shouldEqual subscriptionSuccessResultIgnoreTimestamp(
        subscriptionSuccessResult,
        result.actualConnectorRequest
      )
    }
  }

  "Create request" should {

    "truncate sic code to 4 numbers by removing the rightmost number" in {
      val service = constructService(_ => None)
      val holder  = fullyPopulatedSubscriptionDetails.copy(sicCode = Some("12750"))
      val req     = service.createRequest(organisationRegistrationDetails, holder, None, atarService)

      req.subscriptionCreateRequest.requestDetail.principalEconomicActivity shouldBe Some("1275")
    }

    "replace empty city with a dash" in {
      val service = constructService(_ => None)
      val holder = fullyPopulatedSubscriptionDetails.copy(addressDetails =
        Some(AddressViewModel("some street", "", Some("AB99 3DW"), "GB"))
      )
      val req = service.createRequest(organisationRegistrationDetails, holder, None, atarService)

      req.subscriptionCreateRequest.requestDetail.CDSEstablishmentAddress.city shouldBe "-"
    }

    "replace empty postcode with a None" in {
      val service = constructService(_ => None)
      val holder = fullyPopulatedSubscriptionDetails.copy(addressDetails =
        Some(AddressViewModel("some street", "", Some(""), "GB"))
      )
      val req = service.createRequest(organisationRegistrationDetails, holder, None, atarService)

      req.subscriptionCreateRequest.requestDetail.CDSEstablishmentAddress.postalCode shouldBe None
    }

    "have correct person type for Individual Subscription" in {
      val service = constructService(_ => None)
      val holder  = fullyPopulatedSubscriptionDetails.copy(sicCode = Some("12750"))
      val req     = service.createRequest(individualRegistrationDetails, holder, None, atarService)

      req.subscriptionCreateRequest.requestDetail.typeOfPerson shouldBe Some(EtmpTypeOfPerson.NaturalPerson)
    }

    "throw an exception when unexpected registration details received" in {
      val service = constructService(_ => None)
      val holder  = fullyPopulatedSubscriptionDetails.copy(sicCode = Some("12750"))
      val thrown = intercept[IllegalStateException] {
        service.createRequest(RegistrationDetails.rdSafeId(SafeId("safeid")), holder, None, atarService)
      }
      thrown.getMessage shouldBe "Incomplete cache cannot complete journey"
    }

    "throw an exception when date of Establishment is None" in {
      val service = constructService(_ => None)
      val holder  = fullyPopulatedSubscriptionDetails.copy(dateEstablished = None)
      val thrown = intercept[IllegalStateException] {
        service.createRequest(
          organisationRegistrationDetails,
          holder,
          Some(CdsOrganisationType("third-country-organisation")),
          atarService
        )
      }
      thrown.getMessage shouldBe "Date Established must be present for an organisation subscription"
    }

    "populate the SubscriptionCreate Request when there is a plus (+) sign in the request on telephone number" in {
      val service = constructService(_ => None)
      val holder  = fullyPopulatedSubscriptionDetailsWithPlusSignInTelephone
      val req = service.createRequest(
        organisationRegistrationDetails,
        holder,
        Some(CdsOrganisationType("company")),
        atarService
      )
      req.subscriptionCreateRequest.requestDetail.contactInformation.flatMap(_.telephoneNumber) shouldBe Some(
        "+01632961234"
      )

    }

    "populate the SubscriptionCreate Request when there is a plus (+) sign in the request on fax number" in {
      val service = constructService(_ => None)
      val holder  = fullyPopulatedSubscriptionDetailsWithPlusSignInFaxNumber
      val req = service.createRequest(
        organisationRegistrationDetails,
        holder,
        Some(CdsOrganisationType("company")),
        atarService
      )
      req.subscriptionCreateRequest.requestDetail.contactInformation.flatMap(_.faxNumber) shouldBe Some("+01632961234")
    }

    "populate the SubscriptionCreate Request when there is a plus (+) sign in the request on telephone and fax number" in {
      val service = constructService(_ => None)
      val holder  = fullyPopulatedSubscriptionDetailsWithPlusSignInTelAndFaxNumber
      val req = service.createRequest(
        organisationRegistrationDetails,
        holder,
        Some(CdsOrganisationType("company")),
        atarService
      )
      req.subscriptionCreateRequest.requestDetail.contactInformation.flatMap(_.faxNumber) shouldBe Some("+01632961235")
      req.subscriptionCreateRequest.requestDetail.contactInformation.flatMap(_.telephoneNumber) shouldBe Some(
        "+01632961234"
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
    new SubscriptionService(mockSubscriptionServiceConnector, mockConfig)
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
