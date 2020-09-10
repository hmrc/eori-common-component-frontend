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

package uk.gov.hmrc.customs.rosmfrontend.services.subscription

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.customs.rosmfrontend.connector.{SUB09SubscriptionDisplayConnector, SubscriptionServiceConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.customs.rosmfrontend.domain.{KeyValue, TaxEnrolmentsRequest}
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.RequestCommon
import uk.gov.hmrc.customs.rosmfrontend.domain.messaging.subscription.{SubscriptionCreateRequest, SubscriptionRequest}
import uk.gov.hmrc.customs.rosmfrontend.services.RandomUUIDGenerator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@Singleton
class SubscriptionWithCDSEnrolmentService @Inject()(
  subscriptionDisplayConnector: SUB09SubscriptionDisplayConnector,
  uuidGenerator: RandomUUIDGenerator,
  subscriptionServiceConnector: SubscriptionServiceConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector
)(implicit ec: ExecutionContext) {

  // service name is just a placeholder, which is not used right now, after amending SUB02 schema we will use this
  def enrolToSpecificService(eori: String, serviceName: String = "HMRC-CUS-ORG")(implicit hc: HeaderCarrier) = {

    val uuid = uuidGenerator.generateUUIDAsString

    subscriptionDisplayConnector.subscriptionDisplay(queryParameters(eori, uuid)).flatMap { // SUB09 call to retrieve the subscription details
      case Right(subscriptionDisplayResponse) if subscriptionDisplayResponse.responseDetail.dateOfEstablishment.isDefined => //proceed only when date of establishment is defined
        val requestCommon = RequestCommon(
          regime = "CDS",
          receiptDate = subscriptionDisplayResponse.responseCommon.processingDate, // TODO Confirm if this date is correct or we need to generate a new one
          acknowledgementReference = uuid,
          originatingSystem = Some("MDTP"),
          requestParameters = None
        )
        val requestDetail = subscriptionDisplayResponse.responseDetail.toRequestDetail(uuid) // TODO Confirm if the SAFE ID is the uuid that we generate, it's not part of the response

        val subscriptionCreateRequest = SubscriptionCreateRequest(
          requestCommon = requestCommon,
          requestDetail = requestDetail
        )
        val subscriptionRequest = SubscriptionRequest(subscriptionCreateRequest)

        subscriptionServiceConnector.subscribe(subscriptionRequest).flatMap { _ => // SUB02 call
          val verifiers = subscriptionDisplayResponse.responseDetail.dateOfEstablishment.map { doe =>
            List(KeyValue(key = "DATEOFESTABLISHMENT", value = doe.toString(TaxEnrolmentsRequest.pattern)))
          }

          val taxEnrolmentsRequest = TaxEnrolmentsRequest(
            serviceName = serviceName,
            identifiers = List(KeyValue(key = "EORINUMBER", value = eori)),
            verifiers = verifiers,
            subscriptionState = "SUCCEEDED" // SUCCEEDED is a default value, what does it mean?
          )
          val formBundleId = subscriptionRequest.subscriptionCreateRequest.requestCommon.acknowledgementReference

          taxEnrolmentsConnector.enrol(taxEnrolmentsRequest, formBundleId)
        }
      case Left(_) => ??? // Redirect to the whole journey, we don't have user's subscription details
    }
  }

  private def queryParameters(eori: String, uuid: String): Seq[(String, String)] = Seq(
    "EORI" -> eori,
    "regime" -> "CDS",
    "acknowledgementReference" -> uuid
  )
}
