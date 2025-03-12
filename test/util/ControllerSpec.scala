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

package util

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.testkit.NoMaterializer
import base.{Injector, UnitSpec}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.{DefaultFileMimeTypes, FileMimeTypesConfiguration}
import play.api.i18n.Lang._
import play.api.i18n.{I18nSupport, Messages, MessagesApi, MessagesImpl}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{
  AppConfig,
  InternalAuthTokenInitialiser,
  NoOpInternalAuthTokenInitialiser
}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import util.builders.{AuthBuilder, SessionBuilder}

import java.util.UUID
import scala.concurrent.ExecutionContext.global
import scala.util.Random

trait ControllerSpec extends UnitSpec with MockitoSugar with I18nSupport with Injector with TestData {

  implicit lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser])
    .build()

  implicit val messagesApi: MessagesApi = instanceOf[MessagesApi]

  implicit def materializer: Materializer = NoMaterializer

  implicit val messages: Messages = MessagesImpl(defaultLang, messagesApi)

  implicit val mcc: MessagesControllerComponents = DefaultMessagesControllerComponents(
    new DefaultMessagesActionBuilderImpl(stubBodyParser(AnyContentAsEmpty), messagesApi)(global),
    DefaultActionBuilder(stubBodyParser(AnyContentAsEmpty))(global),
    stubPlayBodyParsers(NoMaterializer),
    messagesApi, // Need to be a real messages api, because our tests checks the content, not keys
    stubLangs(),
    new DefaultFileMimeTypes(FileMimeTypesConfiguration()),
    global
  )

  protected val previousPageUrl = "#"

  val env: Environment = Environment.simple()

  val config: Configuration = Configuration.load(env)

  private val serviceConfig = new ServicesConfig(config)

  val appConfig: AppConfig = new AppConfig(config, serviceConfig, "eori-common-component-frontend")

  val getRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "")

  def postRequest(data: (String, String)*): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest("POST", "").withFormUrlEncodedBody(data: _*)

  protected def assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
    mockAuthConnector: AuthConnector,
    action: Action[AnyContent]
  ): Unit =
    "redirect to GG login when request is not authenticated when the Journey is for a Subscription Journey" in {
      AuthBuilder.withNotLoggedInUser(mockAuthConnector)

      val result = action.apply(
        SessionBuilder.buildRequestWithSessionAndPathNoUser(
          method = "GET",
          path = s"/customs-enrolment-services/atar/subscribe/"
        )
      )
      status(result) shouldBe SEE_OTHER
      header(LOCATION, result).value should include(
        "/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A6750%2Fcustoms-enrolment-services%2Fatar%2Fsubscribe&origin=eori-common-component-frontend"
      )
    }

  val defaultUserId: String = s"user-${UUID.randomUUID}"

  def strim(s: String): String = s.stripMargin.trim.split("\n").mkString(" ")

  def oversizedString(maxLength: Int): String = Random.alphanumeric.take(maxLength + 1).mkString
}
