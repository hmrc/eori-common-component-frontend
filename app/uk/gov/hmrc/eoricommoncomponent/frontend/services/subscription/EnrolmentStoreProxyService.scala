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
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.EnrolmentStoreProxyConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EnrolmentResponse, GroupId}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnrolmentStoreProxyService @Inject() (enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector)(implicit
  ec: ExecutionContext
) {

  private val activatedState = "Activated"

  // TODO - delete - only used by tests
  def isEnrolmentAssociatedToGroup(groupId: GroupId, service: Service)(implicit hc: HeaderCarrier): Future[Boolean] =
    enrolmentStoreProxyConnector
      .getEnrolmentByGroupId(groupId.id)
      .map(_.enrolments)
      .map { enrolment =>
        enrolment.exists(x => x.state == activatedState && x.service == service.enrolmentKey)
      }

  def enrolmentForGroup(groupId: GroupId, service: Service)(implicit
    hc: HeaderCarrier
  ): Future[Option[EnrolmentResponse]] =
    enrolmentStoreProxyConnector
      .getEnrolmentByGroupId(groupId.id)
      .map(_.enrolments)
      .map(enrolment => enrolment.find(x => x.state == activatedState && x.service == service.enrolmentKey))

}
