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

import common.pages.matching.DoYouHaveNinoPage._
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.DoYouHaveNinoController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Individual
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{CdsOrganisationType, NameDobMatchModel, Nino, NinoMatchModel}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.haveRowIndividualsNinoForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.match_nino_row_individual
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.matching.NinoFormBuilder
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DoYouHaveNinoControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]

  private val matchNinoRowIndividualView = instanceOf[match_nino_row_individual]

  private val doYouHaveNinoController = new DoYouHaveNinoController(
    mockAuthAction,
    mockRequestSessionData,
    mcc,
    matchNinoRowIndividualView,
    mockSubscriptionDetailsService
  )

  override def beforeEach: Unit =
    when(mockSubscriptionDetailsService.cacheNinoMatch(any())(any[HeaderCarrier])).thenReturn(Future.successful(()))

  val validNino                           = Nino(NinoFormBuilder.Nino)
  val yesNinoSubmitData                   = Map("have-nino" -> "true")
  val noNinoSubmitData                    = Map("have-nino" -> "false")
  val mandatoryNinoFields: NinoMatchModel = haveRowIndividualsNinoForm.bind(yesNinoSubmitData).value.get

  "Viewing the NINO Individual/Sole trader Rest of World Matching form" should {

    "display the form" in {
      displayForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.getElementsText(pageLevelErrorSummaryListXPath) shouldBe empty
        page.getElementsText(fieldLevelErrorNino) shouldBe empty
      }
    }

    "ensure the labels are correct" in {
      displayForm() { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.getElementsText(yesLabel) shouldBe "Yes"
        page.elementIsPresent(yesRadioButton) shouldBe true

        page.getElementsText(noLabel) shouldBe "No"
        page.elementIsPresent(noRadioButton) shouldBe true

        page.getElementsText(fieldLevelErrorNino) shouldBe empty
      }
    }

  }

  "Submitting the form" should {
    "redirect to 'Get Nino' page when Y is selected" in {
      when(mockSubscriptionDetailsService.cachedNameDobDetails(any[HeaderCarrier])).thenReturn(
        Future.successful(Some(NameDobMatchModel("First name", None, "Last name", new LocalDate(2015, 10, 15))))
      )

      submitForm(yesNinoSubmitData) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("register/matching/row/get-nino")
        val expectedIndividual = Individual.withLocalDate("First name", None, "Last name", new LocalDate(2015, 10, 15))
      }
    }

    "redirect to 'Enter your address' page when N is selected" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(Some(CdsOrganisationType.ThirdCountrySoleTrader))

      submitForm(noNinoSubmitData) { result =>
        await(result)
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith("register/matching/address/third-country-sole-trader")
      }
    }

    "display error when form empty" in {
      submitForm(Map("have-nino" -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText("//*[@id='errors']") should include("Select yes if you have a National Insurance number")
      }
    }

    "throw exeption when N is selected and no org cached" in {
      when(mockRequestSessionData.userSelectedOrganisationType(any()))
        .thenReturn(None)

      intercept[IllegalStateException] {
        submitForm(form = noNinoSubmitData) { result =>
          status(result) shouldBe SEE_OTHER
        }
      }.getMessage should include("No userSelectedOrganisationType details in session")
    }

  }

  private def displayForm()(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      doYouHaveNinoController
        .displayForm(atarService, Journey.Register)
        .apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

  private def submitForm(form: Map[String, String])(test: Future[Result] => Any) {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      doYouHaveNinoController
        .submit(atarService, Journey.Register)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, form))
    )
  }

}
