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

package unit.views.partials

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.ApplicationController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.GroupEnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.models.LongJourney
import uk.gov.hmrc.eoricommoncomponent.frontend.services.EnrolmentJourneyService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.start_subscribe
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, AuthBuilder, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HeaderSpec extends ControllerSpec with AuthActionMock with BeforeAndAfterEach {

  private val mockAuthConnector    = mock[AuthConnector]
  private val mockAuthAction       = authAction(mockAuthConnector)
  private val mockCdsFrontendCache = mock[SessionCache]

  private val mockEnrolmentJourneyService = mock[EnrolmentJourneyService]

  private val viewStartSubscribe      = instanceOf[start_subscribe]
  private val groupEnrolmentExtractor = mock[GroupEnrolmentExtractor]

  private val controller = new ApplicationController(
    mockAuthAction,
    mcc,
    viewStartSubscribe,
    mockCdsFrontendCache,
    mockEnrolmentJourneyService,
    appConfig
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any())).thenReturn(Future.successful(false))
    when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(Future.successful(List.empty))
    when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), any())(any())).thenReturn(Future.successful(None))
  }

  override protected def afterEach(): Unit = {
    reset(groupEnrolmentExtractor)

    super.afterEach()
  }

  "Header Sign in link" should {

    "be present when the user is logged in" in {
      AuthBuilder.withAuthorisedUser("user-1236213", mockAuthConnector)

      when(mockEnrolmentJourneyService.getJourney(any(), any(), any())(any(), any())).thenReturn(
        Future.successful(Right(LongJourney))
      )

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      val page = CdsPage(contentAsString(result))
      page.elementIsPresent("//a[@class='hmrc-sign-out-nav__link']") shouldBe true
    }

    "not be present when a user isn't logged in" in {
      AuthBuilder.withNotLoggedInUser(mockAuthConnector)

      when(mockEnrolmentJourneyService.getJourney(any(), any(), any())(any(), any())).thenReturn(
        Future.successful(Right(LongJourney))
      )

      val result = controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSessionNoUser)

      val page = CdsPage(contentAsString(result))
      page.elementIsPresent("//a[@class='hmrc-sign-out-nav__link']") shouldBe false
    }
  }

  "Feedback URL" should {
    "be present with service param equal to 'eori-common-component-subscribe''" in {
      val result = controller
        .startSubscription(atarService)
        .apply(FakeRequest("GET", "/customs-enrolment-services/atar/subscribe"))

      when(mockEnrolmentJourneyService.getJourney(any(), any(), any())(any(), any())).thenReturn(
        Future.successful(Right(LongJourney))
      )

      val page = CdsPage(contentAsString(result))

      page.getElementAttribute(
        "//span[@class='govuk-phase-banner__text']//a[@class='govuk-link']",
        "href"
      ) should endWith("/contact/beta-feedback?service=eori-common-component-subscribe-atar")
    }
  }

  "Language switch" should {

    "be always presented" in {

      AuthBuilder.withAuthorisedUser("user-1236213", mockAuthConnector)

      val result =
        controller.startSubscription(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      val page = CdsPage(contentAsString(result))
      page.elementIsPresent("//nav[@class='hmrc-language-select']") shouldBe true
    }
  }
}
