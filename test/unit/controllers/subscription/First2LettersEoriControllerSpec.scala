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
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.First2LettersEoriController
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm.EoriRegion.{EU, GB}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.first_2_letters_eori_number
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class First2LettersEoriControllerSpec extends ControllerSpec with AuthActionMock with BeforeAndAfterEach {

  private val pageLevelErrorSummaryListXPath  = "//ul[@class='govuk-list govuk-error-summary__list']"
  private val mockAuthConnector               = mock[AuthConnector]
  private val mockAuthAction                  = authAction(mockAuthConnector)
  private val mockSessionCache                = mock[SessionCache]
  private val mockFirst2LettersEoriNumberPage = instanceOf[first_2_letters_eori_number]

  private val controller =
    new First2LettersEoriController(mockAuthAction, mcc, mockSessionCache, mockFirst2LettersEoriNumberPage)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    withAuthorisedUser(defaultUserId, mockAuthConnector)
  }

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector)
    reset(mockSessionCache)

    super.afterEach()
  }

  "First 2 letters EORI number controller" should {
    "Return 400 Bad Request when no option is selected" in {
      // Given When
      when(mockSessionCache.getFirst2LettersEori(any())).thenReturn(Future.successful(None))

      val result: Future[Result] = controller.submit(cdsService, false)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("region" -> ""))
      )

      // Then
      status(result) shouldBe BAD_REQUEST
      CdsPage(contentAsString(result))
        .getElementsText(pageLevelErrorSummaryListXPath) shouldBe "Select the first two letters of your EORI number"
    }

    "Display the 'What are the first two letters of your EORI number?' page" in {
      // Given When
      when(mockSessionCache.getFirst2LettersEori(any())).thenReturn(Future.successful(None))

      val result = controller.form(cdsService)(SessionBuilder.buildRequestWithSession(defaultUserId))

      // Then
      status(result) shouldBe OK
      CdsPage(contentAsString(result)).title() should startWith("What are the first two letters of your EORI number?")
    }

    "Redirect to eori page when GB is selected" in {
      // Given When
      when(mockSessionCache.saveFirst2LettersEori(any())(any())).thenReturn(Future.successful(GB))

      val result = controller.submit(cdsService, false)(
        SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("region" -> "GB"))
      )

      // Then
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result).value should endWith("eori")
    }

    "Redirect to check eori number page when EU is selected" in {
      // Given When
      when(mockSessionCache.saveFirst2LettersEori(any())(any())).thenReturn(Future.successful(EU))

      val result =
        controller.submit(cdsService, false)(
          SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, Map("region" -> "EU"))
        )

      // Then
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result).value should endWith("check-eori-number")
    }
  }

}
