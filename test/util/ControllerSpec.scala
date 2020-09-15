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

package util

import java.util.UUID

import akka.stream.Materializer
import base.UnitSpec
import common.pages.WebPage
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Configuration, Environment, Mode, Play}
import play.api.i18n.{I18nSupport, Messages, MessagesApi, MessagesImpl}
import play.api.mvc._
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController
import unit.UnitTestApp
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthBuilder, SessionBuilder}
import play.api.i18n.Lang._
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.Future
import scala.util.Random

trait ControllerSpec extends UnitSpec with GuiceOneAppPerSuite with MockitoSugar with I18nSupport with UnitTestApp {

  implicit val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  implicit def materializer: Materializer = Play.materializer

  implicit val messages: Messages = MessagesImpl(defaultLang, messagesApi)

  implicit val mcc: MessagesControllerComponents = app.injector.instanceOf[MessagesControllerComponents]

  protected val previousPageUrl = "javascript:history.back()"

  val env: Environment = Environment.simple()

  implicit val config: Configuration = Configuration.load(env)

  private val runMode = new RunMode(config, Mode.Dev)

  private val serviceConfig = new ServicesConfig(config, runMode)

  implicit val appConfig: AppConfig = new AppConfig(config, serviceConfig, runMode, "eori-common-component-frontend")

