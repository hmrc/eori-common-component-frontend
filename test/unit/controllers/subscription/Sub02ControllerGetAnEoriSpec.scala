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

package unit.controllers.subscription

import common.support.testdata.TestData
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result, Session}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.FeatureFlags
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.Sub02Controller
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.migration_success
import util.ControllerSpec
import util.builders.AuthBuilder._
import util.builders.{AuthActionMock, SessionBuilder}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class Sub02ControllerGetAnEoriSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockSessionCache               = mock[SessionCache]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockFeatureFlag                = mock[FeatureFlags]

  private val migrationSuccessView = instanceOf[migration_success]

  private val subscriptionController = new Sub02Controller(
    mockAuthAction,
    mockRequestSessionData,
    mockSessionCache,
    mockSubscriptionDetailsService,
    mcc,
    migrationSuccessView,
    mockFeatureFlag
  )(global)

  val eoriNumberResponse: String                = "EORI-Number"
  val formBundleIdResponse: String              = "Form-Bundle-Id"
  val emailVerificationTimestamp: LocalDateTime = TestData.emailVerificationTimestamp
  val emulatedFailure                           = new UnsupportedOperationException("Emulated service call failure.")

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(mockSubscriptionDetailsService.saveKeyIdentifiers(any[GroupId], any[InternalId], any[Service])(any(), any()))
      .thenReturn(Future.successful(()))
  }

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector, mockSessionCache)

    super.afterEach()
  }

  private def assertCleanedSession(result: Future[Result]): Unit = {
    val currentSession: Session = session(result)

    currentSession.data.get("selected-user-location") shouldBe None
    currentSession.data.get("subscription-flow") shouldBe None
    currentSession.data.get("selected-organisation-type") shouldBe None
    currentSession.data.get("uri-before-subscription-flow") shouldBe None
  }

  "calling migrationEnd on Sub02Controller" should {
    val sub02Outcome = Sub02Outcome("testDate", "testFullName", Some("EoriTest"))

    "render page with name for UK location" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("uk"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[AnyContent]]))
        .thenReturn(Future.successful(Some(Utr("someUtr"))))
      mockNameAndSub02OutcomeRetrieval
      when(mockSessionCache.sub02Outcome(any[Request[AnyContent]])).thenReturn(Future.successful(sub02Outcome))
      when(mockSessionCache.remove(any[Request[AnyContent]])).thenReturn(Future.successful(true))
      when(mockSessionCache.saveSub02Outcome(any[Sub02Outcome])(any[Request[AnyContent]])).thenReturn(
        Future.successful(true)
      )
      verify(mockSessionCache, never()).registerWithEoriAndIdResponse(any[Request[AnyContent]])
      invokeMigrationEnd { result =>
        assertCleanedSession(result)
        status(result) shouldBe OK
      }
    }

    "render page with name for ROW location when customsId exists" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("eu"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[AnyContent]]))
        .thenReturn(Future.successful(Some(Utr("someUtr"))))
      mockNameAndSub02OutcomeRetrieval
      when(mockSessionCache.sub02Outcome(any[Request[AnyContent]])).thenReturn(Future.successful(sub02Outcome))
      when(mockSessionCache.remove(any[Request[AnyContent]])).thenReturn(Future.successful(true))
      when(mockSessionCache.saveSub02Outcome(any[Sub02Outcome])(any[Request[AnyContent]])).thenReturn(
        Future.successful(true)
      )
      invokeMigrationEnd { result =>
        assertCleanedSession(result)
        status(result) shouldBe OK
      }
    }

    "render page with name for ROW location when no customsId exists" in {
      when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(Some("eu"))
      when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[AnyContent]])).thenReturn(Future.successful(None))
      when(mockSessionCache.sub02Outcome(any[Request[AnyContent]])).thenReturn(Future.successful(sub02Outcome))
      when(mockSessionCache.remove(any[Request[AnyContent]])).thenReturn(Future.successful(true))
      when(mockSessionCache.saveSub02Outcome(any[Sub02Outcome])(any[Request[AnyContent]])).thenReturn(
        Future.successful(true)
      )
      invokeMigrationEnd { result =>
        assertCleanedSession(result)
        status(result) shouldBe OK
      }
    }
  }

  private def invokeMigrationEnd(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(subscriptionController.migrationEnd(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def mockNameAndSub02OutcomeRetrieval = {
    val mrweair  = mock[RegisterWithEoriAndIdResponse]
    val mrweaird = mock[RegisterWithEoriAndIdResponseDetail]
    val rd       = mock[ResponseData]
    val trader   = mock[Trader]
    when(mockSessionCache.registerWithEoriAndIdResponse(any[Request[AnyContent]])).thenReturn(
      Future.successful(mrweair)
    )
    when(mrweair.responseDetail).thenReturn(Some(mrweaird))
    when(mrweaird.responseData).thenReturn(Some(rd))
    when(rd.trader).thenReturn(trader)
    when(trader.fullName).thenReturn("testName")
  }

}
