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

package unit.controllers.registration

import java.time.{LocalDate, LocalDateTime}
import org.mockito.ArgumentMatchers.{any, anyString, eq => meq, contains}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.SUB09SubscriptionDisplayConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.SubscriptionRecoveryController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{RecipientDetails, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RandomUUIDGenerator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  HandleSubscriptionService,
  SubscriptionDetailsService,
  TaxEnrolmentsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.error_template
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SubscriptionInfoBuilder._
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class SubscriptionRecoveryControllerSpec
    extends ControllerSpec with MockitoSugar with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector                     = mock[AuthConnector]
  private val mockAuthAction                        = authAction(mockAuthConnector)
  private val mockSessionCache: SessionCache        = mock[SessionCache]
  private val mockSUB09SubscriptionDisplayConnector = mock[SUB09SubscriptionDisplayConnector]
  private val mockSub01Outcome                      = mock[Sub01Outcome]
  private val mockHandleSubscriptionService         = mock[HandleSubscriptionService]
  private val mockOrgRegistrationDetails            = mock[RegistrationDetailsOrganisation]
  private val mockSubscriptionDetailsHolder         = mock[SubscriptionDetails]
  private val mockRegisterWithEoriAndIdResponse     = mock[RegisterWithEoriAndIdResponse]
  private val mockRandomUUIDGenerator               = mock[RandomUUIDGenerator]
  private val contactDetails                        = mock[ContactDetailsModel]
  private val mockTaxEnrolmentsService              = mock[TaxEnrolmentsService]
  private val mockSubscriptionDetailsService        = mock[SubscriptionDetailsService]
  private val mockRequestSessionData                = mock[RequestSessionData]

  private val errorTemplateView = instanceOf[error_template]

  private val controller = new SubscriptionRecoveryController(
    mockAuthAction,
    mockHandleSubscriptionService,
    mockTaxEnrolmentsService,
    mockSessionCache,
    mockSUB09SubscriptionDisplayConnector,
    mcc,
    errorTemplateView,
    mockRandomUUIDGenerator,
    mockRequestSessionData,
    mockSubscriptionDetailsService
  )(global)

  def registerWithEoriAndIdResponseDetail: Option[RegisterWithEoriAndIdResponseDetail] = {
    val trader               = Trader(fullName = "New trading", shortName = "nt")
    val establishmentAddress = EstablishmentAddress(streetAndNumber = "new street", city = "leeds", countryCode = "GB")
    val responseData: ResponseData = ResponseData(
      SAFEID = "SomeSafeId",
      trader = trader,
      establishmentAddress = establishmentAddress,
      hasInternetPublication = true,
      startDate = "2018-01-01"
    )
    Some(
      RegisterWithEoriAndIdResponseDetail(
        outcome = Some("PASS"),
        caseNumber = Some("case no 1"),
        responseData = Some(responseData)
      )
    )
  }

  override def beforeEach: Unit = {
    reset(
      mockSessionCache,
      mockOrgRegistrationDetails,
      mockRequestSessionData,
      mockSubscriptionDetailsService,
      mockTaxEnrolmentsService
    )
    when(mockRandomUUIDGenerator.generateUUIDAsString).thenReturn("MOCKUUID12345")
  }

  "Viewing the Organisation Name Matching form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.complete(atarService))

    def setupMockCommon() = {
      when(mockSessionCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockSubscriptionDetailsHolder))
      when(mockSUB09SubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(fullyPopulatedResponse)))
      when(mockSubscriptionDetailsHolder.contactDetails).thenReturn(Some(contactDetails))
      when(contactDetails.emailAddress).thenReturn("test@example.com")
      when(mockSubscriptionDetailsHolder.email).thenReturn(Some("test@example.com"))
      when(mockSessionCache.email(any[HeaderCarrier])).thenReturn(Future.successful("test@example.com"))

      when(mockSessionCache.sub01Outcome(any[HeaderCarrier])).thenReturn(Future.successful(mockSub01Outcome))
      when(mockSub01Outcome.processedDate).thenReturn("01 May 2016")

      when(mockSessionCache.saveSub02Outcome(any[Sub02Outcome])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))
      when(
        mockHandleSubscriptionService.handleSubscription(
          anyString,
          any[RecipientDetails],
          any[TaxPayerId],
          any[Option[Eori]],
          any[Option[LocalDateTime]],
          any[SafeId]
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(result = ()))
      when(mockSubscriptionDetailsHolder.nameDobDetails)
        .thenReturn(Some(NameDobMatchModel("fname", Some("mName"), "lname", LocalDate.parse("2019-01-01"))))
    }

    "call Enrolment Complete with successful SUB09 call for Subscription UK journey" in {
      setupMockCommon()

      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber"))

      when(mockSessionCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail).thenReturn(registerWithEoriAndIdResponseDetail)

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]], any[Service])(any[HeaderCarrier])
      ).thenReturn(Future.successful(NO_CONTENT))

      val expectedFormBundleId = fullyPopulatedResponse.responseCommon.returnParameters
        .flatMap(_.find(_.paramName.equals("ETMPFORMBUNDLENUMBER")).map(_.paramValue)).get + "atar"

      callEnrolmentComplete() { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some("/customs-enrolment-services/atar/subscribe/complete")
      }

      verify(mockTaxEnrolmentsService).issuerCall(
        contains(expectedFormBundleId),
        meq(Eori("testEORInumber")),
        any[Option[LocalDate]],
        meq(atarService)
      )(any[HeaderCarrier])

      verify(mockHandleSubscriptionService).handleSubscription(
        contains(expectedFormBundleId),
        any(),
        any(),
        meq(Some(Eori("testEORInumber"))),
        any(),
        any()
      )(any())
    }
    "call Enrolment Complete with successful SUB09 call for Subscription ROW journey" in {
      setupMockCommon()

      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("eu"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(Some(Utr("someUtr"))))
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber2"))
      when(mockSessionCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail).thenReturn(registerWithEoriAndIdResponseDetail)

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]], any[Service])(any[HeaderCarrier])
      ).thenReturn(Future.successful(NO_CONTENT))

      val expectedFormBundleId = fullyPopulatedResponse.responseCommon.returnParameters
        .flatMap(_.find(_.paramName.equals("ETMPFORMBUNDLENUMBER")).map(_.paramValue)).get + "atar"

      callEnrolmentComplete() { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some("/customs-enrolment-services/atar/subscribe/complete")
      }
      verify(mockTaxEnrolmentsService).issuerCall(
        contains(expectedFormBundleId),
        meq(Eori("testEORInumber2")),
        any[Option[LocalDate]],
        meq(atarService)
      )(any[HeaderCarrier])

      verify(mockHandleSubscriptionService).handleSubscription(
        contains(expectedFormBundleId),
        any(),
        any(),
        meq(Some(Eori("testEORInumber"))),
        any(),
        any()
      )(any())
    }
    "call Enrolment Complete with successful SUB09 call for Subscription ROW journey without Identifier" in {
      setupMockCommon()

      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("eu"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[HeaderCarrier]))
        .thenReturn(Future.successful(None))
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber3"))
      when(mockSessionCache.registrationDetails(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      when(mockOrgRegistrationDetails.safeId).thenReturn(SafeId("testsafeId"))

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]], any[Service])(any[HeaderCarrier])
      ).thenReturn(Future.successful(NO_CONTENT))

      val expectedFormBundleId = fullyPopulatedResponse.responseCommon.returnParameters
        .flatMap(_.find(_.paramName.equals("ETMPFORMBUNDLENUMBER")).map(_.paramValue)).get + "atar"

      callEnrolmentComplete() { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some("/customs-enrolment-services/atar/subscribe/complete")
      }
      verify(mockTaxEnrolmentsService).issuerCall(
        contains(expectedFormBundleId),
        meq(Eori("testEORInumber3")),
        any[Option[LocalDate]],
        meq(atarService)
      )(any[HeaderCarrier])

      verify(mockHandleSubscriptionService).handleSubscription(
        contains(expectedFormBundleId),
        any(),
        any(),
        meq(Some(Eori("testEORInumber"))),
        any(),
        any()
      )(any())
    }

    "throw IllegalStateException when SUB09 call returns response without Form Bundle for Subscription UK journey" in {
      setupMockCommon()
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSUB09SubscriptionDisplayConnector.subscriptionDisplay(any())(any[HeaderCarrier]))
        .thenReturn(Future.successful(Right(fullyPopulatedResponseWithoutFormBundle)))
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber"))

      when(mockSessionCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail).thenReturn(registerWithEoriAndIdResponseDetail)

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]], any[Service])(any[HeaderCarrier])
      ).thenReturn(Future.successful(NO_CONTENT))

      intercept[IllegalStateException] {
        await(controller.complete(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
      }
    }
    "throw IllegalStateException when Tax Enrolment Issuer call returns invalid response for Subscription UK journey" in {
      setupMockCommon()
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(Some("testEORInumber"))

      when(mockSessionCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
        .thenReturn(Future.successful(mockRegisterWithEoriAndIdResponse))
      when(mockRegisterWithEoriAndIdResponse.responseDetail).thenReturn(registerWithEoriAndIdResponseDetail)

      when(
        mockTaxEnrolmentsService
          .issuerCall(anyString, any[Eori], any[Option[LocalDate]], any[Service])(any[HeaderCarrier])
      ).thenReturn(Future.successful(INTERNAL_SERVER_ERROR))

      intercept[IllegalArgumentException] {
        await(controller.complete(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
      }
    }
  }

  def callEnrolmentComplete(userId: String = defaultUserId)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    test(controller.complete(atarService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

}
