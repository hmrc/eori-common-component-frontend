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

import common.pages.matching.{
  IndividualNameAndDateOfBirthPage,
  ThirdCountryIndividualNameAndDateOfBirthPage,
  ThirdCountrySoleTraderNameAndDateOfBirthPage
}
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalacheck.Prop
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.Checkers
import play.api.Application
import play.api.data.Form
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.FeatureFlags
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.RowIndividualNameDateOfBirthController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{IndividualNameAndDateOfBirth, NameDobMatchModel}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.row_individual_name_dob
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthActionMock
import util.scalacheck.TestDataGenerators

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.global

class RowIndividualNameDateOfBirthControllerWithFeatureFalseSpec
    extends ControllerSpec with Checkers with TestDataGenerators with BeforeAndAfterEach with ScalaFutures
    with AuthActionMock {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder().configure("features.rowHaveUtrEnabled" -> false).build()

  class ControllerFixture(organisationType: String, form: Form[IndividualNameAndDateOfBirth])
      extends AbstractControllerFixture[RowIndividualNameDateOfBirthController] {
    val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]

    private val rowIndividualNameDob = app.injector.instanceOf[row_individual_name_dob]
    private val mockAuthAction       = authAction(mockAuthConnector)
    private val featureFlags         = app.injector.instanceOf[FeatureFlags]

    override val controller = new RowIndividualNameDateOfBirthController(
      mockAuthAction,
      featureFlags,
      mockSubscriptionDetailsService,
      mcc,
      rowIndividualNameDob
    )(global)

    def saveRegistrationDetailsMockSuccess() {
      when(mockSubscriptionDetailsService.cacheNameDobDetails(any[NameDobMatchModel])(any[HeaderCarrier]))
        .thenReturn(Future.successful(()))
    }

    protected def show(с: RowIndividualNameDateOfBirthController): Action[AnyContent] =
      с.form(organisationType, Service.ATaR, Journey.Register)

    protected def submit(c: RowIndividualNameDateOfBirthController): Action[AnyContent] =
      c.submit(false, organisationType, Service.ATaR, Journey.Register)

    def formData(thirdCountryIndividual: IndividualNameAndDateOfBirth): Map[String, String] =
      form.mapping.unbind(thirdCountryIndividual)

  }

  val InvalidDateError: String = InvalidDate
  val emulatedFailure          = new UnsupportedOperationException("Emulation of service call failure")

  abstract class IndividualNameAndDateOfBirthBehaviour(
    webPage: IndividualNameAndDateOfBirthPage,
    form: Form[IndividualNameAndDateOfBirth],
    val validFormModelGens: IndividualGens[LocalDate]
  ) {

    protected val organisationType: String = webPage.organisationType

    "submitting a valid form" should {

      "redirect to address details page when features.rowHaveUtrEnabled is False" in testControllerWithModel(
        validFormModelGens
      ) { (controllerFixture, individualNameAndDateOfBirth) =>
        import controllerFixture._
        saveRegistrationDetailsMockSuccess()

        submitForm(formData(individualNameAndDateOfBirth)) { result =>
          CdsPage(contentAsString(result)).getElementsHtml(webPage.pageLevelErrorSummaryListXPath) shouldBe empty
          status(result) shouldBe SEE_OTHER
          result.futureValue.header.headers(
            LOCATION
          ) shouldBe s"/customs-enrolment-services/atar/register/matching/address/$organisationType"
          verify(mockSubscriptionDetailsService).cacheNameDobDetails(any())(any())
        }
      }
    }

    protected def withControllerFixture[TestResult](body: ControllerFixture => TestResult): TestResult =
      body(new ControllerFixture(organisationType, form))

    protected def testControllerWithModel(
      formModelGens: IndividualGens[LocalDate]
    )(test: (ControllerFixture, IndividualNameAndDateOfBirth) => Unit): Unit =
      check(Prop.forAllNoShrink(individualNameAndDateOfBirthGenerator(formModelGens)) { individualNameAndDateOfBirth =>
        withControllerFixture { controllerFixture =>
          test.apply(controllerFixture, individualNameAndDateOfBirth)
          Prop.proved
        }
      })

  }

  abstract class ThirdCountryIndividualBehaviour(webPage: IndividualNameAndDateOfBirthPage)
      extends IndividualNameAndDateOfBirthBehaviour(
        webPage,
        form = MatchingForms.thirdCountryIndividualNameDateOfBirthForm,
        validFormModelGens = individualNameAndDateOfBirthGens()
      ) {}

  case object ThirdCountrySoleTraderBehavior
      extends ThirdCountryIndividualBehaviour(ThirdCountrySoleTraderNameAndDateOfBirthPage)

  case object ThirdCountryIndividualBehavior
      extends ThirdCountryIndividualBehaviour(ThirdCountryIndividualNameAndDateOfBirthPage)

  "The third country sole trader case" when (behave like ThirdCountrySoleTraderBehavior)

  "The third country individual case" when (behave like ThirdCountryIndividualBehavior)
}
