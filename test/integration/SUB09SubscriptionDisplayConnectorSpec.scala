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

import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.mvc.Http.Status.*
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.{
  EoriHttpResponse,
  SUB09SubscriptionDisplayConnector,
  ServiceUnavailableResponse
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.{
  ContactInformation,
  SubscriptionDisplayResponse,
  SubscriptionDisplayResponseHolder
}
import uk.gov.hmrc.http.*
import util.externalservices.ExternalServicesConfig.*
import util.externalservices.SubscriptionDisplayMessagingService

import java.time.temporal.ChronoUnit.MINUTES

class SUB09SubscriptionDisplayConnectorSpec extends IntegrationTestsSpec with ScalaFutures with EitherValues {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      Map(
        "microservice.services.eori-common-component-hods-proxy.host"                         -> Host,
        "microservice.services.eori-common-component-hods-proxy.port"                         -> Port,
        "microservice.services.eori-common-component-hods-proxy.subscription-display.context" -> "subscription-display",
        "auditing.enabled"                                                                    -> false,
        "auditing.consumer.baseUri.host"                                                      -> Host,
        "auditing.consumer.baseUri.port"                                                      -> Port
      )
    )
    .overrides(bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser])
    .build()

  private lazy val connector                  = app.injector.instanceOf[SUB09SubscriptionDisplayConnector]
  private val requestTaxPayerId               = "GBE9XSDF10BCKEYAX"
  private val requestEori                     = "GB083456789000"
  private val requestAcknowledgementReference = "1234567890ABCDEFG"

  private val reqTaxPayerId = Seq(
    ("regime", "CDS"),
    ("taxPayerID", requestTaxPayerId),
    ("acknowledgementReference", requestAcknowledgementReference)
  )

  private val reqEori =
    Seq(("regime", "CDS"), ("EORI", requestEori), ("acknowledgementReference", requestAcknowledgementReference))

  private val expectedResponse: SubscriptionDisplayResponse = Json
    .parse(SubscriptionDisplayMessagingService.validResponse(typeOfLegalEntity = "0001"))
    .as[SubscriptionDisplayResponseHolder]
    .subscriptionDisplayResponse

  implicit val hc: HeaderCarrier = HeaderCarrier()

  before {
    resetMockServer()
  }

  override def beforeAll(): Unit =
    startMockServer()

  override def afterAll(): Unit =
    stopMockServer()

  "SubscriptionDisplay SUB09" should {
    "return successful response with OK status when subscription display service returns 200, for EoriNo and journey is Subscription" in {

      SubscriptionDisplayMessagingService.returnSubscriptionDisplayWhenReceiveRequest(
        requestEori,
        requestAcknowledgementReference
      )

      whenReady(connector.subscriptionDisplay(reqEori, "atar")) { eitherResponse =>
        val response: SubscriptionDisplayResponse = eitherResponse.value
        val responseWithTruncatedTimeValues: SubscriptionDisplayResponse = response.copy(responseDetail =
          response.responseDetail.copy(contactInformation =
            Some(
              response.responseDetail.contactInformation.head.copy(emailVerificationTimestamp =
                response.responseDetail.contactInformation.head.emailVerificationTimestamp.map(_.truncatedTo(MINUTES))
              )
            )
          )
        )

        val expectedResponseWithTruncatedTimeValues = expectedResponse.copy(responseDetail =
          expectedResponse.responseDetail.copy(contactInformation =
            Some(
              expectedResponse.responseDetail.contactInformation.head.copy(emailVerificationTimestamp =
                expectedResponse.responseDetail.contactInformation.head.emailVerificationTimestamp.map(
                  _.truncatedTo(MINUTES)
                )
              )
            )
          )
        )

        responseWithTruncatedTimeValues mustBe expectedResponseWithTruncatedTimeValues
      }
    }

    "return Service Unavailable Response when subscription display service returns an exception" in {

      SubscriptionDisplayMessagingService.returnSubscriptionDisplayWhenReceiveRequest(
        requestTaxPayerId,
        requestAcknowledgementReference,
        returnedStatus = SERVICE_UNAVAILABLE
      )

      whenReady(connector.subscriptionDisplay(reqTaxPayerId, "atar")) { response =>
        response mustBe Left(ServiceUnavailableResponse)
      }
    }
  }
}
