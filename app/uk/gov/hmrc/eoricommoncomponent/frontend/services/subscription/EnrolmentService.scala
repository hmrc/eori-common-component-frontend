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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.{EnrolmentStoreProxyConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.EnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.models.enrolmentRequest.{
  GovernmentGatewayEnrolmentRequest,
  Identifier,
  KnownFactsQuery,
  Verifier
}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnrolmentService @Inject() (
  enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector,
  taxEnrolmentsConnector: TaxEnrolmentsConnector
)(implicit ec: ExecutionContext)
    extends EnrolmentExtractor {

  def enrolWithExistingCDSEnrolment(eori: String, service: Service)(implicit hc: HeaderCarrier): Future[Int] =
    enrolmentStoreProxyConnector.queryKnownFactsByIdentifiers(KnownFactsQuery(eori)).flatMap {
      case Some(knownFacts) =>
        val cdsEnrolmentVerifiers =
          knownFacts.enrolments.headOption.map(_.verifiers).getOrElse(throw MissingEnrolmentException(eori))

        val governmentGatewayEnrolmentRequest = GovernmentGatewayEnrolmentRequest(
          identifiers = List(Identifier("EORINumber", eori)),
          verifiers = cdsEnrolmentVerifiers.map(Verifier.fromKeyValuePair)
        )

        taxEnrolmentsConnector.enrolAndActivate(service.enrolmentKey, governmentGatewayEnrolmentRequest).map(_.status)
      case _ => throw MissingEnrolmentException(eori)
    }

}

case class MissingEnrolmentException(eori: String) extends Exception(s"Missing key enrolment information: $eori")
