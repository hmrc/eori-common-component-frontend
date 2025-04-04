/*
 * Copyright 2025 HM Revenue & Customs
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

package unit.controllers.migration

import common.support.testdata.subscription.SubscriptionContactDetailsModelBuilder._
import common.support.testdata.subscription.{BusinessDatesOrganisationTypeTables, ReviewPageOrganisationTypeTables}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.CheckYourDetailsController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{
  RowOrganisationFlow,
  SubscriptionDetails,
  SubscriptionFlow
}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{AddressViewModel, CompanyRegisteredCountry}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.check_your_details
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.RegistrationDetailsBuilder.{existingOrganisationRegistrationDetails, individualRegistrationDetails}
import util.builders.SubscriptionContactDetailsFormBuilder.Email
import util.builders.{AuthActionMock, SessionBuilder}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CheckYourDetailsControllerSpec
    extends ControllerSpec with BusinessDatesOrganisationTypeTables with ReviewPageOrganisationTypeTables
    with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector      = mock[AuthConnector]
  private val mockAuthAction         = authAction(mockAuthConnector)
  private val mockCdsDataCache       = mock[SessionCache]
  private val mockRequestSessionData = mock[RequestSessionData]
  private val mockSubscriptionFlow   = mock[SubscriptionFlow]

  private val checkYourDetailsView = instanceOf[check_your_details]

  val controller =
    new CheckYourDetailsController(mockAuthAction, mockCdsDataCache, mcc, checkYourDetailsView, mockRequestSessionData)

  override def beforeEach(): Unit = {
    reset(mockCdsDataCache)
    reset(mockSubscriptionFlow)
    when(mockRequestSessionData.userSubscriptionFlow(any[Request[AnyContent]])).thenReturn(mockSubscriptionFlow)

    val subscriptionDetailsHolderForCompany = SubscriptionDetails(
      contactDetails = Some(contactUkDetailsModelWithMandatoryValuesOnly),
      nameDobDetails = Some(NameDobMatchModel("John", None, "Doe", LocalDate.parse("2003-04-08"))),
      idDetails = Some(IdMatchModel(id = "AB123456C")),
      eoriNumber = Some("SOMEEORINUMBER"),
      dateEstablished = Some(LocalDate.parse("2003-04-08")),
      nameIdOrganisationDetails = Some(NameIdOrganisationMatchModel(name = "Company UTR number", id = "UTRNUMBER")),
      addressDetails =
        Some(AddressViewModel(street = "street", city = "city", postcode = Some("postcode"), countryCode = "GB")),
      email = Some("john.doe@example.com"),
      registeredCompany = Some(CompanyRegisteredCountry("GB"))
    )
    when(mockCdsDataCache.email(any[Request[_]])).thenReturn(Future.successful(Email))

    when(mockCdsDataCache.subscriptionDetails(any[Request[_]])).thenReturn(
      Future.successful(subscriptionDetailsHolderForCompany)
    )
    when(mockCdsDataCache.registrationDetails(any[Request[_]])).thenReturn(
      Future.successful(individualRegistrationDetails)
    )
    when(mockCdsDataCache.addressLookupParams(any[Request[_]])).thenReturn(Future.successful(None))
  }

  "Reviewing the details" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewDetails(atarService))

    "return ok when data has been provided" in {
      when(mockCdsDataCache.registrationDetails(any[Request[_]])).thenReturn(
        Future.successful(existingOrganisationRegistrationDetails)
      )

      showForm() { result =>
        status(result) shouldBe OK
      }
    }

    "return ok when data has been provided for RowOrganisationFlow" in {
      when(mockCdsDataCache.registrationDetails(any[Request[_]])).thenReturn(
        Future.successful(existingOrganisationRegistrationDetails)
      )

      when(mockRequestSessionData.userSubscriptionFlow(any())).thenReturn(RowOrganisationFlow)

      showForm() { result =>
        status(result) shouldBe OK
      }
    }
  }

  def showForm(
    userSelectedOrgType: Option[CdsOrganisationType] = None,
    userId: String = defaultUserId,
    isIndividualSubscriptionFlow: Boolean = false
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(userSelectedOrgType)
    when(mockSubscriptionFlow.isIndividualFlow).thenReturn(isIndividualSubscriptionFlow)

    test(controller.reviewDetails(atarService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

}
