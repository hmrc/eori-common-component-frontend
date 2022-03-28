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

package unit.controllers

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Request
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.GroupEnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{ApplicationController, MissingGroupId}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EnrolmentResponse, ExistingEori, KeyValue}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.start_subscribe
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApplicationControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockAuthAction    = authAction(mockAuthConnector)
  private val mockSessionCache  = mock[SessionCache]

  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  private val startSubscribeView      = instanceOf[start_subscribe]
  private val groupEnrolmentExtractor = mock[GroupEnrolmentExtractor]

  val controller = new ApplicationController(
    mockAuthAction,
    mcc,
    startSubscribeView,
    mockSessionCache,
    groupEnrolmentExtractor,
    mockEnrolmentStoreProxyService,
    appConfig
  )

  override protected def beforeEach(): Unit = {
    when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), any())(any()))
      .thenReturn(Future.successful(None))
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any()))
      .thenReturn(Future.successful(false))
    when(groupEnrolmentExtractor.checkAllServiceEnrolments(any())(any())).thenReturn(Future.successful(None))
  }

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector, groupEnrolmentExtractor, mockSessionCache, mockEnrolmentStoreProxyService)

    super.afterEach()
  }

  private def groupEnrolment(service: Service) = Some(
    EnrolmentResponse(service.enrolmentKey, "Activated", List(KeyValue("EORINumber", "GB123456463324")))
  )

  "Navigating to start" should {

    "direct authenticated users to start subscription" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)

      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(Future.successful(List.empty))
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(Future.successful(None))

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe OK
      CdsPage(contentAsString(result)).title should startWith("You must subscribe to use")
    }

    "direct authenticated users with CDS enrolment to start short-cut subscription" in {
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
        .thenReturn(Future.successful(None))
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(Future.successful(List.empty))
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(Future.successful(None))

      val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB134123")
      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(cdsEnrolment))

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
      verifyZeroInteractions(mockSessionCache)
    }

    "direct authenticated users to start short-cut subscription and pick other enrolment apart from CDS" in {
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(gvmsService))(any()))
        .thenReturn(Future.successful(None))
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(Future.successful(List.empty))
      when(groupEnrolmentExtractor.checkAllServiceEnrolments(any())(any())).thenReturn(
        Future.successful(groupEnrolment(atarService))
      )
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockSessionCache.saveGroupEnrolment(any[EnrolmentResponse])(any())).thenReturn(Future.successful(true))
      val atarEnrolment   = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB134123")
      val route1Enrolment = Enrolment("HMRC-CTS-ORG").withIdentifier("EORINumber", "GB134123")
      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(atarEnrolment, route1Enrolment))

      val result =
        controller.startSubscription(gvmsService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
    }

    "direct authenticated users where group id has CDS enrolment to start short-cut subscription" in {
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
        .thenReturn(Future.successful(None))
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(Service.cds))(any()))
        .thenReturn(Future.successful(groupEnrolment(Service.cds)))
      when(mockSessionCache.saveGroupEnrolment(any[EnrolmentResponse])(any())).thenReturn(Future.successful(true))
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(Future.successful(List.empty))
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(Future.successful(None))

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty)

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
      verify(mockSessionCache).saveGroupEnrolment(any[EnrolmentResponse])(any())
    }

    "direct authenticated users where group id has other than  enrolment to start short-cut subscription apart from CDS" in {
      when(groupEnrolmentExtractor.checkAllServiceEnrolments(any())(any()))
        .thenReturn(Future.successful(groupEnrolment(atarService)))
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(Service.cds))(any()))
        .thenReturn(Future.successful(None))
      when(mockSessionCache.saveGroupEnrolment(any[EnrolmentResponse])(any())).thenReturn(Future.successful(true))
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(Future.successful(List.empty))
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(Future.successful(None))

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty)

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
      verify(mockSessionCache).saveGroupEnrolment(
        meq(EnrolmentResponse("HMRC-ATAR-ORG", "Activated", List(KeyValue("EORINumber", "GB123456463324"))))
      )(any())
    }

    "inform authenticated users with ATAR enrolment that subscription exists" in {
      val atarEnrolment = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB134123")

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(atarEnrolment))

      val result =
        controller.startSubscription(atarService).apply(
          SessionBuilder.buildRequestWithSessionAndPath("/atar/subscribe", defaultUserId)
        )

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("enrolment-already-exists")
    }

    "inform authenticated users where group Id has an enrolment that subscription exists" in {

      when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
        .thenReturn(Future.successful(true))

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty)

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("enrolment-already-exists-for-group")
    }

    "throw missing group id exception when user doesn't have group id" in {

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty, groupId = None)

      intercept[MissingGroupId] {
        await(controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
      }
    }
  }

  "Navigating to unable to use service" should {

    "happen for authenticated users with CDS enrolment" when {

      "enrolment is in use" in {

        when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
          .thenReturn(Future.successful(None))
        when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(
          Future.successful(List(groupEnrolment(atarService).get))
        )
        when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(
          Future.successful(Some(ExistingEori("GB123456789123", "HMRC-GVMS-ORG")))
        )
        when(mockSessionCache.saveEori(any())(any())).thenReturn(Future.successful(true))

        val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB123456789123")
        withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(cdsEnrolment))

        val result =
          controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("unable-to-use-id")
        verify(mockSessionCache).saveEori(any())(any())
      }
    }

    "happen for authenticated users with groupId CDS enrolment" when {

      "enrolment is in use" in {

        when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
          .thenReturn(Future.successful(None))
        when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(Service.cds))(any()))
          .thenReturn(Future.successful(groupEnrolment(Service.cds)))
        when(mockSessionCache.saveGroupEnrolment(any[EnrolmentResponse])(any())).thenReturn(Future.successful(true))
        when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(
          Future.successful(List(groupEnrolment(atarService).get))
        )
        when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(
          Future.successful(Some(ExistingEori("GB123456789123", "HMRC-GVMS-ORG")))
        )
        when(mockSessionCache.saveEori(any())(any())).thenReturn(Future.successful(true))

        withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty)

        val result =
          controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("unable-to-use-id")
        verify(mockSessionCache).saveEori(any())(any())
      }
    }

    "happen to authenticated users with GVMS enrolment and without CDS enrolment and without groupId enrolment" when {

      "enrolment is in use" in {

        when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
          .thenReturn(Future.successful(None))
        when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(Service.cds))(any()))
          .thenReturn(Future.successful(None))
        when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(
          Future.successful(List(groupEnrolment(atarService).get))
        )
        when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(
          Future.successful(Some(ExistingEori("GB123456789123", "HMRC-GVMS-ORG")))
        )
        when(mockSessionCache.saveEori(any())(any())).thenReturn(Future.successful(true))

        withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty)

        val result =
          controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("unable-to-use-id")
        verify(mockSessionCache).saveEori(any())(any())
      }
    }
  }

  "Navigating to logout" should {

    "logout an authenticated user for subscribe" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSessionCache.remove(any[Request[_]])).thenReturn(Future.successful(true))

      val result =
        controller.logout(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      session(result).isEmpty shouldBe true
      await(result).header.headers("Location") should endWith("feedback/eori-common-component-subscribe-atar")
    }
  }

  "Navigating to keepAlive" should {

    "return a status of OK" in {

      when(mockSessionCache.keepAlive(any())).thenReturn(Future.successful(true))

      val result =
        controller.keepAlive(atarService).apply(SessionBuilder.buildRequestWithSessionNoUser)

      status(result) shouldBe OK

      verify(mockSessionCache).keepAlive(any())
    }
  }
}
