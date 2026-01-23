/*
 * Copyright 2026 HM Revenue & Customs
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

package unit.controllers.address

import common.pages.subscription.AddressPage
import common.support.testdata.subscription.BusinessDatesOrganisationTypeTables
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.Results.Status
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.AddressController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController.submit
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.AddressDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.AddressService
import unit.controllers.subscription.{
  SubscriptionFlowCreateModeTestSupport,
  SubscriptionFlowReviewModeTestSupport,
  SubscriptionFlowTestSupport
}
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.Future

class AddressControllerSpec
    extends SubscriptionFlowTestSupport with BusinessDatesOrganisationTypeTables with BeforeAndAfterEach
    with SubscriptionFlowCreateModeTestSupport with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = AddressPage.formId

  protected override def submitInCreateModeUrl: String =
    submit(isInReviewMode = false, atarService).url

  protected override def submitInReviewModeUrl: String =
    submit(isInReviewMode = true, atarService).url

  private val mockAddressService = mock[AddressService]
  private val controller         = new AddressController(mockAuthAction, mcc, mockAddressService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    setupMockSubscriptionFlowManager(AddressDetailsSubscriptionFlowPage)
  }

  override protected def afterEach(): Unit = {
    reset(mockAddressService)
    super.afterEach()
  }

  "Subscription Address Controller form in create mode" should {
    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(atarService))

    "Display successfully" in {
      when(mockAddressService.populateFormViewCached(any(), any())(any[Request[AnyContent]])).thenReturn(
        Future.successful(Status(OK))
      )
      showCreateForm(atarService) { result =>
        status(result) shouldBe OK
      }
    }
  }

  "Subscription Address Controller form in review mode" should {
    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewForm(atarService))

    "Display successfully" in {
      when(mockAddressService.populateReviewViewCached(any(), any())(any[Request[AnyContent]])).thenReturn(
        Future.successful(Status(SEE_OTHER))
      )
      submitFormInReviewMode(atarService) { result =>
        status(result) shouldBe SEE_OTHER
      }
    }
  }

  "Subscription Address Controller form" should {
    "Submit successfully" in {
      when(mockAddressService.handleFormDataAndRedirect(any(), any(), any())(any[Request[AnyContent]])).thenReturn(
        Future.successful(Status(SEE_OTHER))
      )
      submitForm(atarService) { result =>
        status(result) shouldBe SEE_OTHER
      }
    }
  }

  private def showCreateForm(service: Service)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(controller.createForm(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def submitFormInReviewMode(service: Service)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(controller.reviewForm(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId)))
  }

  private def submitForm(service: Service)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    test(
      controller.submit(isInReviewMode = false, service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))
    )
  }

}
