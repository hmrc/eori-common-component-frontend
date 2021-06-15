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

import base.{Injector, UnitSpec}
import common.support.testdata.TestData
import common.support.testdata.subscription.SubscriptionContactDetailsBuilder
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito.{when, _}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.i18n.Lang.defaultLang
import play.api.i18n.{Messages, MessagesApi, MessagesImpl}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.ResponseCommon
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{RecipientDetails, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationConfirmService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class CdsSubscriberSpec extends UnitSpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach with Injector {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(15, Millis)))

  private val mockSubscriptionService                      = mock[SubscriptionService]
  private val mockCdsFrontendDataCache                     = mock[SessionCache]
  private val mockRegistrationConfirmService               = mock[RegistrationConfirmService]
  private val mockSubscriptionFlowManager                  = mock[SubscriptionFlowManager]
  private val mockHandleSubscriptionService                = mock[HandleSubscriptionService]
  private val mockRegistrationDetails: RegistrationDetails = mock[RegistrationDetails]
  private val mockRequestSessionData                       = mock[RequestSessionData]
  private val mockSubscriptionDetailsService               = mock[SubscriptionDetailsService]

  implicit private val hc: HeaderCarrier                = mock[HeaderCarrier]
  implicit private val mockRequest: Request[AnyContent] = mock[Request[AnyContent]]

  implicit val messages: Messages = MessagesImpl(defaultLang, instanceOf[MessagesApi])

  private val eori                       = "EORI-Number"
  private val formBundleId               = "Form-Bundle-Id"
  private val processingDate             = "19 April 2018"
  private val emailVerificationTimestamp = TestData.emailVerificationTimestamp
  private val mockContactDetailsModel    = mock[ContactDetailsModel]
  private val contactDetails             = SubscriptionContactDetailsBuilder.contactDetailsWithMandatoryValuesOnly

  private val subscriptionDetails = SubscriptionDetails(
    contactDetails = Some(mockContactDetailsModel),
    eoriNumber = Some(eori),
    email = Some("test@example.com"),
    nameIdOrganisationDetails = Some(NameIdOrganisationMatchModel("orgname", "orgid"))
  )

  private val emulatedFailure = new UnsupportedOperationException("Emulation of service call failure")

  private val cdsSubscriber = new CdsSubscriber(
    mockSubscriptionService,
    mockCdsFrontendDataCache,
    mockHandleSubscriptionService,
    mockSubscriptionDetailsService,
    mockRequestSessionData
  )(global)

  override def beforeEach: Unit = {
    reset(
      mockCdsFrontendDataCache,
      mockSubscriptionService,
      mockCdsFrontendDataCache,
      mockRegistrationConfirmService,
      mockSubscriptionFlowManager,
      mockHandleSubscriptionService,
      mockRequestSessionData,
      mockRegistrationDetails
    )
    when(mockRegistrationDetails.sapNumber).thenReturn(TaxPayerId("some-SAP-number"))
    when(mockContactDetailsModel.contactDetails).thenReturn(contactDetails)
    when(mockSubscriptionDetailsService.cachedCustomsId).thenReturn(Future.successful(None))
  }

  "CdsSubscriber" should {

    "call SubscriptionService when there is a cache hit when user journey type is Subscribe and ContactDetails Missing Subscribe" in {
      val expectedEmail = "email@address.fromCache"
      when(mockRequestSessionData.selectedUserLocation(any())).thenReturn(Some(UserLocation.Uk))
      when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.successful(expectedEmail))
      when(mockCdsFrontendDataCache.saveSub02Outcome(any[Sub02Outcome])(any[HeaderCarrier]))
        .thenReturn(Future.successful(true))

      mockSuccessfulExistingRegistration(
        stubRegisterWithEoriAndIdResponse,
        subscriptionDetails.copy(email = Some(expectedEmail))
      )
      when(
        mockHandleSubscriptionService.handleSubscription(
          anyString,
          any[RecipientDetails],
          any[TaxPayerId],
          any[Option[Eori]],
          any[Option[DateTime]],
          any[SafeId]
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))
      val inOrder =
        org.mockito.Mockito.inOrder(
          mockCdsFrontendDataCache,
          mockSubscriptionService,
          mockCdsFrontendDataCache,
          mockHandleSubscriptionService
        )

      whenReady(cdsSubscriber.subscribeWithCachedDetails(atarService)) {
        subscriptionResult =>
          subscriptionResult shouldBe SubscriptionSuccessful(
            Eori(eori),
            formBundleId,
            processingDate,
            Some(emailVerificationTimestamp)
          )
          inOrder.verify(mockCdsFrontendDataCache).registerWithEoriAndIdResponse(any[HeaderCarrier])
          inOrder
            .verify(mockSubscriptionService)
            .existingReg(
              meq(stubRegisterWithEoriAndIdResponse),
              any[SubscriptionDetails],
              meq(expectedEmail),
              meq(atarService)
            )(any[HeaderCarrier])
          inOrder.verify(mockCdsFrontendDataCache).saveSub02Outcome(any())(any())
          inOrder
            .verify(mockHandleSubscriptionService)
            .handleSubscription(
              meq(formBundleId),
              meq(
                RecipientDetails(
                  atarService,
                  Journey.Subscribe,
                  expectedEmail,
                  "",
                  Some("New trading"),
                  Some(processingDate)
                )
              ),
              any[TaxPayerId],
              meq(Some(Eori(eori))),
              meq(Some(emailVerificationTimestamp)),
              any[SafeId]
            )(any[HeaderCarrier])
      }
    }

    "call SubscriptionService when there is a cache hit when user journey type is Subscribe and ContactDetails have Contact Name populated " in {
      val expectedEmail = "email@address.fromCache"
      when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.successful(expectedEmail))

      val expectedRecipient =
        RecipientDetails(
          atarService,
          Journey.Subscribe,
          expectedEmail,
          "TEST NAME",
          Some("New trading"),
          Some(processingDate)
        )
      when(mockRequestSessionData.selectedUserLocation(any())).thenReturn(Some(UserLocation.Uk))
      mockSuccessfulExistingRegistration(
        stubRegisterWithEoriAndIdResponseWithContactDetails,
        subscriptionDetails.copy(email = Some(expectedEmail))
      )
      when(
        mockHandleSubscriptionService.handleSubscription(
          anyString,
          any[RecipientDetails],
          any[TaxPayerId],
          any[Option[Eori]],
          any[Option[DateTime]],
          any[SafeId]
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      whenReady(cdsSubscriber.subscribeWithCachedDetails(atarService)) {
        subscriptionResult =>
          subscriptionResult shouldBe SubscriptionSuccessful(
            Eori(eori),
            formBundleId,
            processingDate,
            Some(emailVerificationTimestamp)
          )

          val inOrder = org.mockito.Mockito
            .inOrder(mockCdsFrontendDataCache, mockSubscriptionService, mockHandleSubscriptionService)
          inOrder.verify(mockCdsFrontendDataCache).registerWithEoriAndIdResponse(any[HeaderCarrier])
          inOrder
            .verify(mockSubscriptionService)
            .existingReg(
              meq(stubRegisterWithEoriAndIdResponseWithContactDetails),
              any[SubscriptionDetails],
              meq(expectedEmail),
              meq(atarService)
            )(any[HeaderCarrier])
          inOrder
            .verify(mockHandleSubscriptionService)
            .handleSubscription(
              meq(formBundleId),
              meq(expectedRecipient),
              any[TaxPayerId],
              meq(Some(Eori(eori))),
              meq(Some(emailVerificationTimestamp)),
              any[SafeId]
            )(any[HeaderCarrier])
      }
    }

    "call handle-subscription service when subscription successful when Journey is Subscribe " in {
      mockSuccessfulExistingRegistration(stubRegisterWithEoriAndIdResponseWithContactDetails, subscriptionDetails)
      val expectedEmail = "test@example.com"
      when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.successful(expectedEmail))

      val expectedRecipient =
        RecipientDetails(
          atarService,
          Journey.Subscribe,
          expectedEmail,
          "TEST NAME",
          Some("New trading"),
          Some(processingDate)
        )

      whenReady(cdsSubscriber.subscribeWithCachedDetails(atarService)) {
        result =>
          result shouldBe SubscriptionSuccessful(
            Eori(eori),
            formBundleId,
            processingDate,
            Some(emailVerificationTimestamp)
          )

          val inOrder = org.mockito.Mockito.inOrder(mockCdsFrontendDataCache, mockHandleSubscriptionService)
          inOrder
            .verify(mockCdsFrontendDataCache)
            .saveSub02Outcome(meq(Sub02Outcome(processingDate, "New trading", Some(eori))))(meq(hc))
          inOrder
            .verify(mockHandleSubscriptionService)
            .handleSubscription(
              meq(formBundleId),
              meq(expectedRecipient),
              any[TaxPayerId],
              meq(Some(Eori(eori))),
              meq(Some(emailVerificationTimestamp)),
              any[SafeId]
            )(any[HeaderCarrier])
      }
    }

    "call to SubscriptionService Future should fail when there is no email in subscription Details when user journey type is Subscribe" in {
      when(mockRequestSessionData.selectedUserLocation(any())).thenReturn(Some(UserLocation.Uk))
      when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.failed(new IllegalStateException))

      mockSuccessfulExistingRegistration(stubRegisterWithEoriAndIdResponseWithContactDetails, subscriptionDetails)

      an[IllegalStateException] should be thrownBy {
        await(cdsSubscriber.subscribeWithCachedDetails(atarService))
      }
    }

    "throw an exception when there is no email in the cache" in {
      when(mockRequestSessionData.selectedUserLocation(any())).thenReturn(Some(UserLocation.Uk))
      when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.failed(new IllegalStateException))

      mockSuccessfulExistingRegistration(stubRegisterWithEoriAndIdResponse, subscriptionDetails)

      an[IllegalStateException] should be thrownBy {
        await(cdsSubscriber.subscribeWithCachedDetails(atarService))
      }
    }

    "propagate a failure when subscriptionDetailsHolder cache fails to be accessed" in {
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(mockRegistrationDetails)
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(Future.failed(emulatedFailure))

      val caught = the[UnsupportedOperationException] thrownBy {
        await(cdsSubscriber.subscribeWithCachedDetails(atarService))
      }
      caught shouldBe emulatedFailure
      verifyZeroInteractions(mockSubscriptionService)
      verify(mockCdsFrontendDataCache, never).remove(any[HeaderCarrier])
    }
  }

  private def stubRegisterWithEoriAndIdResponse = stubRegister(false)

  private def stubRegisterWithEoriAndIdResponseWithContactDetails: RegisterWithEoriAndIdResponse = stubRegister(true)

  private def stubRegister(useContactDetail: Boolean): RegisterWithEoriAndIdResponse = {
    val processingDate = DateTime.now.withTimeAtStartOfDay()
    val responseCommon = ResponseCommon(status = "OK", processingDate = processingDate)
    val trader         = Trader(fullName = "New trading", shortName = "nt")
    val establishmentAddress =
      EstablishmentAddress(streetAndNumber = "Street Address", city = "City", countryCode = "GB")
    val cd = if (useContactDetail) Some(ContactDetail(establishmentAddress, "TEST NAME", None, None, None)) else None
    val responseData: ResponseData = ResponseData(
      SAFEID = "SomeSafeId",
      trader = trader,
      establishmentAddress = establishmentAddress,
      hasInternetPublication = true,
      startDate = "2018-01-01",
      contactDetail = cd
    )
    val registerWithEoriAndIdResponseDetail = RegisterWithEoriAndIdResponseDetail(
      outcome = Some("PASS"),
      caseNumber = Some("case no 1"),
      responseData = Some(responseData)
    )
    RegisterWithEoriAndIdResponse(responseCommon, Some(registerWithEoriAndIdResponseDetail))
  }

  private def mockSuccessfulExistingRegistration(
    cachedRegistrationDetails: RegisterWithEoriAndIdResponse,
    cachedSubscriptionDetailsHolder: SubscriptionDetails
  ) = {

    when(mockCdsFrontendDataCache.registerWithEoriAndIdResponse(any[HeaderCarrier]))
      .thenReturn(Future.successful(cachedRegistrationDetails))

    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(cachedSubscriptionDetailsHolder))

    when(
      mockSubscriptionService
        .existingReg(any[RegisterWithEoriAndIdResponse], any[SubscriptionDetails], any[String], any[Service])(
          any[HeaderCarrier]
        )
    ).thenReturn(
      Future
        .successful(SubscriptionSuccessful(Eori(eori), formBundleId, processingDate, Some(emailVerificationTimestamp)))
    )
    when(
      mockHandleSubscriptionService.handleSubscription(
        anyString,
        any[RecipientDetails],
        any[TaxPayerId],
        any[Option[Eori]],
        any[Option[DateTime]],
        any[SafeId]
      )(any[HeaderCarrier])
    ).thenReturn(Future.successful(()))
    when(mockCdsFrontendDataCache.saveSub02Outcome(any[Sub02Outcome])(any[HeaderCarrier]))
      .thenReturn(Future.successful(true))
  }

}
