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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import uk.gov.hmrc.customs.rosmfrontend.controllers.ApplicationController
import uk.gov.hmrc.customs.rosmfrontend.models.{Journey, Service}
import uk.gov.hmrc.customs.rosmfrontend.services.cache.SessionCache
import uk.gov.hmrc.customs.rosmfrontend.views.html.migration.migration_start
import uk.gov.hmrc.customs.rosmfrontend.views.html.{accessibility_statement, start}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApplicationControllerSpec extends ControllerSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockSessionCache = mock[SessionCache]

  private val startView = app.injector.instanceOf[start]
  private val migrationStartView = app.injector.instanceOf[migration_start]
  private val accessibilityStatementView = app.injector.instanceOf[accessibility_statement]

  val controller = new ApplicationController(
    app,
    mockAuthConnector,
    mcc,
    startView,
    migrationStartView,
    accessibilityStatementView,
    mockSessionCache,
    appConfig
  )

  "Navigating to start" should {

    "allow unauthenticated users to access the start page" in {
      invokeStartFormWithUnauthenticatedUser() { result =>
        status(result) shouldBe OK
        CdsPage(bodyOf(result)).title should startWith("Get an EORI number")
      }
    }

    "direct authenticated users to start subscription" in {
      invokeStartFormSubscriptionWithAuthenticatedUser() { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("are-you-based-in-uk")
      }
    }

    "direct authenticated users with CDS enrolment to start short-cut subscription" in {
      val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB134123")
      invokeStartFormSubscriptionWithAuthenticatedUser(enrolment = Some(cdsEnrolment)) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("check-existing-eori")
      }
    }

    "inform authenticated users with ATAR enrolment that subscription exists" in {
      val atarEnrolment = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB134123")
      invokeStartFormSubscriptionWithAuthenticatedUser(enrolment = Some(atarEnrolment)) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("enrolment-already-exists")
      }
    }
  }

  "Navigating to logout" should {
    "logout an authenticated user for register" in {
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))

      invokeLogoutWithAuthenticatedUser(journey = Journey.Register) { result =>
        session(result).get(SessionKeys.userId) shouldBe None
        await(result).header.headers("Location") should endWith("feedback/CDS")
      }
    }

    "logout an authenticated user for subscribe" in {
      when(mockSessionCache.remove(any[HeaderCarrier])).thenReturn(Future.successful(true))

      invokeLogoutWithAuthenticatedUser(journey = Journey.Subscribe) { result =>
        session(result).get(SessionKeys.userId) shouldBe None
        await(result).header.headers("Location") should endWith("feedback/get-access-cds")
      }
    }
  }

  "Navigating to keepAlive" should {
    "return a status of OK" in {
      invokeKeepAliveWithUnauthenticatedUser() { result =>
        status(result) shouldBe OK
      }
    }
  }

  def invokeLogoutWithAuthenticatedUser(userId: String = defaultUserId, journey: Journey.Value)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(controller.logout(journey).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  def invokeStartFormWithUnauthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    test(controller.start.apply(SessionBuilder.buildRequestWithSessionNoUser))
  }

  def invokeStartFormSubscriptionWithAuthenticatedUser(userId: String = defaultUserId, enrolment: Option[Enrolment] = None)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector, otherEnrolments = enrolment.map(e => Set(e)).getOrElse(Set.empty))
    test(controller.startSubscription(Service.ATar).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  def invokeKeepAliveWithUnauthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    test(controller.keepAlive(Journey.Register).apply(SessionBuilder.buildRequestWithSessionNoUser))
  }
}
