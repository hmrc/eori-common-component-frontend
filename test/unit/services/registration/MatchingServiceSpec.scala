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

package unit.services.registration

import base.UnitSpec
import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers, Mockito}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.MatchingServiceConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Individual
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.matching.{
  MatchingRequestHolder,
  MatchingResponse,
  Organisation
}
import play.api.libs.json._
import play.api.mvc.{AnyContent, Request}
import play.mvc.Http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RequestCommonGenerator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.MatchingService
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class MatchingServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with MatchingServiceTestData {

  private val mockMatchingServiceConnector = mock[MatchingServiceConnector]
  private val mockDetailsCreator           = mock[RegistrationDetailsCreator]
  private val mockRequestSessionData       = mock[RequestSessionData]
  private val mockDetails                  = mock[RegistrationDetails]

  private val mockRequest = mock[Request[AnyContent]]

  private val mockHeaderCarrier          = mock[HeaderCarrier]
  private val mockRequestCommonGenerator = mock[RequestCommonGenerator]
  private val mockCache                  = mock[SessionCache]
  private val loggedInCtUser             = mock[LoggedInUserWithEnrolments]
  private val mockGroupId                = mock[GroupId]

  private val service = new MatchingService(
    mockMatchingServiceConnector,
    mockRequestCommonGenerator,
    mockDetailsCreator,
    mockCache,
    mockRequestSessionData
  )(global)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(mockGroupId.id).thenReturn("mockedGroupId")
    when(loggedInCtUser.groupId).thenReturn(Some("mockedGroupId"))

    when(mockRequestCommonGenerator.generate())
      .thenReturn(ExpectedRequestCommon)

    when(
      mockCache.saveRegistrationDetails(
        ArgumentMatchers.any[RegistrationDetails],
        ArgumentMatchers.any[GroupId],
        ArgumentMatchers.any[Option[CdsOrganisationType]]
      )(ArgumentMatchers.any[HeaderCarrier])
    ).thenReturn(true)

    when(
      mockCache.saveRegistrationDetails(ArgumentMatchers.any[RegistrationDetails])(ArgumentMatchers.any[HeaderCarrier])
    ).thenReturn(true)
  }

  override protected def afterEach(): Unit = {
    Mockito.reset(mockMatchingServiceConnector, mockDetailsCreator, mockCache, loggedInCtUser)

    super.afterEach()
  }

  "matching an organisation with id and name" should {

    "return failed future for matchBusinessWithOrganisationName when connector fails to return result" in {
      when(
        mockMatchingServiceConnector
          .lookup(ArgumentMatchers.any())(ArgumentMatchers.any())
      ).thenReturn(Future.failed(UpstreamErrorResponse("failure", INTERNAL_SERVER_ERROR, 1)))

      val caught = intercept[UpstreamErrorResponse] {
        await(
          service.matchBusiness(
            Utr("some-utr"),
            Organisation("name", CorporateBody),
            establishmentDate = None,
            mockGroupId
          )(mockRequest, mockHeaderCarrier)
        )
      }
      caught.statusCode shouldBe 500
      caught.getMessage shouldBe "failure"
    }

    "for UTR and name match, call matching api with correct values" in {
      when(
        mockMatchingServiceConnector
          .lookup(ArgumentMatchers.any())(ArgumentMatchers.any())
      ).thenReturn(Future.successful(Some(matchSuccessResponse)))

      await(
        service.matchBusiness(utr, Organisation("someOrg", Partnership), establishmentDate = None, mockGroupId)(
          mockRequest,
          mockHeaderCarrier
        )
      ) shouldBe true

      val matchBusinessDataCaptor =
        ArgumentCaptor.forClass(classOf[MatchingRequestHolder])
      verify(mockMatchingServiceConnector).lookup(matchBusinessDataCaptor.capture())(ArgumentMatchers.any())

      Json.toJson(matchBusinessDataCaptor.getValue) shouldBe utrAndNameRequestJson
    }

    "for UTR with a K, call matching api without the K" in {
      when(
        mockMatchingServiceConnector
          .lookup(ArgumentMatchers.any())(ArgumentMatchers.any())
      ).thenReturn(Future.successful(Some(matchSuccessResponse)))

      await(
        service.matchBusiness(
          Utr(utrId + "K"),
          Organisation("someOrg", Partnership),
          establishmentDate = None,
          mockGroupId
        )(mockRequest, mockHeaderCarrier)
      ) shouldBe true

      val matchBusinessDataCaptor =
        ArgumentCaptor.forClass(classOf[MatchingRequestHolder])
      verify(mockMatchingServiceConnector).lookup(matchBusinessDataCaptor.capture())(ArgumentMatchers.any())

      Json.toJson(matchBusinessDataCaptor.getValue) shouldBe utrAndNameRequestJson
    }

    "for UTR with a k, call matching api without the k" in {
      when(
        mockMatchingServiceConnector
          .lookup(ArgumentMatchers.any())(ArgumentMatchers.any())
      ).thenReturn(Future.successful(Some(matchSuccessResponse)))

      await(
        service.matchBusiness(
          Utr(utrId + "k"),
          Organisation("someOrg", Partnership),
          establishmentDate = None,
          mockGroupId
        )(mockRequest, mockHeaderCarrier)
      ) shouldBe true

      val matchBusinessDataCaptor =
        ArgumentCaptor.forClass(classOf[MatchingRequestHolder])
      verify(mockMatchingServiceConnector).lookup(matchBusinessDataCaptor.capture())(ArgumentMatchers.any())

      Json.toJson(matchBusinessDataCaptor.getValue) shouldBe utrAndNameRequestJson
    }

    "for EORI and name match, call matching api with correct values" in {
      when(
        mockMatchingServiceConnector
          .lookup(ArgumentMatchers.any())(ArgumentMatchers.any())
      ).thenReturn(Future.successful(Some(matchSuccessResponse)))

      await(
        service.matchBusiness(eori, Organisation("someOrg", UnincorporatedBody), someEstablishmentDate, mockGroupId)(
          mockRequest,
          mockHeaderCarrier
        )
      ) shouldBe true

      val matchBusinessDataCaptor =
        ArgumentCaptor.forClass(classOf[MatchingRequestHolder])
      verify(mockMatchingServiceConnector).lookup(matchBusinessDataCaptor.capture())(ArgumentMatchers.any())
      Json.toJson(matchBusinessDataCaptor.getValue) shouldBe eoriAndNameRequestJson
    }

    "store registration details in cache when found a match" in {
      when(
        mockMatchingServiceConnector
          .lookup(ArgumentMatchers.any())(ArgumentMatchers.any())
      ).thenReturn(Future.successful(Some(matchSuccessResponse)))

      when(
        mockDetailsCreator.registrationDetails(
          ArgumentMatchers.eq(matchSuccessResponse.registerWithIDResponse),
          ArgumentMatchers.eq(utr),
          ArgumentMatchers.eq(None)
        )
      ).thenReturn(mockDetails)

      await(
        service.matchBusiness(utr, Organisation("someOrg", Partnership), establishmentDate = None, mockGroupId)(
          mockRequest,
          mockHeaderCarrier
        )
      ) shouldBe true

      verify(mockCache).saveRegistrationDetails(
        ArgumentMatchers.eq(mockDetails),
        ArgumentMatchers.eq(mockGroupId),
        ArgumentMatchers.any()
      )(ArgumentMatchers.eq(mockHeaderCarrier))
    }
  }

  private def assertMatchIndividualWithUtr(
    connectorResponse: Option[MatchingResponse],
    expectedServiceCallResult: Boolean
  ): Unit = {
    when(
      mockMatchingServiceConnector
        .lookup(ArgumentMatchers.any())(ArgumentMatchers.any())
    ).thenReturn(Future.successful(connectorResponse))

    await(
      service.matchIndividualWithId(utr, individual, mockGroupId)(mockHeaderCarrier)
    ) shouldBe expectedServiceCallResult

    val matchBusinessDataCaptor =
      ArgumentCaptor.forClass(classOf[MatchingRequestHolder])
    verify(mockMatchingServiceConnector).lookup(matchBusinessDataCaptor.capture())(ArgumentMatchers.any())

    Json.toJson(matchBusinessDataCaptor.getValue) shouldBe utrIndividualRequestJson
  }

  "matching an individual with a utr" should {

    "call matching api with matched values" in
      assertMatchIndividualWithUtr(
        connectorResponse = Some(matchIndividualSuccessResponse),
        expectedServiceCallResult = true
      )

    "call matching api with unmatched values" in
      assertMatchIndividualWithUtr(connectorResponse = None, expectedServiceCallResult = false)

    "store match details in cache when found a match" in {
      when(
        mockDetailsCreator.registrationDetails(
          ArgumentMatchers
            .eq(matchIndividualSuccessResponse.registerWithIDResponse),
          ArgumentMatchers.eq(utr),
          ArgumentMatchers.eq(Some(individualLocalDateOfBirth))
        )
      ).thenReturn(mockDetails)

      assertMatchIndividualWithUtr(
        connectorResponse = Some(matchIndividualSuccessResponse),
        expectedServiceCallResult = true
      )
      verify(mockCache).saveRegistrationDetails(
        ArgumentMatchers.eq(mockDetails),
        ArgumentMatchers.eq(mockGroupId),
        ArgumentMatchers.any()
      )(ArgumentMatchers.eq(mockHeaderCarrier))

    }
  }

  private def assertMatchIndividualWithEori(
    individual: Individual = individualWithMiddleName,
    expectedRequestJson: JsValue = eoriIndividualRequestJson,
    connectorResponse: Option[MatchingResponse],
    expectedServiceCallResult: Boolean
  ): Unit = {
    when(
      mockMatchingServiceConnector
        .lookup(ArgumentMatchers.any())(ArgumentMatchers.any())
    ).thenReturn(Future.successful(connectorResponse))

    await(
      service.matchIndividualWithId(eori, individual, mockGroupId)(mockHeaderCarrier)
    ) shouldBe expectedServiceCallResult

    val matchBusinessDataCaptor =
      ArgumentCaptor.forClass(classOf[MatchingRequestHolder])
    verify(mockMatchingServiceConnector).lookup(matchBusinessDataCaptor.capture())(ArgumentMatchers.any())

    Json.toJson(matchBusinessDataCaptor.getValue) shouldBe expectedRequestJson
  }

  "matching an individual with an EORI number" should {

    "call matching api with matched values" in
      assertMatchIndividualWithEori(
        connectorResponse = Some(matchIndividualSuccessResponse),
        expectedServiceCallResult = true
      )

    "call matching api with unmatched values" in
      assertMatchIndividualWithEori(connectorResponse = None, expectedServiceCallResult = false)

    "store match details in cache when found a match" in {
      when(
        mockDetailsCreator.registrationDetails(
          ArgumentMatchers
            .eq(matchIndividualSuccessResponse.registerWithIDResponse),
          ArgumentMatchers.eq(eori),
          ArgumentMatchers.eq(Some(individualLocalDateOfBirth))
        )
      ).thenReturn(mockDetails)

      assertMatchIndividualWithEori(
        connectorResponse = Some(matchIndividualSuccessResponse),
        expectedServiceCallResult = true
      )
      verify(mockCache).saveRegistrationDetails(
        ArgumentMatchers.eq(mockDetails),
        ArgumentMatchers.eq(mockGroupId),
        ArgumentMatchers.any()
      )(ArgumentMatchers.eq(mockHeaderCarrier))

    }
  }
}
