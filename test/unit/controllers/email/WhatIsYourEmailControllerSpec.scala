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

package unit.controllers.email

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.WhatIsYourEmailController
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.what_is_your_email
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WhatIsYourEmailControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector     = mock[AuthConnector]
  private val mockSave4LaterService = mock[Save4LaterService]
  private val mockAuthAction        = authAction(mockAuthConnector)
  private val whatIsYourEmailView   = instanceOf[what_is_your_email]

  private val controller =
    new WhatIsYourEmailController(mockAuthAction, mcc, whatIsYourEmailView, mockSave4LaterService)

  val email                    = "test@example.com"
  val emailStatus: EmailStatus = EmailStatus(Some(email))

  val EmailFieldsMap: Map[String, String]            = Map("email" -> email)
  val unpopulatedEmailFieldsMap: Map[String, String] = Map("email" -> "")

  override def beforeEach(): Unit = {
    when(mockSave4LaterService.fetchEmailForService(any(), any(), any())(any()))
      .thenReturn(Future.successful(Some(emailStatus)))

    when(mockSave4LaterService.saveEmailForService(any())(any(), any(), any())(any[HeaderCarrier]))
      .thenReturn(Future.successful(()))
  }

  "What Is Your Email form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.createForm(cdsService, subscribeJourneyShort)
    )

    "display title as 'What is your email address'" in {
      showCreateForm(journey = subscribeJourneyShort) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("What is your email address?")
      }
    }
  }

  "What Is Your Email form" should {
    "be mandatory" in {
      submitFormInCreateMode(unpopulatedEmailFieldsMap, journey = subscribeJourneyShort) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }

    "be restricted to 50 characters for email length" in {
      val maxEmail = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx@xxxxxxxxxx"
      submitFormInCreateMode(unpopulatedEmailFieldsMap ++ Map("email" -> maxEmail), journey = subscribeJourneyShort) {
        result =>
          status(result) shouldBe BAD_REQUEST

      }
    }

    "be valid for correct email format" in {
      submitFormInCreateMode(EmailFieldsMap, journey = subscribeJourneyShort) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value should endWith(
          "/customs-enrolment-services/cds/subscribe/autoenrolment/matching/check-your-email"
        )
      }
    }

    "be valid for correct email format (Long Journey)" in {
      submitFormInCreateMode(EmailFieldsMap, journey = subscribeJourneyLong) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value should endWith(
          "/customs-enrolment-services/cds/subscribe/longjourney/matching/check-your-email"
        )
      }
    }
  }

  private def submitFormInCreateMode(
    form: Map[String, String],
    userId: String = defaultUserId,
    journey: SubscribeJourney,
    service: Service = cdsService,
    controller: WhatIsYourEmailController = controller
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    val result =
      controller.submit(service, journey)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    test(result)
  }

  private def showCreateForm(userId: String = defaultUserId, journey: SubscribeJourney)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .createForm(cdsService, journey)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

}
