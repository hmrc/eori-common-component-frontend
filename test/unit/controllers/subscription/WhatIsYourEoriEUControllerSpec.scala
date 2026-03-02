/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.WhatIsYourEoriEUController
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.what_is_your_eori_eu
import util.ControllerSpec
import unit.controllers.CdsPage
import util.builders.{AuthActionMock, SessionBuilder}
import util.builders.AuthBuilder.withAuthorisedUser

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WhatIsYourEoriEUControllerSpec extends ControllerSpec with AuthActionMock with BeforeAndAfterEach {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockWhatIsYourEoriEUPage       = instanceOf[what_is_your_eori_eu]
  private val pageLevelErrorSummaryListXPath  = "//ul[@class='govuk-list govuk-error-summary__list']"

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    reset(mockSubscriptionDetailsService)
  }

  override protected def afterEach(): Unit = {
    reset(mockSubscriptionDetailsService)
    reset(mockAuthConnector)
    super.afterEach()
  }

  private val controller =
    new WhatIsYourEoriEUController(mockAuthAction, mcc, mockSubscriptionDetailsService, mockWhatIsYourEoriEUPage)

  "What Is Your Eori EU Controller" should {
    "Display the 'What is your EORI number' page" in {

      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val result = controller.form(cdsService)(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe OK
      CdsPage(contentAsString(result)).title() should startWith("What is your EORI number?")
    }

    "Allow a user to enter a valid EU EORI and redirect to next page" in {
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))
      when(mockSubscriptionDetailsService.cacheEoriNumber(any())(any())).thenReturn(Future.successful((): Unit))

      val result: Future[Result] = controller.submit(cdsService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("eori-number" -> "FR123456789012"))
      )

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe "https://www.gov.uk/check-eori-number"
    }

    "Display the correct error when nothing is entered" in {
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.submit(cdsService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("eori-number" -> ""))
      )

      status(result) shouldBe BAD_REQUEST
      CdsPage(contentAsString(result))
        .getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter your EORI number"
    }

    "Display the correct error when user enters EORI starting with GB" in {
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.submit(cdsService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("eori-number" -> "GB123456789012"))
      )

      status(result) shouldBe BAD_REQUEST
      CdsPage(contentAsString(result))
        .getElementsText(pageLevelErrorSummaryListXPath) shouldBe "EORI number must not start with GB or XI"
    }

    "Display the correct error when user enters EORI starting with XI" in {
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.submit(cdsService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("eori-number" -> "XI123456789012"))
      )

      status(result) shouldBe BAD_REQUEST
      CdsPage(contentAsString(result))
        .getElementsText(pageLevelErrorSummaryListXPath) shouldBe "EORI number must not start with GB or XI"
    }


    "Display the correct error when user enters an EORI that does not have two letters at the start" in {
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.submit(cdsService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("eori-number" -> "123456789012"))
      )

      status(result) shouldBe BAD_REQUEST
      CdsPage(contentAsString(result))
        .getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Enter an EORI number in the correct format"
    }

    "Display the correct error when user enters an EORI that is too short" in {
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.submit(cdsService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("eori-number" -> "FR"))
      )

      status(result) shouldBe BAD_REQUEST
      CdsPage(contentAsString(result))
        .getElementsText(pageLevelErrorSummaryListXPath) shouldBe "EORI number must be between 3 and 17 characters"
    }

    "Display the correct error when user enters an EORI that is too long" in {
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.submit(cdsService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("eori-number" -> "FR12345678901234567890"))
      )

      status(result) shouldBe BAD_REQUEST
      CdsPage(contentAsString(result))
        .getElementsText(pageLevelErrorSummaryListXPath) shouldBe "EORI number must be between 3 and 17 characters"
    }

    "Display the correct error when user enters an EORI that has invalid characters" in {
      when(mockSubscriptionDetailsService.cachedEoriNumber(any())).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.submit(cdsService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("eori-number" -> "FR1234567-89012"))
      )

      status(result) shouldBe BAD_REQUEST
      CdsPage(contentAsString(result))
        .getElementsText(pageLevelErrorSummaryListXPath) shouldBe "EORI number must only include letters a to z and numbers"
    }
  }
}
