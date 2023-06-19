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

package unit.controllers.registration

import common.pages.EuOrgOrIndividualPage
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request, Result, Session}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.OrganisationTypeController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, RequestSessionDataKeys}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.organisation_type
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder._
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO Move view spec to separate test and keep here only controller logic test
class OrganisationTypeControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockSubscriptionFlowManager    = mock[SubscriptionFlowManager]
  private val mockRegistrationDetailsService = mock[RegistrationDetailsService]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]

  private val organisationTypeView = instanceOf[organisation_type]

  private val organisationTypeController = new OrganisationTypeController(
    mockAuthAction,
    mockSubscriptionFlowManager,
    mockRequestSessionData,
    mcc,
    organisationTypeView,
    mockRegistrationDetailsService,
    mockSubscriptionDetailsService
  )

  private val ProblemWithSelectionError = "Select what you want to apply as"
  private val companyXpath              = "//*[@id='organisation-type-company']"
  private val soleTraderXpath           = "//*[@id='organisation-type-sole-trader']"
  private val individualXpath           = "//*[@id='organisation-type-individual']"

  override protected def beforeEach(): Unit = {
    reset(mockRequestSessionData)
    reset(mockRegistrationDetailsService)
    reset(mockSubscriptionDetailsService)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
    when(
      mockRegistrationDetailsService
        .initialiseCacheWithRegistrationDetails(any[CdsOrganisationType]())(any[Request[AnyContent]]())
    ).thenReturn(Future.successful(true))
    when(mockSubscriptionDetailsService.cachedOrganisationType(any())).thenReturn(Future.successful(None))
  }

  "Displaying the form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, organisationTypeController.form(atarService))

    s"show correct options when user has selected location of ${UserLocation.Uk}" in {
      showFormWithAuthenticatedUser(userLocation = Some(UserLocation.Uk)) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.elementIsPresent(companyXpath) shouldBe true
        page.elementIsPresent(soleTraderXpath) shouldBe true
        page.elementIsPresent(individualXpath) shouldBe true
      }
    }

    "redirect status code when user has not selected a location" in {
      showFormWithAuthenticatedUser(userLocation = None) { result =>
        status(result) shouldBe OK
      }
    }

    "throw IllegalArgumentException when session cache holds invalid Organisation type" in {
      showFormWithAuthenticatedUser(userLocation = None) { result =>
        status(result) shouldBe OK
      }
    }

  }

  "Submitting the form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      organisationTypeController.submit(atarService)
    )

    "ensure an organisation type has been selected" in {
      submitForm(Map.empty) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(EuOrgOrIndividualPage.pageLevelErrorSummaryListXPath) shouldBe ProblemWithSelectionError
        page.getElementsText(
          EuOrgOrIndividualPage.fieldLevelErrorOrganisationType
        ) shouldBe s"Error: $ProblemWithSelectionError"
      }
    }

    val urlParameters =
      Table[CdsOrganisationType, String](
        ("option", "urlParameter"),
        (CdsOrganisationType.Company, "company"),
        (CdsOrganisationType.SoleTrader, "name-date-of-birth/sole-trader"),
        (CdsOrganisationType.Individual, "name-date-of-birth/individual"),
        (CdsOrganisationType.ThirdCountryOrganisation, "name/third-country-organisation"),
        (CdsOrganisationType.ThirdCountrySoleTrader, "row-name-date-of-birth/third-country-sole-trader"),
        (CdsOrganisationType.ThirdCountryIndividual, "row-name-date-of-birth/third-country-individual")
      )

    val subscriptionPage: Map[CdsOrganisationType, SubscriptionPage] = Map(
      (CdsOrganisationType.Company, NameUtrDetailsSubscriptionFlowPage),
      (CdsOrganisationType.SoleTrader, NameDobDetailsSubscriptionFlowPage),
      (CdsOrganisationType.Individual, NameDobDetailsSubscriptionFlowPage),
      (CdsOrganisationType.ThirdCountryOrganisation, NameDetailsSubscriptionFlowPage),
      (CdsOrganisationType.ThirdCountrySoleTrader, NameDobDetailsSubscriptionFlowPage),
      (CdsOrganisationType.ThirdCountryIndividual, NameDobDetailsSubscriptionFlowPage)
    )

    forAll(urlParameters) { (cdsOrganisationType, _) =>
      val option: String = cdsOrganisationType.id
      val page           = subscriptionPage(cdsOrganisationType)

      s"return a redirect to the matching form for the correct organisation type when '$option' is selected and user Journey type is Subscribe " in {
        val updatedMockSession =
          Session(Map()) + (RequestSessionDataKeys.selectedOrganisationType -> CdsOrganisationType.CompanyId)
        when(mockRequestSessionData.sessionWithOrganisationTypeAdded(any(), any())).thenReturn(updatedMockSession)

        when(
          mockSubscriptionFlowManager
            .startSubscriptionFlow(any(), any(), any())(any())
        ).thenReturn(Future.successful((page, updatedMockSession)))

        submitForm(Map("organisation-type" -> option)) { result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) shouldBe page.url(atarService)
        }
      }

      s"store the correct organisation type when '$option' is selected for Subscription Journey" in {
        val updatedMockSession =
          Session(Map()) + (RequestSessionDataKeys.selectedOrganisationType -> cdsOrganisationType.id)
        when(
          mockRequestSessionData
            .sessionWithOrganisationTypeAdded(ArgumentMatchers.any[Session], ArgumentMatchers.any[CdsOrganisationType])
        ).thenReturn(updatedMockSession)
        when(
          mockSubscriptionFlowManager
            .startSubscriptionFlow(any(), any(), any())(any[Request[AnyContent]]())
        ).thenReturn(Future.successful((page, updatedMockSession)))

        submitForm(Map("organisation-type" -> option)) { result =>
          await(result) //this is needed to ensure the future is completed before the verify is called
          verify(mockRequestSessionData)
            .sessionWithOrganisationTypeAdded(ArgumentMatchers.any[Session], ArgumentMatchers.any[CdsOrganisationType])
        }
      }
    }
  }

  def showFormWithAuthenticatedUser(
    userId: String = defaultUserId,
    userLocation: Option[String] = Some(UserLocation.Uk)
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(userLocation)

    test(organisationTypeController.form(atarService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  def showFormWithUnauthenticatedUser(test: Future[Result] => Any): Unit = {
    withNotLoggedInUser(mockAuthConnector)

    test(organisationTypeController.form(atarService).apply(SessionBuilder.buildRequestWithSessionNoUser))
  }

  def submitForm(
    form: Map[String, String],
    userId: String = defaultUserId,
    userLocation: Option[String] = Some(UserLocation.Uk)
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(userLocation)

    test(
      organisationTypeController.submit(atarService).apply(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

}
