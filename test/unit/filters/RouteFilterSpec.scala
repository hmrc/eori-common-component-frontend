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

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import base.UnitSpec
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.{RequestHeader, Result, Results}
import play.api.test.FakeRequest
import uk.gov.hmrc.customs.rosmfrontend.filters.RouteFilter

import scala.concurrent.Future

class RouteFilterSpec extends UnitSpec with MockitoSugar {

  implicit val system = ActorSystem()
  implicit val mat: Materializer = ActorMaterializer()

  "RouteFilter" should {

    "ignore the filter when application is running in Dev mode" in {
      val filter = new RouteFilter()
      val request = FakeRequest("GET", "/some-url")

      val headerToResultFunction: RequestHeader => Future[Result] = _ => Future.successful(Results.Ok)

      val result: Result = await(filter.apply(headerToResultFunction)(request))

      status(result) shouldBe 200
    }
  }
}
