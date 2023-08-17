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

import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, Enrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.GroupEnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.http.HeaderCarrier
import util.ControllerSpec
import util.builders.AuthActionMock

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrolmentJourneyServiceSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(3, Seconds)), interval = scaled(Span(50, Millis)))

  private val mockAuthConnector = mock[AuthConnector]

  private val mockSessionCache               = mock[SessionCache]
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]
  private val groupEnrolmentExtractor        = mock[GroupEnrolmentExtractor]

  val service = new EnrolmentJourneyService(mockSessionCache, groupEnrolmentExtractor, mockEnrolmentStoreProxyService)

  override protected def beforeEach(): Unit = {
    when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), any())(any()))
      .thenReturn(Future.successful(None))
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any()))
      .thenReturn(Future.successful(false))
    when(groupEnrolmentExtractor.checkAllServiceEnrolments(any())(any())).thenReturn(Future.successful(None))
  }

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector)
    reset(groupEnrolmentExtractor)
    reset(mockSessionCache)
    reset(mockEnrolmentStoreProxyService)
    super.afterEach()
  }

  private def groupEnrolment(service: Service) = Some(
    EnrolmentResponse(service.enrolmentKey, "Activated", List(KeyValue("EORINumber", "GB123456463324")))
  )

  private def loggedInUser(enrolments: Set[Enrolment]) =
    LoggedInUserWithEnrolments(None, None, Enrolments(enrolments), None, None, "credId")

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private implicit val request           = FakeRequest()
  private val groupId                    = "test-123"

  "Application Service" should {

    "return LongJourney to start subscription for users with no existing enrolments" in {
      val user = loggedInUser(Set.empty[Enrolment])

      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(Future.successful(List.empty))
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(Future.successful(None))

      val result = service.getJourney(user, groupId, atarService).futureValue

      result shouldBe Right(LongJourney)
    }

    "return ShortJourney for users with existing enrolment to other service" in {
      when(groupEnrolmentExtractor.groupIdEnrolmentTo(any(), ArgumentMatchers.eq(atarService))(any()))
        .thenReturn(Future.successful(None))
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(Future.successful(List.empty))
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(Future.successful(None))

      val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB134123")
      val user         = loggedInUser(Set(cdsEnrolment))

      val result = service.getJourney(user, groupId, atarService).futureValue

      result shouldBe Right(AutoEnrolment)
      verifyNoMoreInteractions(mockSessionCache)
    }

    "return ShortJourney for users where group has enrolment to other service" in {
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(
        Future.successful(List(groupEnrolment(cdsService).get))
      )
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockSessionCache.saveGroupEnrolment(any[EnrolmentResponse])(any())).thenReturn(Future.successful(true))

      val user   = loggedInUser(Set.empty[Enrolment])
      val result = service.getJourney(user, groupId, atarService).futureValue

      result shouldBe Right(AutoEnrolment)
      verify(mockSessionCache).saveGroupEnrolment(
        meq(EnrolmentResponse("HMRC-CUS-ORG", "Activated", List(KeyValue("EORINumber", "GB123456463324"))))
      )(any())
    }

    "return EnrolmentExistsUser for users with existing enrolment" in {
      val atarEnrolment = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB123456789123")

      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(
        Future.successful(List.empty[EnrolmentResponse])
      )
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(
        Future.successful(Some(ExistingEori("GB123456789123", "HMRC-ATAR-ORG")))
      )
      when(mockSessionCache.saveEori(any[Eori])(any())).thenReturn(Future.successful(true))

      val user   = loggedInUser(Set(atarEnrolment))
      val result = service.getJourney(user, groupId, atarService).futureValue

      result shouldBe Left(EnrolmentExistsUser)
      verify(mockSessionCache).saveEori(meq(Eori("GB123456789123")))(any())
    }

    "return EnrolmentExistsUser for users with other enrolment assigned to their group" in {
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(
        Future.successful(List(groupEnrolment(cdsService).get))
      )
      when(mockEnrolmentStoreProxyService.isEnrolmentInUse(any(), any())(any())).thenReturn(
        Future.successful(Some(ExistingEori("GB123456789123", "HMRC-CUS-ORG")))
      )
      when(mockSessionCache.saveEori(any[Eori])(any())).thenReturn(Future.successful(true))

      val user   = loggedInUser(Set.empty[Enrolment])
      val result = service.getJourney(user, groupId, atarService).futureValue

      result shouldBe Left(EnrolmentExistsUser)
      verify(mockSessionCache).saveEori(meq(Eori("GB123456789123")))(any())
    }

    "return EnrolmentExistsGroup for users who have enrolment assigned to their group" in {
      when(groupEnrolmentExtractor.groupIdEnrolments(any())(any())).thenReturn(
        Future.successful(List(groupEnrolment(atarService).get))
      )

      val user   = loggedInUser(Set.empty[Enrolment])
      val result = service.getJourney(user, groupId, atarService).futureValue

      result shouldBe Left(EnrolmentExistsGroup)
    }
  }
}
