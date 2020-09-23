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

package unit.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Result
import play.api.test.Helpers.{INTERNAL_SERVER_ERROR, _}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{FailedEnrolmentException, HasExistingEoriController}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{eori_enrol_success, has_existing_eori}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HasExistingEoriControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {
  private val mockAuthConnector    = mock[AuthConnector]
  private val mockAuthAction       = authAction(mockAuthConnector)
  private val mockEnrolmentService = mock[EnrolmentService]

  private val hasExistingEoriView  = app.injector.instanceOf[has_existing_eori]
  private val eoriEnrolSuccessView = app.injector.instanceOf[eori_enrol_success]

  private val controller =
    new HasExistingEoriController(mockAuthAction, hasExistingEoriView, eoriEnrolSuccessView, mcc, mockEnrolmentService)

  override def beforeEach: Unit =
    reset(mockAuthConnector, mockEnrolmentService)

  "Has Existing EORI Controller display page" should {

    "throw exception when user does not have existing CDS enrolment" in {
      intercept[IllegalStateException](displayPage(Service.ATaR)(result => status(result))).getMessage should startWith(
        "No EORI found in enrolments"
      )
    }

    "return Ok 200 when displayPage method is requested" in {
      displayPage(Service.ATaR, Some("GB123456463324")) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should startWith("Your Government Gateway user ID is linked to an EORI")
      }
    }
  }

  "Has Existing EORI Controller enrol" should {

    "redirect to confirmation page on success" in {
      enrol(Service.ATaR, NO_CONTENT) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/check-existing-eori/confirmation")
      }
    }

    "throw exception on failure" in {
      intercept[FailedEnrolmentException](
        enrol(Service.ATaR, INTERNAL_SERVER_ERROR)(result => status(result))
      ).getMessage should endWith(INTERNAL_SERVER_ERROR.toString)
    }
  }

  "Has Existing EORI Controller enrol confirmation page" should {

    "throw exception when user does not have existing CDS enrolment" in {
      intercept[IllegalStateException](
        enrolSuccess(Service.ATaR)(result => status(result))
      ).getMessage should startWith("No EORI found in enrolments")
    }

    "return Ok 200 when enrol confirmation page is requested" in {
      enrolSuccess(Service.ATaR, Some("GB123456463324")) { result =>
        status(result) shouldBe OK
        val page = CdsPage(bodyOf(result))
        page.title should startWith("Application complete")
      }
    }
  }

  private def displayPage(service: Service, cdsEnrolmentId: Option[String] = None)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector, cdsEnrolmentId = cdsEnrolmentId)
    await(test(controller.displayPage(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def enrol(service: Service, responseStatus: Int)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(
      mockEnrolmentService.enrolWithExistingCDSEnrolment(any[LoggedInUserWithEnrolments], any[Service])(any())
    ).thenReturn(Future(responseStatus))
    await(test(controller.enrol(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def enrolSuccess(service: Service, cdsEnrolmentId: Option[String] = None)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector, cdsEnrolmentId = cdsEnrolmentId)
    await(test(controller.enrolSuccess(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

}
