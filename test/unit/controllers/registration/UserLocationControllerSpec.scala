/*
 * Copyright 2021 HM Revenue & Customs
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

package unit.controllers.registration

import java.util.UUID

import common.pages.registration.UserLocationPageOrganisation._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, mock => _}
import play.api.mvc.{AnyContent, Request, Result, Session}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.UserLocationController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.user_location
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.Future

class UserLocationControllerSpec extends ControllerSpec with MockitoSugar with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockAuthAction    = authAction(mockAuthConnector)

  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  private val userLocationView = instanceOf[user_location]

  private val controller = new UserLocationController(mockAuthAction, mockRequestSessionData, mcc, userLocationView)

  private val ProblemWithSelectionError = "Select where you are based"

  private val locationFieldName = "location"

  override def beforeEach(): Unit = {
    super.beforeEach()

    when(mockRequestSessionData.sessionWithOrganisationTypeAdded(any(), any()))
      .thenReturn(Session())
    when(
      mockRequestSessionData
        .sessionWithUserLocationAdded(any[String])(any[Request[AnyContent]])
    ).thenReturn(Session())
    when(
      mockEnrolmentStoreProxyService
        .enrolmentForGroup(any(), any())(any())
    ).thenReturn(Future.successful(None))
  }

  override protected def afterEach(): Unit = {
    reset(mockRequestSessionData)

    super.afterEach()
  }

  "Viewing the user location form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.form(atarService))
    "display the form with no errors" in {
      showForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
      }
    }
  }

  "Submitting the form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.submit(atarService))

    "ensure a location option has been selected" in {
      submitForm(Map.empty) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe ProblemWithSelectionError
        page.getElementsText(fieldLevelErrorLocation) shouldBe s"Error: $ProblemWithSelectionError"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    "ensure a valid answer option has been selected" in {
      val invalidOption = UUID.randomUUID.toString
      submitForm(Map(locationFieldName -> invalidOption)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe ProblemWithSelectionError
        page.getElementsText(fieldLevelErrorLocation) shouldBe s"Error: $ProblemWithSelectionError"
        page.getElementsText("title") should startWith("Error: ")
      }
    }

    assertCorrectSessionDataAndRedirect(UserLocation.Uk)

    assertCorrectSessionDataAndRedirect(UserLocation.Eu)

    assertCorrectSessionDataAndRedirect(UserLocation.ThirdCountry)

    assertCorrectSessionDataAndRedirect(UserLocation.Islands)
  }

  private def showForm(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.form(atarService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def submitForm(form: Map[String, String], userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.submit(atarService).apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)))
  }

  private def assertCorrectSessionDataAndRedirect(selectedOptionValue: String): Unit =
    s"store the correct organisation type when '$selectedOptionValue' is selected" in {
      val selectedOptionToJourney = selectedOptionValue match {
        case UserLocation.Eu           => "eu"
        case UserLocation.ThirdCountry => "third-country"
        case UserLocation.Uk           => "uk"
        case UserLocation.Iom          => "iom"
        case UserLocation.Islands      => "islands"
      }

      submitForm(Map(locationFieldName -> selectedOptionValue)) { result =>
        status(result)
        verify(mockRequestSessionData).sessionWithUserLocationAdded(ArgumentMatchers.eq(selectedOptionToJourney))(
          any[Request[AnyContent]]
        )
      }
    }

}
