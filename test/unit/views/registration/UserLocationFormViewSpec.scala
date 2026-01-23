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

package unit.views.registration

import common.pages.registration.UserLocationPageOrganisation
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.UserLocationController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.UserLocationDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.user_location
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserLocationFormViewSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]
  private val mockSessionCache               = mock[SessionCache]
  private val mockRegistrationDetailsService = mock[RegistrationDetailsService]

  private val userLocationView = instanceOf[user_location]

  private val controller = new UserLocationController(
    mockAuthAction,
    mockRequestSessionData,
    mockRegistrationDetailsService,
    mockSessionCache,
    mcc,
    userLocationView
  )

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(
      mockEnrolmentStoreProxyService
        .enrolmentForGroup(any(), any())(any())
    ).thenReturn(Future.successful(None))
  }

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector)

    super.afterEach()
  }

  "User location page" should {

    val expectedTitleOrganisation = "Where is your organisation established?"
    when(mockSessionCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
    s"display title as '$expectedTitleOrganisation for entity with type organisation" in {
      showMigrationForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(expectedTitleOrganisation)
      }
    }

    val expectedTitleIndividual = "Where are you based?"
    when(mockSessionCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
    s"display title as '$expectedTitleIndividual for entity with type individual" in {
      showMigrationForm(affinityGroup = AffinityGroup.Individual) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith(expectedTitleIndividual)
      }
    }

    "submit result when user chooses to continue" in {
      when(mockSessionCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
      showMigrationForm() { result =>
        val page = CdsPage(contentAsString(result))
        page
          .formAction(
            "user-location-form"
          ) shouldBe uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.UserLocationController
          .submit(atarService)
          .url
      }
    }

    "display correct location on migration journey" in {
      when(mockSessionCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
      showMigrationForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.elementIsPresent(UserLocationPageOrganisation.locationUkField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationUkField) should be("uk")
        page.elementIsPresent(UserLocationPageOrganisation.locationIslandsField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationIslandsField) should be("islands")
        page.elementIsPresent(UserLocationPageOrganisation.locationEuField) should be(false)
        page.elementIsPresent(UserLocationPageOrganisation.locationThirdCountryField) should be(false)
        page.elementIsPresent(UserLocationPageOrganisation.locationThirdCountryIncEuField) should be(true)
        page.getElementValue(UserLocationPageOrganisation.locationThirdCountryIncEuField) should be(
          "third-country-inc-eu"
        )
      }
    }
  }

  private def showMigrationForm(
    userId: String = defaultUserId,
    affinityGroup: AffinityGroup = AffinityGroup.Organisation
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector, userAffinityGroup = affinityGroup)

    val result = controller
      .form(atarService)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

}
