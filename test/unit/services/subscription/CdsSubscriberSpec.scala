/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.i18n.Lang.defaultLang
import play.api.i18n.{Messages, MessagesApi, MessagesImpl}
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.ResponseCommon
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{RecipientDetails, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactDetailsModel
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
  private val mockCdsOrganisationType    = mock[Option[CdsOrganisationType]]
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

    "call SubscriptionService when there is a cache hit" in {
      mockSuccessfulSubscribeGYEJourney(mockRegistrationDetails, subscriptionDetails)
      val inOrder =
        org.mockito.Mockito.inOrder(mockCdsFrontendDataCache, mockSubscriptionService, mockRegistrationConfirmService)

      whenReady(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Register)) {
        subscriptionResult =>
          subscriptionResult shouldBe SubscriptionSuccessful(
            Eori(eori),
            formBundleId,
            processingDate,
            Some(emailVerificationTimestamp)
          )
          inOrder.verify(mockCdsFrontendDataCache).registrationDetails(any[HeaderCarrier])
          inOrder
            .verify(mockSubscriptionService)
            .subscribe(
              meq(mockRegistrationDetails),
              meq(subscriptionDetails),
              meq(mockCdsOrganisationType),
              any[Journey.Value],
              any[Service]
            )(any[HeaderCarrier])
      }
    }

    "call SubscriptionService when there is a cache hit when user journey type is Subscribe and ContactDetails Missing Subscribe" in {
      val expectedEmail = "email@address.fromCache"
      when(mockRequestSessionData.selectedUserLocation(any())).thenReturn(Some(UserLocation.Uk))
      when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.successful(expectedEmail))

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
        org.mockito.Mockito.inOrder(mockCdsFrontendDataCache, mockSubscriptionService, mockHandleSubscriptionService)

      whenReady(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Subscribe)) {
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
            .existingReg(meq(stubRegisterWithEoriAndIdResponse), any[Eori], meq(expectedEmail), meq(Service.ATaR))(
              any[HeaderCarrier]
            )
          inOrder
            .verify(mockHandleSubscriptionService)
            .handleSubscription(
              meq(formBundleId),
              meq(
                RecipientDetails(
                  Service.ATaR,
                  Journey.Subscribe,
                  expectedEmail,
                  "",
                  Some("orgname"),
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
          Service.ATaR,
          Journey.Subscribe,
          expectedEmail,
          "TEST NAME",
          Some("orgname"),
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

      whenReady(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Subscribe)) {
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
              any[Eori],
              meq(expectedEmail),
              meq(Service.ATaR)
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
          Service.ATaR,
          Journey.Subscribe,
          expectedEmail,
          "TEST NAME",
          Some("orgname"),
          Some(processingDate)
        )

      whenReady(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Subscribe)) {
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
            .saveSub02Outcome(meq(Sub02Outcome(processingDate, "", Some(eori))))(meq(hc))
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
        await(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Subscribe))
      }
    }

    "throw an exception when there is no email in the cache" in {
      when(mockRequestSessionData.selectedUserLocation(any())).thenReturn(Some(UserLocation.Uk))
      when(mockCdsFrontendDataCache.email(any[HeaderCarrier])).thenReturn(Future.failed(new IllegalStateException))

      mockSuccessfulExistingRegistration(stubRegisterWithEoriAndIdResponse, subscriptionDetails)

      an[IllegalStateException] should be thrownBy {
        await(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Subscribe))
      }
    }

    "call SubscriptionService when there is only registration details in cache to get a pending subscription" in {
      mockPendingSubscribe(mockRegistrationDetails)
      val inOrder = org.mockito.Mockito.inOrder(mockCdsFrontendDataCache, mockSubscriptionService)

      whenReady(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Register)) {
        result =>
          result shouldBe SubscriptionPending(formBundleId, processingDate, Some(emailVerificationTimestamp))
          inOrder.verify(mockCdsFrontendDataCache).registrationDetails(any[HeaderCarrier])
          inOrder
            .verify(mockSubscriptionService)
            .subscribe(
              meq(mockRegistrationDetails),
              meq(subscriptionDetails),
              any[Option[CdsOrganisationType]],
              any[Journey.Value],
              any[Service]
            )(any[HeaderCarrier])
          verify(mockCdsFrontendDataCache, never).remove(any[HeaderCarrier])
      }
    }

    "propagate a failure when registrationDetails cache fails to be accessed" in {
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(Future.failed(emulatedFailure))

      val caught = the[UnsupportedOperationException] thrownBy {
        await(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Register))
      }
      caught shouldBe emulatedFailure
      verifyZeroInteractions(mockSubscriptionService)
      verify(mockCdsFrontendDataCache, never).remove(any[HeaderCarrier])
    }

    "propagate a failure when subscriptionDetailsHolder cache fails to be accessed" in {
      when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(mockRegistrationDetails)
      when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(Future.failed(emulatedFailure))

      val caught = the[UnsupportedOperationException] thrownBy {
        await(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Register))
      }
      caught shouldBe emulatedFailure
      verifyZeroInteractions(mockSubscriptionService)
      verify(mockCdsFrontendDataCache, never).remove(any[HeaderCarrier])
    }

    "call handle-subscription service when subscription successful" in {
      val expectedOrgName = "My Successful Org"
      val expectedRecipient = RecipientDetails(
        Service.ATaR,
        Journey.Register,
        "john.doe@example.com",
        "John Doe",
        Some("My Successful Org"),
        Some("19 April 2018")
      )

      mockSuccessfulSubscribeGYEJourney(mockRegistrationDetails, subscriptionDetails, expectedOrgName)
      whenReady(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Register)) {
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
            .saveSub02Outcome(meq(Sub02Outcome(processingDate, expectedOrgName, Some(eori))))(meq(hc))
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

    "call handle-subscription service when subscription returns pending status" in {
      val expectedOrgName = "My Pending Org"
      val expectedRecipient = RecipientDetails(
        Service.ATaR,
        Journey.Register,
        "john.doe@example.com",
        "John Doe",
        Some("My Pending Org"),
        Some("19 April 2018")
      )
      mockPendingSubscribe(mockRegistrationDetails, expectedOrgName)
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

      whenReady(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Register)) {
        result =>
          result shouldBe SubscriptionPending(formBundleId, processingDate, Some(emailVerificationTimestamp))
          val inOrder = org.mockito.Mockito.inOrder(mockCdsFrontendDataCache, mockHandleSubscriptionService)
          inOrder
            .verify(mockCdsFrontendDataCache)
            .saveSub02Outcome(meq(Sub02Outcome(processingDate, expectedOrgName)))(meq(hc))
          inOrder
            .verify(mockHandleSubscriptionService)
            .handleSubscription(
              meq(formBundleId),
              meq(expectedRecipient),
              any[TaxPayerId],
              meq(None),
              meq(Some(emailVerificationTimestamp)),
              any[SafeId]
            )(any[HeaderCarrier])
      }
    }

    "not call handle-subscription service when subscription returns a failure status" in {
      val expectedName = "Org Already Has EORI in PDS"
      mockFailedSubscribe(mockRegistrationDetails, subscriptionDetails, expectedName)
      whenReady(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Register)) {
        result =>
          result shouldBe SubscriptionFailed("EORI already exists", processingDate)
          verify(mockCdsFrontendDataCache).saveSub02Outcome(meq(Sub02Outcome(processingDate, expectedName)))(meq(hc))
          verifyZeroInteractions(mockHandleSubscriptionService)
      }
    }

    "fail when subscription succeeds but handle-subscription call fails" in {
      mockSuccessfulSubscribeGYEJourney(mockRegistrationDetails, subscriptionDetails)
      when(
        mockHandleSubscriptionService.handleSubscription(
          anyString,
          any[RecipientDetails],
          any[TaxPayerId],
          any[Option[Eori]],
          any[Option[DateTime]],
          any[SafeId]
        )(any[HeaderCarrier])
      ).thenReturn(Future.failed(emulatedFailure))

      val caught = the[UnsupportedOperationException] thrownBy {
        await(cdsSubscriber.subscribeWithCachedDetails(mockCdsOrganisationType, Service.ATaR, Journey.Register))
      }
      caught shouldBe emulatedFailure
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

  private def mockSuccessfulSubscribeGYEJourney(
    mockRegistrationDetails: RegistrationDetails,
    cachedSubscriptionDetailsHolder: SubscriptionDetails,
    registeredName: String = "orgName"
  ) = {
    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(mockRegistrationDetails))
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier]))
      .thenReturn(Future.successful(cachedSubscriptionDetailsHolder))
    when(
      mockSubscriptionService.subscribe(
        any[RegistrationDetails],
        any[SubscriptionDetails],
        any[Option[CdsOrganisationType]],
        any[Journey.Value],
        any[Service]
      )(any[HeaderCarrier])
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
    when(mockRegistrationDetails.name).thenReturn(registeredName)
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
        .existingReg(any[RegisterWithEoriAndIdResponse], any[Eori], any[String], any[Service])(any[HeaderCarrier])
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

  private def mockPendingSubscribe(
    cachedRegistrationDetails: RegistrationDetails,
    registeredName: String = "orgName"
  ) = {

    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(cachedRegistrationDetails)
    when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(subscriptionDetails)
    when(
      mockSubscriptionService
        .subscribe(
          any[RegistrationDetails],
          meq(subscriptionDetails),
          any[Option[CdsOrganisationType]],
          any[Journey.Value],
          any[Service]
        )(any[HeaderCarrier])
    ).thenReturn(Future.successful(SubscriptionPending(formBundleId, processingDate, Some(emailVerificationTimestamp))))
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
    when(mockRegistrationDetails.name).thenReturn(registeredName)
  }

  private def mockFailedSubscribe(
    registrationDetails: RegistrationDetails,
    subscriptionDetails: SubscriptionDetails,
    registeredName: String = "orgName"
  ) = {

    when(mockCdsFrontendDataCache.registrationDetails(any[HeaderCarrier])).thenReturn(registrationDetails)
    when(mockCdsFrontendDataCache.subscriptionDetails(any[HeaderCarrier])).thenReturn(subscriptionDetails)
    when(
      mockSubscriptionService
        .subscribe(
          any[RegistrationDetails],
          any[SubscriptionDetails],
          any[Option[CdsOrganisationType]],
          any[Journey.Value],
          any[Service]
        )(any[HeaderCarrier])
    ).thenReturn(Future.successful(SubscriptionFailed("EORI already exists", processingDate)))
    when(mockRegistrationDetails.name).thenReturn(registeredName)
  }

}
