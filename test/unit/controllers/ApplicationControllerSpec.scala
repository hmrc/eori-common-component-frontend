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

package unit.controllers

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Request
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{Assistant, AuthConnector, Enrolment}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{ApplicationController, MissingGroupId}

import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.start_subscribe
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApplicationControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector           = mock[AuthConnector]
  private val mockAuthAction              = authAction(mockAuthConnector)
  private val mockSessionCache            = mock[SessionCache]
  private val mockEnrolmentJourneyService = mock[EnrolmentJourneyService]

  private val startSubscribeView = instanceOf[start_subscribe]

  val controller = new ApplicationController(
    mockAuthAction,
    mcc,
    startSubscribeView,
    mockSessionCache,
    mockEnrolmentJourneyService,
    appConfig
  )

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockSessionCache)
    reset(mockEnrolmentJourneyService)

    super.afterEach()
  }

  "Navigating to start" should {

    "direct authenticated users to start subscription" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockEnrolmentJourneyService.getJourney(any(), any(), any())(any(), any())).thenReturn(
        Future.successful(Right(LongJourney))
      )

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe OK
    }

    "direct authenticated users with CDS enrolment to start short-cut subscription" in {
      when(
        mockEnrolmentJourneyService.getJourney(any(), any(), ArgumentMatchers.eq(atarService))(any(), any())
      ).thenReturn(Future.successful(Right(AutoEnrolment)))

      val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB134123")
      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(cdsEnrolment))

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
      verifyNoMoreInteractions(mockSessionCache)
    }

    "inform authenticated users with ATAR enrolment that subscription exists" in {
      when(
        mockEnrolmentJourneyService.getJourney(any(), any(), ArgumentMatchers.eq(atarService))(any(), any())
      ).thenReturn(Future.successful(Left(EnrolmentExistsUser)))

      val atarEnrolment = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB134123")

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(atarEnrolment))

      val result =
        controller.startSubscription(atarService).apply(
          SessionBuilder.buildRequestWithSessionAndPath("/atar/subscribe", defaultUserId)
        )

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("enrolment-already-exists")
    }

    "inform authenticated Assistant users with ATAR enrolment that subscription exists" in {
      when(
        mockEnrolmentJourneyService.getJourney(any(), any(), ArgumentMatchers.eq(atarService))(any(), any())
      ).thenReturn(Future.successful(Left(EnrolmentExistsUser)))

      val atarEnrolment = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB134123")

      withAuthorisedUser(
        defaultUserId,
        mockAuthConnector,
        otherEnrolments = Set(atarEnrolment),
        userCredentialRole = Some(Assistant)
      )

      val result =
        controller.startSubscription(atarService).apply(
          SessionBuilder.buildRequestWithSessionAndPath("/atar/subscribe", defaultUserId)
        )

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("enrolment-already-exists")
    }

    "inform authenticated users where group Id has an enrolment that subscription exists" in {
      when(
        mockEnrolmentJourneyService.getJourney(any(), any(), ArgumentMatchers.eq(atarService))(any(), any())
      ).thenReturn(Future.successful(Left(EnrolmentExistsGroup)))

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
        when(
          mockEnrolmentJourneyService.getJourney(any(), any(), ArgumentMatchers.eq(atarService))(any(), any())
        ).thenReturn(Future.successful(Left(EnrolmentExistsUser)))

        when(mockSessionCache.saveEori(any())(any())).thenReturn(Future.successful(true))

        val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB123456789123")
        withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(cdsEnrolment))

        val result =
          controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("unable-to-use-id")
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
        controller.keepAlive.apply(SessionBuilder.buildRequestWithSessionNoUser)

      status(result) shouldBe OK

      verify(mockSessionCache).keepAlive(any())
    }
  }
}
