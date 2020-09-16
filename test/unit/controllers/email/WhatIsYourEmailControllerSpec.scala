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

package unit.controllers.email

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.WhatIsYourEmailController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.InternalId
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.Save4LaterService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.email.what_is_your_email
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WhatIsYourEmailControllerSpec extends ControllerSpec with BeforeAndAfterEach {

  private val mockAuthConnector = mock[AuthConnector]

  private val mockSave4LaterService = mock[Save4LaterService]

  private val whatIsYourEmailView = app.injector.instanceOf[what_is_your_email]

  private val controller =
    new WhatIsYourEmailController(app, mockAuthConnector, mcc, whatIsYourEmailView, mockSave4LaterService)

  val email       = "test@example.com"
  val emailStatus = EmailStatus(email)

  val internalId = "InternalID"
  val jsonValue  = Json.toJson(emailStatus)
  val data       = Map(internalId -> jsonValue)
  val cacheMap   = CacheMap(internalId, data)

  val EmailFieldsMap            = Map("email" -> email)
  val unpopulatedEmailFieldsMap = Map("email" -> "")

  override def beforeEach: Unit = {
    when(mockSave4LaterService.fetchEmail(any[InternalId])(any[HeaderCarrier]))
      .thenReturn(Future.successful(Some(emailStatus)))

    when(
      mockSave4LaterService
        .saveEmail(any[InternalId], any[EmailStatus])(any[HeaderCarrier])
    ).thenReturn(Future.successful(()))
  }

  "What Is Your Email form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.createForm(Service.ATaR, Journey.Subscribe)
    )

    "display title as 'What is your email address'" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(bodyOf(result))
        page.title() should startWith("What is your email address?")
      }
    }
  }

  "What Is Your Email form" should {
    "be mandatory" in {
      submitFormInCreateMode(unpopulatedEmailFieldsMap, journey = Journey.Subscribe) { result =>
        status(result) shouldBe BAD_REQUEST
      }
    }

    "be restricted to 50 characters for email length" in {
      val maxEmail = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx@xxxxxxxxxx"
      submitFormInCreateMode(unpopulatedEmailFieldsMap ++ Map("email" -> maxEmail), journey = Journey.Subscribe) {
        result =>
          status(result) shouldBe BAD_REQUEST

      }
    }

    "be valid for correct email format" in {

      submitFormInCreateMode(EmailFieldsMap, journey = Journey.Subscribe) { result =>
        status(result) shouldBe SEE_OTHER
        result.header.headers("Location") should endWith(
          "/customs-enrolment-services/atar/subscribe/matching/check-your-email"
        )

      }
    }
  }

  private def submitFormInCreateMode(form: Map[String, String], userId: String = defaultUserId, journey: Journey.Value)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)
    val result =
      controller.submit(Service.ATaR, journey)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    test(result)
  }

  private def showCreateForm(userId: String = defaultUserId, journey: Journey.Value)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    val result = controller
      .createForm(Service.ATaR, journey)
      .apply(SessionBuilder.buildRequestWithSession(userId))
    test(result)
  }

}
