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
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.EoriNumberSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, RequestSessionDataKeys}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.organisation_type
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder._
import util.builders.OrganisationTypeBuilder.mandatoryMap
import util.builders.SessionBuilder

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class OrganisationTypeControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockSubscriptionFlowManager    = mock[SubscriptionFlowManager]
  private val mockRegistrationDetailsService = mock[RegistrationDetailsService]

  private val organisationTypeView = app.injector.instanceOf[organisation_type]

  private val organisationTypeController = new OrganisationTypeController(
    app,
    mockAuthConnector,
    mockSubscriptionFlowManager,
    mockRequestSessionData,
    mcc,
    organisationTypeView,
    mockRegistrationDetailsService
  )

  private val ProblemWithSelectionError     = "Tell us what you want to apply as"
  private val thirdCountryOrganisationXpath = "//*[@id='organisation-type-third-country-organisation']"
  private val thirdCountrySoleTraderXpath   = "//*[@id='organisation-type-third-country-sole-trader']"
  private val thirdCountryIndividualXpath   = "//*[@id='organisation-type-third-country-individual']"
  private val companyXpath                  = "//*[@id='organisation-type-company']"
  private val soleTraderXpath               = "//*[@id='organisation-type-sole-trader']"
  private val individualXpath               = "//*[@id='organisation-type-individual']"

  override protected def beforeEach(): Unit = {
    reset(mockRequestSessionData)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(None)
    when(
      mockRegistrationDetailsService
        .initialiseCacheWithRegistrationDetails(any[CdsOrganisationType]())(any[HeaderCarrier]())
    ).thenReturn(Future.successful(true))
  }

  "Displaying the form" should {

    val userLocations =
      Table("userLocation", UserLocation.Uk, UserLocation.Eu, UserLocation.ThirdCountry)

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      organisationTypeController.form(Service.ATaR, Journey.Register)
    )

    forAll(userLocations) { userLocation =>
      s"show correct options when user has selected location of $userLocation" in {
        showFormWithAuthenticatedUser(userLocation = Some(userLocation)) { result =>
          status(result) shouldBe OK
          val includeUk           = userLocation == UserLocation.Uk
          val includeEu           = userLocation == UserLocation.Eu
          val includeThirdCountry = userLocation == UserLocation.ThirdCountry
          val page                = CdsPage(bodyOf(result))
          page.elementIsPresent(companyXpath) shouldBe includeUk
          page.elementIsPresent(soleTraderXpath) shouldBe includeUk
          page.elementIsPresent(individualXpath) shouldBe includeUk
          page.elementIsPresent(EuOrgOrIndividualPage.organisationXpath) shouldBe includeEu
          page.elementIsPresent(EuOrgOrIndividualPage.individualXpath) shouldBe includeEu
          page.elementIsPresent(thirdCountryOrganisationXpath) shouldBe includeThirdCountry
          page.elementIsPresent(thirdCountrySoleTraderXpath) shouldBe includeThirdCountry
          page.elementIsPresent(thirdCountryIndividualXpath) shouldBe includeThirdCountry
        }
      }
    }

    "redirect status code when user has not selected a location" in {
      showFormWithAuthenticatedUser(userLocation = None) { result =>
        status(result) shouldBe OK
      }
    }

  }

  "Submitting the form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
      mockAuthConnector,
      organisationTypeController.submit(Service.ATaR, Journey.Register)
    )

    "ensure an organisation type has been selected" in {
      submitForm(mandatoryMap.filterKeys(_ != "organisation-type"), journey = Journey.Register) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(EuOrgOrIndividualPage.pageLevelErrorSummaryListXPath) shouldBe ProblemWithSelectionError
        page.getElementsText(EuOrgOrIndividualPage.fieldLevelErrorOrganisationType) shouldBe ProblemWithSelectionError
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

    forAll(urlParameters) { (cdsOrganisationType, urlParameter) =>
      val option: String = cdsOrganisationType.id

      s"return a redirect to the matching form for the correct organisation type when '$option' is selected" in {
        submitForm(
          Map("organisation-type" -> option),
          organisationType = Some(cdsOrganisationType),
          journey = Journey.Register
        ) { result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers(LOCATION) should endWith(
            s"/customs-enrolment-services/atar/register/matching/$urlParameter"
          )
        }
      }

      s"return a redirect to the matching form for the correct organisation type when '$option' is selected and user Journey type is Subscribe " in {
        val updatedMockSession =
          Session(Map()) + (RequestSessionDataKeys.selectedOrganisationType -> CdsOrganisationType.CompanyId)
        when(mockRequestSessionData.sessionWithOrganisationTypeAdded(any(), any())).thenReturn(updatedMockSession)

        when(
          mockSubscriptionFlowManager
            .startSubscriptionFlow(any[Service], any[Journey.Value])(any[HeaderCarrier](), any[Request[AnyContent]]())
        ).thenReturn(Future.successful((EoriNumberSubscriptionFlowPage, updatedMockSession)))

        submitForm(
          Map("organisation-type" -> option),
          organisationType = Some(cdsOrganisationType),
          journey = Journey.Subscribe
        ) { result =>
          status(result) shouldBe SEE_OTHER
          result.header.headers(
            LOCATION
          ) shouldBe "/customs-enrolment-services/atar/subscribe/matching/what-is-your-eori"
        }
      }

      s"store the correct organisation type when '$option' is selected" in {
        submitForm(
          mandatoryMap + ("organisation-type" -> option),
          organisationType = Some(cdsOrganisationType),
          journey = Journey.Register
        ) { result =>
          await(result) //this is needed to ensure the future is completed before the verify is called
          verify(mockRequestSessionData).sessionWithOrganisationTypeAdded(ArgumentMatchers.eq(cdsOrganisationType))(
            any[Request[AnyContent]]
          )
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
            .startSubscriptionFlow(any[Service], any[Journey.Value])(any[HeaderCarrier](), any[Request[AnyContent]]())
        ).thenReturn(Future.successful((EoriNumberSubscriptionFlowPage, updatedMockSession)))

        submitForm(
          Map("organisation-type" -> option),
          organisationType = Some(cdsOrganisationType),
          journey = Journey.Subscribe
        ) { result =>
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
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(userLocation)

    test(
      organisationTypeController.form(Service.ATaR, Journey.Register).apply(
        SessionBuilder.buildRequestWithSession(userId)
      )
    )
  }

  def showFormWithUnauthenticatedUser(test: Future[Result] => Any) {
    withNotLoggedInUser(mockAuthConnector)

    test(
      organisationTypeController.form(Service.ATaR, Journey.Register).apply(
        SessionBuilder.buildRequestWithSessionNoUser
      )
    )
  }

  def submitForm(
    form: Map[String, String],
    userId: String = defaultUserId,
    organisationType: Option[CdsOrganisationType] = None,
    userLocation: Option[String] = Some(UserLocation.Uk),
    journey: Journey.Value
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    organisationType foreach { o =>
      when(mockRequestSessionData.sessionWithOrganisationTypeAdded(ArgumentMatchers.eq(o))(any[Request[AnyContent]]))
        .thenReturn(Session())
    }
    when(mockRequestSessionData.selectedUserLocation(any[Request[AnyContent]])).thenReturn(userLocation)

    test(
      organisationTypeController
        .submit(Service.ATaR, journey)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

}
