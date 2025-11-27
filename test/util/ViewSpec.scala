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

import base.Injector
import com.google.inject.name.Names
import org.apache.pekko.util.Timeout
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.i18n.*
import play.api.i18n.Lang.defaultLang
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.CSRFTokenHelper
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}

import scala.concurrent.duration.*

trait ViewSpec extends PlaySpec with CSRFTest with Injector with TestData with GuiceOneAppPerSuite {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "create-internal-auth-token-on-start" -> false
    )
    .overrides {
      bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser]
      bind[String].qualifiedWith(Names.named("appName")).toInstance("eori-common-component-frontend")
    }
    .build()

  private val messageApi: MessagesApi = app.injector.instanceOf[MessagesApi]

  implicit val messages: Messages = MessagesImpl(defaultLang, messageApi)

  implicit val timeout: Timeout = 30.seconds
  val userId: String            = "someUserId"
}

import play.api.test.FakeRequest

trait CSRFTest {

  def withFakeCSRF[T](fakeRequest: FakeRequest[T]): Request[T] =
    CSRFTokenHelper.addCSRFToken(fakeRequest)

  val fakeAtarSubscribeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/atar/subscribe")

  val defaultLangFakeRequest: Request[AnyContentAsEmpty.type] =
    FakeRequest("GET", "/atar/subscribe").withTransientLang(defaultLang)

}
