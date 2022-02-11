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

package unit.services.subscription

import base.UnitSpec
import common.support.testdata.TestData
import org.mockito.ArgumentMatchers.{eq => meq, _}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.{HandleSubscriptionConnector, NotifyRcmConnector}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.HandleSubscriptionRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{
  NotifyRcmRequest,
  RecipientDetails,
  SubscriptionDetails
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{Eori, NameIdOrganisationMatchModel, SafeId, TaxPayerId}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{HandleSubscriptionService, NotifyRcmService}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class NotifyRcmServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfter {

  private val mockNotifyRcmConnector     = mock[NotifyRcmConnector]
  private val mockSessionCache           = mock[SessionCache]
  private val nameId                     = NameIdOrganisationMatchModel(name = "orgname", id = "ID")
  private val service                    = new NotifyRcmService(mockSessionCache, mockNotifyRcmConnector)
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  before {
    reset(mockNotifyRcmConnector)
  }

  "NotifyRcmService" should {
    "call handle subscription connector with a valid handle subscription request" in {
      when(mockSessionCache.subscriptionDetails(any[HeaderCarrier]))
        .thenReturn(SubscriptionDetails(nameIdOrganisationDetails = Some(nameId)))
      when(mockSessionCache.email(any[HeaderCarrier])).thenReturn(Future.successful("test@example.com"))
      when(mockNotifyRcmConnector.notifyRCM(any[NotifyRcmRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful {})
      service.notifyRcm(atarService)(hc, global)
    }
  }

}
