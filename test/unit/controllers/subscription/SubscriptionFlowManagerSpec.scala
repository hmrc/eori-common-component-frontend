/*
 * Copyright 2025 HM Revenue & Customs
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

package unit.controllers.subscription

import base.UnitSpec
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.Tables.Table
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request, Session}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType.{Company, Individual, SoleTrader}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{
  DataUnavailableException,
  RequestSessionData,
  SessionCache
}
import util.ControllerSpec

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class SubscriptionFlowManagerSpec
    extends UnitSpec with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach with ControllerSpec {

  private val mockRequestSessionData   = mock[RequestSessionData]
  private val mockCdsFrontendDataCache = mock[SessionCache]

  val controller =
    new SubscriptionFlowManager(mockRequestSessionData, mockCdsFrontendDataCache)(global)

  private val mockOrgRegistrationDetails        = mock[RegistrationDetailsOrganisation]
  private val mockIndividualRegistrationDetails = mock[RegistrationDetailsIndividual]
  private val mockRegistrationDetails           = mock[RegistrationDetailsSafeId]
  private val mockSession                       = mock[Session]

  private val mockRequest = mock[Request[AnyContent]]

  private val mockSubscriptionFlow = mock[SubscriptionFlow]

  val noSubscriptionFlowInSessionException: DataUnavailableException = DataUnavailableException(
    "No subscription flow in session."
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(mockRequestSessionData.storeUserSubscriptionFlow(any[SubscriptionFlow], any[String])(any[Request[AnyContent]]))
      .thenReturn(mockSession)
    when(mockCdsFrontendDataCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[Request[AnyContent]]))
      .thenReturn(Future.successful(true))
  }

  override protected def afterEach(): Unit = {
    reset(mockRequestSessionData)
    reset(mockSession)
    reset(mockCdsFrontendDataCache)
    super.afterEach()
  }

  "Getting current subscription flow" should {
    "return value from session when stored there before" in {
      when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(mockSubscriptionFlow)

      controller.currentSubscriptionFlow(mockRequest) shouldBe mockSubscriptionFlow
    }

    "fail when there was no flow stored in session before" in {
      when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]]))
        .thenThrow(noSubscriptionFlowInSessionException)

      intercept[DataUnavailableException](
        controller.currentSubscriptionFlow(mockRequest)
      ) shouldBe noSubscriptionFlowInSessionException
    }
  }

  "Flow already started" should {
    val values = Table(
      ("flow", "currentPage", "expectedStepNumber", "expectedTotalSteps", "expectedNextPage"),
      (OrganisationFlow, NameUtrDetailsSubscriptionFlowPage, 1, 3, DateOfEstablishmentSubscriptionFlowPageMigrate),
      (OrganisationFlow, DateOfEstablishmentSubscriptionFlowPageMigrate, 2, 3, AddressDetailsSubscriptionFlowPage),
      (OrganisationFlow, AddressDetailsSubscriptionFlowPage, 3, 3, ReviewDetailsPageSubscription),
      (SoleTraderFlow, NameDobDetailsSubscriptionFlowPage, 1, 3, HowCanWeIdentifyYouSubscriptionFlowPage),
      (SoleTraderFlow, HowCanWeIdentifyYouSubscriptionFlowPage, 2, 3, AddressDetailsSubscriptionFlowPage),
      (SoleTraderFlow, AddressDetailsSubscriptionFlowPage, 3, 3, ReviewDetailsPageSubscription)
    )

    TableDrivenPropertyChecks.forAll(values) {
      (
        flow: SubscriptionFlow,
        currentPage: SubscriptionPage,
        expectedStepNumber: Int,
        expectedTotalSteps: Int,
        expectedNextPage: SubscriptionPage
      ) =>
        when(mockRequestSessionData.userSubscriptionFlow(mockRequest)).thenReturn(flow)
        val actual = controller.stepInformation(currentPage)(mockRequest)

        s"${flow.name} flow: current step is $expectedStepNumber when currentPage is $currentPage" in {
          actual.stepNumber shouldBe expectedStepNumber
        }

        s"${flow.name} flow: total Number of steps are $expectedTotalSteps when currentPage is $currentPage" in {
          actual.totalSteps shouldBe expectedTotalSteps
        }

        s"${flow.name} flow: next page is $expectedNextPage when currentPage is $currentPage" in {
          actual.nextPage shouldBe expectedNextPage
        }
    }
  }

  "First Page" should {

    "start Individual Subscription Flow for individual" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)

      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(mockIndividualRegistrationDetails))
      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(Some(UserLocationPage), Individual, atarService)(mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession

      verify(mockRequestSessionData)
        .storeUserSubscriptionFlow(IndividualFlow, UserLocationPage.url(atarService))(mockRequest)
    }

    "start Individual Subscription Flow for individual for ROW journey" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)
      when(mockIndividualRegistrationDetails.customsId).thenReturn(Some(Utr("1111111111k")))
      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(mockIndividualRegistrationDetails))
      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(Some(UserLocationPage), Individual, atarService)(mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession

      verify(mockRequestSessionData)
        .storeUserSubscriptionFlow(IndividualFlow, UserLocationPage.url(atarService))(mockRequest)
    }

    "throw DataUnavailableException when registration details is not available in cache" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)

      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(mockRegistrationDetails))
      intercept[DataUnavailableException] {
        await(controller.startSubscriptionFlow(Some(UserLocationPage), Individual, atarService)(mockRequest))
      }

    }
    "start Corporate Subscription Flow when cached registration details are for an Organisation" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)

      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(None, Company, atarService)(mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession

      verify(mockRequestSessionData)
        .storeUserSubscriptionFlow(OrganisationFlow, UserLocationPage.url(atarService))(mockRequest)
    }

    "start Corporate Subscription Flow when selected organisation type is Sole Trader" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))

      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(mockIndividualRegistrationDetails))
      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(None, SoleTrader, atarService)(mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession
      verify(mockRequestSessionData).storeUserSubscriptionFlow(SoleTraderFlow, UserLocationPage.url(atarService))(
        mockRequest
      )
    }

    "start Corporate Subscription Flow when cached registration details are for an Organisation Reg-existing (a.k.a migration)" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)
      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(mockOrgRegistrationDetails))
      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(None, Company, atarService)(mockRequest))

      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession

      verify(mockRequestSessionData)
        .storeUserSubscriptionFlow(OrganisationFlow, UserLocationPage.url(atarService))(mockRequest)
    }
  }
}

class SubscriptionFlowManagerNinoUtrEnabledSpec
    extends UnitSpec with MockitoSugar with BeforeAndAfterAll with BeforeAndAfterEach with ControllerSpec {

  private val mockRequestSessionData   = mock[RequestSessionData]
  private val mockCdsFrontendDataCache = mock[SessionCache]

  val controller =
    new SubscriptionFlowManager(mockRequestSessionData, mockCdsFrontendDataCache)(global)

  private val mockSession = mock[Session]

  private val mockRequest = mock[Request[AnyContent]]

  override def beforeEach(): Unit = {
    reset(mockRequestSessionData)
    reset(mockSession)
    reset(mockCdsFrontendDataCache)
    when(mockRequestSessionData.storeUserSubscriptionFlow(any[SubscriptionFlow], any[String])(any[Request[AnyContent]]))
      .thenReturn(mockSession)
    when(mockCdsFrontendDataCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[Request[_]]))
      .thenReturn(Future.successful(true))
  }

  "First Page" should {

    "start Corporate Subscription Flow when selected organisation type is Sole Trader" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest))
        .thenReturn(Some(CdsOrganisationType.SoleTrader))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(RegistrationDetailsIndividual()))

      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(None, SoleTrader, atarService)(mockRequest))
      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession
      verify(mockRequestSessionData).storeUserSubscriptionFlow(RowIndividualFlow, UserLocationPage.url(atarService))(
        mockRequest
      )
    }

    "start Corporate Subscription Flow when selected organisation type is Individual" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest))
        .thenReturn(Some(CdsOrganisationType.Individual))
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(RegistrationDetailsIndividual()))

      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(None, Individual, atarService)(mockRequest))
      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession
      verify(mockRequestSessionData).storeUserSubscriptionFlow(RowIndividualFlow, UserLocationPage.url(atarService))(
        mockRequest
      )
    }

    "start Corporate Subscription Flow when cached registration details are for an Organisation" in {
      when(mockRequestSessionData.userSelectedOrganisationType(mockRequest)).thenReturn(None)
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some(UserLocation.Eu))
      when(mockCdsFrontendDataCache.registrationDetails(mockRequest))
        .thenReturn(Future.successful(RegistrationDetailsOrganisation()))

      val (subscriptionPage, session) =
        await(controller.startSubscriptionFlow(None, Company, atarService)(mockRequest))
      subscriptionPage.isInstanceOf[SubscriptionPage] shouldBe true
      session shouldBe mockSession
      verify(mockRequestSessionData).storeUserSubscriptionFlow(RowOrganisationFlow, UserLocationPage.url(atarService))(
        mockRequest
      )
    }
  }
}