  protected def assertNotLoggedInUserShouldBeRedirectedToLoginPage(
    mockAuthConnector: AuthConnector,
    actionDescription: String,
    action: Action[AnyContent]
  ): Unit =
    actionDescription should {
      "redirect to GG login when request is not authenticated" in {
        AuthBuilder.withNotLoggedInUser(mockAuthConnector)

        val result = action.apply(
          SessionBuilder.buildRequestWithSessionAndPathNoUser(
            method = "GET",
            path = s"/customs-enrolment-services/register/"
          )
        )
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result) shouldBe Some(
          "/gg/sign-in?continue=http%3A%2F%2Flocalhost%3A6750%2Fcustoms-enrolment-services%2Fregister%2Fmatch&origin=eori-common-component-frontend"
        )
      }
    }

  protected def assertNotLoggedInAndCdsEnrolmentChecksForGetAnEori(
    mockAuthConnector: AuthConnector,
    action: Action[AnyContent],
    additionalLabel: String = ""
  ): Unit =
    s"redirect to GG login when request is not authenticated when the Journey is for a Get An EORI Journey $additionalLabel" in {
      AuthBuilder.withNotLoggedInUser(mockAuthConnector)

      val result: Future[Result] = action.apply(
        SessionBuilder.buildRequestWithSessionAndPathNoUser(
          method = "GET",
          path = s"/customs-enrolment-services/register/"
        )
      )
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result) shouldBe Some(
        "/gg/sign-in?continue=http%3A%2F%2Flocalhost%3A6750%2Fcustoms-enrolment-services%2Fregister%2Fmatch&origin=eori-common-component-frontend"
      )
    }

  protected def assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
    mockAuthConnector: AuthConnector,
    action: Action[AnyContent]
  ): Unit =
    "redirect to GG login when request is not authenticated when the Journey is for a Subscription Journey" in {
      AuthBuilder.withNotLoggedInUser(mockAuthConnector)

      val result = action.apply(
        SessionBuilder.buildRequestWithSessionAndPathNoUser(
          method = "GET",
          path = s"/customs-enrolment-services/subscribe/"
        )
      )
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result) shouldBe Some(
        s"/gg/sign-in?continue=http%3A%2F%2Flocalhost%3A6750%2Fcustoms-enrolment-services%2Fsubscribe%2Fsubscribe&origin=eori-common-component-frontend"
      )
    }

  def assertCustomPageLevelError(
    result: Future[Result],
    pageLevelErrorSummaryXPath: String,
    errorMessage: String
  ): Unit = {
    status(result) shouldBe BAD_REQUEST
    val page = CdsPage(bodyOf(result))
    page.getElementsText(pageLevelErrorSummaryXPath) shouldBe errorMessage
  }

  def assertFieldLevelError(
    result: Future[Result],
    field: String,
    fieldLevelErrorXPath: String,
    errorMessage: String
  ): Unit = {
    status(result) shouldBe BAD_REQUEST
    val page = CdsPage(bodyOf(result))

    withClue(s"Not found in the page: field level error block for '$field' with xpath $fieldLevelErrorXPath") {
      page.elementIsPresent(fieldLevelErrorXPath) shouldBe true
    }

    page.getElementsText(fieldLevelErrorXPath) shouldBe errorMessage
  }

  def assertRadioButtonIsPresent(
    page: CdsPage,
    labelXpath: String,
    expectedText: String,
    expectedValue: String
  ): Unit = {
    page.getElementText(labelXpath) should be(expectedText)
    page.getElementValueForLabel(labelXpath) should be(expectedValue)
  }

  trait AbstractControllerFixture[C <: FrontendController] {
    val mockAuthConnector = mock[AuthConnector]
    val userId            = defaultUserId

    val controller: C

    private def withAuthorisedUser[T](block: => T): T = {
      AuthBuilder.withAuthorisedUser(userId, mockAuthConnector)
      block
    }

    protected def show(controller: C): Action[AnyContent]

    def showForm[T](test: Future[Result] => T): T = withAuthorisedUser {
      test(show(controller).apply(SessionBuilder.buildRequestWithSession(userId)))
    }

    protected def submit(controller: C): Action[AnyContent]

    def submitForm[T](formValues: Map[String, String])(test: Future[Result] => T): T = withAuthorisedUser {
      test(submit(controller).apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, formValues)))
    }

    def assertInvalidField(
      formValues: Map[String, String],
      webPage: WebPage
    )(problemField: String, fieldLevelErrorXPath: String, errorMessage: String): Result =
      submitForm(formValues) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(bodyOf(result))
        page.getElementsText(webPage.pageLevelErrorSummaryListXPath) shouldBe errorMessage
        withClue(
          s"Not found in the page: field level error block for '$problemField' with xpath $fieldLevelErrorXPath"
        ) {
          page.elementIsPresent(fieldLevelErrorXPath) shouldBe true
        }
        page.getElementsText(fieldLevelErrorXPath) shouldBe errorMessage
        result
      }

    def assertPresentOnPage(page: CdsPage)(elementXpath: String): Unit =
      withClue(s"Element xpath not present in page: $elementXpath")(page.elementIsPresent(elementXpath) shouldBe true)

  }

  implicit val hc = mock[HeaderCarrier]

  val Required              = "This field is required"
  var NoSelection           = "Please select one of the options"
  val InvalidDate           = "Please enter a valid date, for example '31 3 1980'"
  val FutureDate            = "You must specify a date that is not in the future"
  val enterAValidEori       = "Enter an EORI number in the right format"
  val enterAGbEori          = "Enter an EORI number that starts with GB"
  val defaultUserId: String = s"user-${UUID.randomUUID}"

  val helpAndSupportLabelXpath: String = "//*[@id='helpAndSupport']"

  val helpAndSupportText: String =
    "Help and support Telephone: 0300 322 7067 Open 8am to 6pm, Monday to Friday (closed bank holidays)."

  private val cdsEnrolmentId: Option[String] = Some("GB1234567890ABCDE")

  def strim(s: String): String = s.stripMargin.trim.lines mkString " "

  def oversizedString(maxLength: Int): String = Random.alphanumeric.take(maxLength + 1).mkString

  def undersizedString(minLength: Int): String = Random.alphanumeric.take(minLength - 1).mkString

  def maxLength(maxLength: Int): String = s"Maximum length is $maxLength"

  def thereIsAProblemWithThe(field: String): String = s"Enter the $field"

  def customPageSummaryError(field: String, prefix: String): String = messagesApi(s"$prefix.$field")(defaultLang)
}
