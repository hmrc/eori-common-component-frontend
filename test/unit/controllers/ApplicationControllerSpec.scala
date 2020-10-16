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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.MissingGroupId
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.GroupEnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EnrolmentResponse, KeyValue}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
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
  private val groupEnrolmentExtractor    = mock[GroupEnrolmentExtractor]

  val controller = new ApplicationController(
    mockAuthAction,
    mcc,
    startView,
    accessibilityStatementView,
    mockSessionCache,
    groupEnrolmentExtractor,
    appConfig
  )

  override def beforeEach: Unit = {
    super.beforeEach()
    reset(mockAuthConnector, groupEnrolmentExtractor, mockSessionCache)

    when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), any())(any()))
      .thenReturn(Future.successful(None))
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any()))
      .thenReturn(Future.successful(false))
  }

  private def groupEnrolment(service: Service) = Some(
    EnrolmentResponse(service.enrolmentKey, "Activated", List(KeyValue("EORINumber", "GB123456463324")))
  )

  "Navigating to start" should {

    "allow unauthenticated users to access the start page" in {

      val result = controller.start(atarService).apply(SessionBuilder.buildRequestWithSessionNoUser)

      status(result) shouldBe OK
      CdsPage(contentAsString(result)).title should startWith("Get an EORI number")
    }

    "direct authenticated users to start subscription" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("are-you-based-in-uk")
    }

    "direct authenticated users with CDS enrolment to start short-cut subscription" in {
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
        .thenReturn(Future.successful(None))

      val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB134123")
      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(cdsEnrolment))

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
      verifyZeroInteractions(mockSessionCache)
    }

    "direct authenticated users where group id has CDS enrolment to start short-cut subscription" in {
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
        .thenReturn(Future.successful(None))
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(Service.cds))(any()))
        .thenReturn(Future.successful(groupEnrolment(Service.cds)))
      when(mockSessionCache.saveGroupEnrolment(any[EnrolmentResponse])(any())).thenReturn(Future.successful(true))

      withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set.empty)

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe SEE_OTHER
      await(result).header.headers("Location") should endWith("check-existing-eori")
      verify(mockSessionCache).saveGroupEnrolment(any[EnrolmentResponse])(any())
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

  "Navigating to logout" should {
    "logout an authenticated user for register" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))

      val result =
        controller.logout(atarService, Journey.Register).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      session(result).get(SessionKeys.userId) shouldBe None
      await(result).header.headers("Location") should endWith("feedback/eori-common-component-register-atar")
    }

    "logout an authenticated user for subscribe" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))

      val result =
        controller.logout(atarService, Journey.Subscribe).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      session(result).get(SessionKeys.userId) shouldBe None
      await(result).header.headers("Location") should endWith("feedback/eori-common-component-subscribe-atar")
    }
  }

  "Navigating to keepAlive" should {

    "return a status of OK" in {

      val result =
        controller.keepAlive(atarService, Journey.Register).apply(SessionBuilder.buildRequestWithSessionNoUser)

      status(result) shouldBe OK
    }
  }
}
