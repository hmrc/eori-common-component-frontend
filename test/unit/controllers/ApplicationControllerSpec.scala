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

package unit.controllers

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.ApplicationController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EnrolmentResponse, KeyValue}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service.{ATaR, CDS}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{accessibility_statement, start}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApplicationControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockAuthAction    = authAction(mockAuthConnector)
  private val mockSessionCache  = mock[SessionCache]

  private val startView                  = instanceOf[start]
  private val accessibilityStatementView = instanceOf[accessibility_statement]
  private val enrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  val controller = new ApplicationController(
    mockAuthAction,
    mcc,
    startView,
    accessibilityStatementView,
    mockSessionCache,
    enrolmentStoreProxyService,
    appConfig
  )

  override def beforeEach: Unit = {
    super.beforeEach()
    reset(mockAuthConnector, enrolmentStoreProxyService, mockSessionCache)
  }

  private def groupEnrolment(service: Service) = Some(
    EnrolmentResponse(service.enrolmentKey, "Activated", List(KeyValue("EORINumber", "GB123456463324")))
  )

  "Navigating to start" should {

    "allow unauthenticated users to access the start page" in {

      val result = controller.start.apply(SessionBuilder.buildRequestWithSessionNoUser)

      status(result) shouldBe OK
      CdsPage(contentAsString(result)).title should startWith("Get an EORI number")
    }

    "direct authenticated users to start subscription" in {
      when(enrolmentStoreProxyService.enrolmentForGroup(any(), any())(any()))
        .thenReturn(Future.successful(None))

      withAuthorisedUser(defaultUserId, mockAuthConnector)
      val result =
        controller.startSubscription(Service.ATaR).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("are-you-based-in-uk")
    }

    "direct authenticated users with CDS enrolment to start short-cut subscription" in {
      when(enrolmentStoreProxyService.enrolmentForGroup(any(), ArgumentMatchers.eq(ATaR))(any()))
        .thenReturn(Future.successful(None))

      val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB134123")
      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(cdsEnrolment))

      val result =
        controller.startSubscription(Service.ATaR).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
      verifyZeroInteractions(mockSessionCache)
    }

    "direct authenticated users where group id has CDS enrolment to start short-cut subscription" in {
      when(enrolmentStoreProxyService.enrolmentForGroup(any(), ArgumentMatchers.eq(ATaR))(any()))
        .thenReturn(Future.successful(None))
      when(enrolmentStoreProxyService.enrolmentForGroup(any(), ArgumentMatchers.eq(CDS))(any()))
        .thenReturn(Future.successful(groupEnrolment(CDS)))
      when(mockSessionCache.saveGroupEnrolment(any[EnrolmentResponse])(any())).thenReturn(Future.successful(true))

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty)

      val result =
        controller.startSubscription(Service.ATaR).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
      verify(mockSessionCache).saveGroupEnrolment(any[EnrolmentResponse])(any())
    }

    "inform authenticated users with ATAR enrolment that subscription exists" in {
      val atarEnrolment = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB134123")

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(atarEnrolment))

      val result =
        controller.startSubscription(Service.ATaR).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("enrolment-already-exists")
    }

    "inform authenticated users where group Id has an enrolment that subscription exists" in {

      when(enrolmentStoreProxyService.enrolmentForGroup(any(), ArgumentMatchers.eq(ATaR))(any()))
        .thenReturn(Future.successful(groupEnrolment(CDS)))

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty)

      val result =
        controller.startSubscription(Service.ATaR).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("enrolment-already-exists-for-group")
    }
  }

  "Navigating to logout" should {
    "logout an authenticated user for register" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))

      val result =
        controller.logout(Service.ATaR, Journey.Register).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      session(result).get(SessionKeys.userId) shouldBe None
      await(result).header.headers("Location") should endWith("feedback/CDS")
    }

    "logout an authenticated user for subscribe" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))

      val result =
        controller.logout(Service.ATaR, Journey.Subscribe).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      session(result).get(SessionKeys.userId) shouldBe None
      await(result).header.headers("Location") should endWith("feedback/get-access-cds")
    }
  }

  "Navigating to keepAlive" should {

    "return a status of OK" in {

      val result = controller.keepAlive(Journey.Register).apply(SessionBuilder.buildRequestWithSessionNoUser)

      status(result) shouldBe OK
    }
  }
}
