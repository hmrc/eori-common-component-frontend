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

package unit.filters

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import base.UnitSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{RequestHeader, Result, Results}
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.rosmfrontend.CdsErrorHandler
import uk.gov.hmrc.customs.rosmfrontend.config.AppConfig
import uk.gov.hmrc.customs.rosmfrontend.filters.RouteFilter

import scala.concurrent.Future

class RouteFilterSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val system = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()
  val errorHandler = mock[CdsErrorHandler]
  val config = mock[AppConfig]

  private def filter = new RouteFilter(config, errorHandler)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    when(errorHandler.onClientError(any(), any(), any())).thenReturn(Future.successful(Results.NotFound("Content")))
  }

  override protected def afterEach(): Unit = {
    reset(errorHandler, config)
    super.afterEach()
  }

  "RouteFilter" should {

    "ignore the filter when there are no blocked routes" in {

      when(config.blockedRoutesRegex).thenReturn(List())
      val request = FakeRequest("GET", "/some-url")

      val headerToResultFunction: RequestHeader => Future[Result] = _ => Future.successful(Results.Ok)

      val result: Result = await(filter.apply(headerToResultFunction)(request))

      status(result) shouldBe 200
    }

    "return 404 when blocked routes contains a URL that matches" in {
      when(config.blockedRoutesRegex).thenReturn(List("/some-url".r))
      val request = FakeRequest("GET", "/some-url")

      val result: Result = await(filter.apply(okAction)(request))

      status(result) shouldBe 404
    }

    "return 200 when blocked routes contains a URL that doesn't match" in {
      when(config.blockedRoutesRegex).thenReturn(List("/some-url".r))
      val request = FakeRequest("GET", "/some-other-url")

      val result: Result = await(filter.apply(okAction)(request))

      status(result) shouldBe 200
    }

  }

  private val okAction = (rh: RequestHeader) => Future.successful(Results.Ok)
}
