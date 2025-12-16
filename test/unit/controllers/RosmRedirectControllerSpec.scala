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

package unit.controllers.registration

import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}
import util.ControllerSpec
import util.builders.AuthActionMock
import uk.gov.hmrc.auth.core.AuthConnector
import util.builders.AuthBuilder._
import util.builders.SessionBuilder
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.RosmRedirectController
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.eori_exists_rosm
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import play.api.mvc.MessagesControllerComponents
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EnrolmentResponse, KeyValue}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RosmRedirectControllerSpec extends ControllerSpec with AuthActionMock {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockEnrolmentStoreProxyService = mock[EnrolmentStoreProxyService]

  val sut = new RosmRedirectController(
    authorise = mockAuthAction,
    eoriExistsView = instanceOf[eori_exists_rosm],
    enrolmentStoreProxyService = mockEnrolmentStoreProxyService,
    mcc = instanceOf[MessagesControllerComponents]
  )

  def application: Application = new GuiceApplicationBuilder()
    .overrides(bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser])
    .build()

  Seq(
    "/customs/register-for-cds",
    "/customs/subscribe-for-cds",
    "/customs/subscribe-for-cds/are-you-based-in-uk"
  ).foreach { url =>
    s"$url" should {
      "redirect the user to our start page" in {
        val appToTest = application
        running(appToTest) {
          val request = FakeRequest(GET, url)
          val result  = route(appToTest, request).get

          status(result) shouldEqual SEE_OTHER
          redirectLocation(result).get shouldEqual "/customs-enrolment-services/cds/subscribe"
        }
      }
    }

  }

  "/customs/register-for-cds/are-you-based-in-uk" should {
    "redirect the user to our start page if the user does not have an EORI" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)

      when(
        mockEnrolmentStoreProxyService
          .enrolmentsForGroup(any())(any())
      ).thenReturn(Future.successful(Nil))

      val result = sut.checkEoriNumber.apply(SessionBuilder.buildRequestWithSession(defaultUserId))
      status(result) shouldEqual SEE_OTHER
      redirectLocation(result).get shouldEqual "/customs-enrolment-services/cds/subscribe"
    }

    "disply the EORI exists screen if the user has an EORI" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector, cdsEnrolmentId = Some("GB123456789012"))

      when(
        mockEnrolmentStoreProxyService
          .enrolmentsForGroup(any())(any())
      ).thenReturn(
        Future.successful(
          List(EnrolmentResponse("HMRC-CUS-ORG", "Active", List(KeyValue("EORINumber", "GB123456789012"))))
        )
      )

      val result = sut.checkEoriNumber.apply(SessionBuilder.buildRequestWithSession(defaultUserId))
      status(result) shouldEqual OK
    }

  }

}
