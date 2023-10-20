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

package unit.services

import akka.dispatch.ThreadPoolConfig.defaultTimeout
import base.UnitSpec
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Reads
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.Save4LaterConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney, Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Save4LaterServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ScalaFutures {
  private val mockSave4LaterConnector = mock[Save4LaterConnector]

  private implicit val hc: HeaderCarrier = mock[HeaderCarrier]
  private val safeId                     = SafeId("safeId")
  private val groupId                    = GroupId("groupId-123")

  private val organisationType: CdsOrganisationType =
    CdsOrganisationType.Company

  private val emailStatus = EmailStatus(Some("test@example.com"))

  private val safeIdKey   = "safeId"
  private val orgTypeKey  = "orgType"
  private val emailKey    = "email"
  private val cdsEmailKey = "cdsEmail"
  private val groupIdKey  = "cachedGroupId"

  private val service =
    new Save4LaterService(mockSave4LaterConnector)

  override implicit def patienceConfig: PatienceConfig =
    super.patienceConfig.copy(timeout = Span(defaultTimeout.toMillis, Millis))

  override protected def beforeEach(): Unit =
    reset(mockSave4LaterConnector)

  "Save4LaterService" should {
    "save abd fetch the safeId against the users InternalId" in {
      when(
        mockSave4LaterConnector.put[SafeId](
          ArgumentMatchers.eq(groupId.id),
          ArgumentMatchers.eq(safeIdKey),
          ArgumentMatchers.eq(safeId)
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveSafeId(groupId, safeId)
        .futureValue
      result shouldBe ((): Unit)
    }

    "save the CdsOrganisationType against the users InternalId" in {
      when(
        mockSave4LaterConnector.put[CdsOrganisationType](
          ArgumentMatchers.eq(groupId.id),
          ArgumentMatchers.eq(orgTypeKey),
          ArgumentMatchers.eq(Some(organisationType))
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveOrgType(groupId, Some(organisationType))
        .futureValue
      result shouldBe ((): Unit)
    }

    "save the CDS email for CDS Short Journey" in {
      when(
        mockSave4LaterConnector.put[EmailStatus](
          ArgumentMatchers.eq(groupId.id),
          ArgumentMatchers.eq(cdsEmailKey),
          ArgumentMatchers.eq(emailStatus)
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveEmailForService(emailStatus)(cdsService, SubscribeJourney(AutoEnrolment), groupId)
        .futureValue

      result shouldBe ((): Unit)
    }

    "save email for CDS Long Journey" in {
      when(
        mockSave4LaterConnector.put[EmailStatus](
          ArgumentMatchers.eq(groupId.id),
          ArgumentMatchers.eq(emailKey),
          ArgumentMatchers.eq(emailStatus)
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveEmailForService(emailStatus)(cdsService, SubscribeJourney(LongJourney), groupId)
        .futureValue

      result shouldBe ((): Unit)
    }

    "save email for non CDS Short Journey" in {
      when(
        mockSave4LaterConnector.put[EmailStatus](
          ArgumentMatchers.eq(groupId.id),
          ArgumentMatchers.eq(emailKey),
          ArgumentMatchers.eq(emailStatus)
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveEmailForService(emailStatus)(atarService, SubscribeJourney(AutoEnrolment), groupId)
        .futureValue

      result shouldBe ((): Unit)
    }

    "save the email against the users InternalId" in {
      when(
        mockSave4LaterConnector.put[EmailStatus](
          ArgumentMatchers.eq(groupId.id),
          ArgumentMatchers.eq(emailKey),
          ArgumentMatchers.eq(emailStatus)
        )(any[HeaderCarrier])
      ).thenReturn(Future.successful(()))

      val result: Unit = service
        .saveEmail(groupId, emailStatus)
        .futureValue
      result shouldBe ((): Unit)
    }

    "fetch the email for the users InternalId" in {
      when(
        mockSave4LaterConnector.get[EmailStatus](ArgumentMatchers.eq(groupId.id), ArgumentMatchers.eq(emailKey))(
          any[HeaderCarrier],
          any[Reads[EmailStatus]]
        )
      ).thenReturn(Future.successful(Some(emailStatus)))

      val result = service
        .fetchEmail(groupId)
        .futureValue
      result shouldBe Some(emailStatus)
    }
  }

  "fetch the CDS email for CDS Short Journey" in {
    when(
      mockSave4LaterConnector.get[EmailStatus](ArgumentMatchers.eq(groupId.id), ArgumentMatchers.eq(cdsEmailKey))(
        any[HeaderCarrier],
        any[Reads[EmailStatus]]
      )
    ).thenReturn(Future.successful(Some(emailStatus)))

    val result = service
      .fetchEmailForService(cdsService, SubscribeJourney(AutoEnrolment), groupId)
      .futureValue
    result shouldBe Some(emailStatus)
  }

  "fetch email for CDS Long Journey" in {
    when(
      mockSave4LaterConnector.get[EmailStatus](ArgumentMatchers.eq(groupId.id), ArgumentMatchers.eq(emailKey))(
        any[HeaderCarrier],
        any[Reads[EmailStatus]]
      )
    ).thenReturn(Future.successful(Some(emailStatus)))

    val result = service
      .fetchEmailForService(cdsService, SubscribeJourney(LongJourney), groupId)
      .futureValue
    result shouldBe Some(emailStatus)
  }

  "fetch email for non CDS Short Journey" in {
    when(
      mockSave4LaterConnector.get[EmailStatus](ArgumentMatchers.eq(groupId.id), ArgumentMatchers.eq(emailKey))(
        any[HeaderCarrier],
        any[Reads[EmailStatus]]
      )
    ).thenReturn(Future.successful(Some(emailStatus)))

    val result = service
      .fetchEmailForService(atarService, SubscribeJourney(AutoEnrolment), groupId)
      .futureValue
    result shouldBe Some(emailStatus)
  }

  "fetch the CacheIds for the users InternalId" in {
    when(
      mockSave4LaterConnector.get[CacheIds](ArgumentMatchers.eq(groupId.id), ArgumentMatchers.eq(groupIdKey))(
        any[HeaderCarrier],
        any[Reads[CacheIds]]
      )
    ).thenReturn(Future.successful(Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some(atarService.code)))))

    val result = service
      .fetchCacheIds(groupId)
      .futureValue
    result shouldBe Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some(atarService.code)))
  }

  "fetch the fetchProcessingService for the users InternalId" in {
    when(
      mockSave4LaterConnector.get[CacheIds](ArgumentMatchers.eq(groupId.id), ArgumentMatchers.eq(groupIdKey))(
        any[HeaderCarrier],
        any[Reads[CacheIds]]
      )
    ).thenReturn(Future.successful(Some(CacheIds(InternalId("int-id"), SafeId("safe-id"), Some(atarService.code)))))

    val result = service
      .fetchProcessingService(groupId).futureValue
    result shouldBe Some(
      Service(
        "atar",
        "HMRC-ATAR-ORG",
        "ATaR",
        Some("http://localhost:9582/advance-tariff-application"),
        "Advance Tariff Rulings",
        "Dyfarniadau Tariffau Uwch (ATaR)",
        None
      )
    )
  }

  "return None when connector returns None for fetchProcessingService for the users InternalId" in {
    when(
      mockSave4LaterConnector.get[CacheIds](ArgumentMatchers.eq(groupId.id), ArgumentMatchers.eq(groupIdKey))(
        any[HeaderCarrier],
        any[Reads[CacheIds]]
      )
    ).thenReturn(Future.successful(None))

    val result = service
      .fetchProcessingService(groupId).futureValue
    result shouldBe None
  }

  "deleteCachedGroupId against the users InternalId" in {
    when(
      mockSave4LaterConnector.deleteKey[CacheIds](ArgumentMatchers.eq(groupId.id), ArgumentMatchers.eq(groupIdKey))(
        any[HeaderCarrier]
      )
    ).thenReturn(Future.successful(()))

    val result: Unit = service
      .deleteCachedGroupId(groupId)
      .futureValue
    result shouldBe ((): Unit)
  }

  "deleteCacheIds against the users InternalId" in {
    when(mockSave4LaterConnector.delete[CacheIds](ArgumentMatchers.eq(groupId.id))(any[HeaderCarrier])).thenReturn(
      Future.successful(())
    )

    val result: Unit = service
      .deleteCacheIds(groupId)
      .futureValue
    result shouldBe ((): Unit)
  }

}
