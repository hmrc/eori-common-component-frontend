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

package integration

import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.mvc.Http.Status.{FORBIDDEN, INTERNAL_SERVER_ERROR}
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.SubscriptionStatusConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{
  SubscriptionStatusQueryParams,
  SubscriptionStatusResponseHolder,
  TaxPayerId
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import util.externalservices.ExternalServicesConfig._
import util.externalservices.{AuditService, SubscriptionStatusMessagingService}

import java.time.LocalDateTime

class SubscriptionStatusConnectorSpec extends IntegrationTestsSpec with ScalaFutures {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "microservice.services.eori-common-component-hods-proxy.port"                        -> Port,
        "microservice.services.eori-common-component-hods-proxy.subscription-status.context" -> "subscription-status",
        "auditing.enabled"                                                                   -> true,
        "auditing.consumer.baseUri.port"                                                     -> Port
      )
    )
    .overrides(bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser])
    .build()

  private val subscriptionStatusConnector = app.injector.instanceOf[SubscriptionStatusConnector]
  private val AValidTaxPayerID            = "1234567890"
  private val taxPayerId                  = TaxPayerId(AValidTaxPayerID).mdgTaxPayerId
  private val safeId                      = "someSafeId"
  private val Regime                      = "CDS"
  private val receiptDate                 = LocalDateTime.of(2016, 3, 17, 9, 30, 47, 114)
  private val receiptDateWithZeroSeconds  = LocalDateTime.of(2016, 3, 17, 9, 30, 0, 114)
  private val colon: String               = "%3A"

  private val expectedGetUrl =
    s"/subscription-status?receiptDate=2016-03-17T09${colon}30${colon}47Z&regime=$Regime&taxPayerID=$taxPayerId"

  private val expectedGetUrlWithSafeId =
    s"/subscription-status?receiptDate=2016-03-17T09${colon}30${colon}47Z&regime=$Regime&SAFE=$safeId"

  private val expectedGetUrlForReceiptDateZeroSeconds =
    s"/subscription-status?receiptDate=2016-03-17T09${colon}30${colon}00Z&regime=$Regime&taxPayerID=$taxPayerId"

  private val request =
    SubscriptionStatusQueryParams(receiptDate, Regime, "taxPayerID", TaxPayerId(AValidTaxPayerID).mdgTaxPayerId)

  private val requestWithSafeId =
    SubscriptionStatusQueryParams(receiptDate, Regime, "SAFE", "someSafeId")

  private val requestWithReceiptDateZeroSeconds =
    SubscriptionStatusQueryParams(
      receiptDateWithZeroSeconds,
      Regime,
      "taxPayerID",
      TaxPayerId(AValidTaxPayerID).mdgTaxPayerId
    )

  implicit val hc: HeaderCarrier           = HeaderCarrier()
  implicit val originatingService: Service = Service.cds

  val responseWithOk: JsValue =
    Json.parse("""
        |{
        |  "subscriptionStatusResponse": {
        |    "responseCommon": {
        |      "status": "OK",
        |      "processingDate": "2016-03-17T09:30:47Z"
        |    },
        |    "responseDetail": {
        |      "subscriptionStatus": "00"
        |    }
        |  }
        |}
      """.stripMargin)

  before {
    resetMockServer()
    AuditService.stubAuditService()
  }

  override def beforeAll(): Unit =
    startMockServer()

  override def afterAll(): Unit =
    stopMockServer()

  "subscription status" should {
    "return successful response with OK status when subscription status service returns 200" in {

      SubscriptionStatusMessagingService.returnTheSubscriptionResponseWhenReceiveRequest(
        expectedGetUrl,
        responseWithOk.toString
      )
      await(subscriptionStatusConnector.status(request)) must be(
        responseWithOk.as[SubscriptionStatusResponseHolder].subscriptionStatusResponse
      )
    }
    "return successful response for subscriotion status request providing SAFE ID" in {

      SubscriptionStatusMessagingService.returnTheSubscriptionResponseWhenReceiveRequest(
        expectedGetUrlWithSafeId,
        responseWithOk.toString
      )
      await(subscriptionStatusConnector.status(requestWithSafeId)) must be(
        responseWithOk.as[SubscriptionStatusResponseHolder].subscriptionStatusResponse
      )
    }

    "format receiptDate having zero seconds correctly and return successful response with OK status" in {

      SubscriptionStatusMessagingService.returnTheSubscriptionResponseWhenReceiveRequest(
        expectedGetUrlForReceiptDateZeroSeconds,
        responseWithOk.toString
      )
      await(subscriptionStatusConnector.status(requestWithReceiptDateZeroSeconds)) must be(
        responseWithOk.as[SubscriptionStatusResponseHolder].subscriptionStatusResponse
      )
    }

    "fail when Internal Server Error" in {
      SubscriptionStatusMessagingService.stubTheSubscriptionResponse(
        expectedGetUrl,
        responseWithOk.toString,
        INTERNAL_SERVER_ERROR
      )

      val caught: UpstreamErrorResponse = intercept[UpstreamErrorResponse] {
        await(subscriptionStatusConnector.status(request))
      }

      caught.statusCode mustBe 500
      caught.getMessage must startWith("GET of ")
    }

    "fail when 4xx status code is received" in {
      SubscriptionStatusMessagingService.stubTheSubscriptionResponse(expectedGetUrl, responseWithOk.toString, FORBIDDEN)

      val caught: UpstreamErrorResponse = intercept[UpstreamErrorResponse] {
        await(subscriptionStatusConnector.status(request))
      }

      caught.statusCode mustBe 403
      caught.getMessage must startWith("GET of ")
    }

    "audit a successful request" in {
      SubscriptionStatusMessagingService.returnTheSubscriptionResponseWhenReceiveRequest(
        expectedGetUrl,
        responseWithOk.toString
      )

      await(subscriptionStatusConnector.status(request))
      eventually(AuditService.verifyXAuditWrite(1))
    }
  }
}
