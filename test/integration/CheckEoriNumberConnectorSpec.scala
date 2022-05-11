/*
 * Copyright 2022 HM Revenue & Customs
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

package integration

import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.CheckEoriNumberConnector
import uk.gov.hmrc.http._
import util.externalservices.CheckEoriNumberService
import util.externalservices.ExternalServicesConfig._

import scala.concurrent.ExecutionContext.Implicits.global

class CheckEoriNumberConnectorSpec extends IntegrationTestsSpec with ScalaFutures {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "microservice.services.check-eori-number.host"    -> Host,
        "microservice.services.check-eori-number.port"    -> Port,
        "microservice.services.check-eori-number.context" -> "check-eori-number",
        "auditing.enabled"                                -> false,
        "auditing.consumer.baseUri.host"                  -> Host,
        "auditing.consumer.baseUri.port"                  -> Port
      )
    )
    .build()

  private lazy val connector = app.injector.instanceOf[CheckEoriNumberConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  before {
    resetMockServer()
  }

  override def beforeAll: Unit = startMockServer()

  override def afterAll: Unit = stopMockServer()

  "CheckEoriNumber" should {

    "return a response with a valid EORI Number parsed from a HTTP response" in {
      val eori = "GB89898989898989"
      CheckEoriNumberService.returnEoriValidCheck(eori)
      connector.check(eori).futureValue mustBe Some(true)
    }

    "return a response with a invalid EORI Number parsed from a 404 HTTP response" in {
      val eori = "GB89898989898989"
      CheckEoriNumberService.returnEoriInvalidCheck(eori)
      connector.check(eori).futureValue mustBe Some(false)
    }

    "return a response with a valid EORI Number parsed from an unexpected error HTTP response" in {
      val eori = "GB89898989898989"
      CheckEoriNumberService.returnEoriUndeterminedCheck(eori)
      connector.check(eori).futureValue mustBe Some(true)
    }
  }

}
