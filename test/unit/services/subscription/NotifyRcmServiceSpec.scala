/*
 * Copyright 2023 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfter
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.NotifyRcmConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.NameIdOrganisationMatchModel
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{NotifyRcmRequest, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.NotifyRcmService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NotifyRcmServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfter {

  private val mockNotifyRcmConnector            = mock[NotifyRcmConnector]
  private val mockSessionCache                  = mock[SessionCache]
  private val nameId                            = NameIdOrganisationMatchModel(name = "orgname", id = "ID")
  private val service                           = new NotifyRcmService(mockSessionCache, mockNotifyRcmConnector)
  private implicit val hc: HeaderCarrier        = HeaderCarrier()
  private implicit val req: Request[AnyContent] = mock[Request[AnyContent]]
  before {
    reset(mockNotifyRcmConnector)
  }

  "NotifyRcmService" should {
    "call handle subscription connector with a valid handle subscription request" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(SubscriptionDetails(nameIdOrganisationDetails = Some(nameId)))
      when(mockSessionCache.email(any[Request[AnyContent]])).thenReturn(Future.successful("test@example.com"))
      when(mockNotifyRcmConnector.notifyRCM(any[NotifyRcmRequest])(any[HeaderCarrier]))
        .thenReturn(Future.successful {})
      service.notifyRcm(atarService)(hc, req, global)
    }
  }

}
