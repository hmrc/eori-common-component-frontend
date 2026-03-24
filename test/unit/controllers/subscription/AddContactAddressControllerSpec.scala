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

package unit.controllers.subscription

import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{Request, Result}
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.{
  AddContactAddressController,
  SubscriptionFlowManager
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.YesNo
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{
  ContactAddressSubscriptionFlowPage,
  ReviewDetailsPageSubscription,
  SubscriptionFlowInfo
}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.add_contact_address
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AddContactAddressControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val yesNoInputName = YesNo.yesNoAnswer
  private val answerYes      = "true"
  private val answerNo       = "false"

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val mockSubscriptionFlowManager    = mock[SubscriptionFlowManager]

  private val addContactAddressView = instanceOf[add_contact_address]

  private val controller = new AddContactAddressController(
    mockAuthAction,
    mcc,
    mockSubscriptionDetailsService,
    mockSubscriptionFlowManager,
    addContactAddressView
  )

  override def beforeEach(): Unit = {
    super.beforeEach()

    when(mockSubscriptionDetailsService.cachedAddressContactDetails()(any[Request[_]]))
      .thenReturn(Future.successful(None))

    when(mockSubscriptionDetailsService.cacheAddContactAddressDetails(any())(any()))
      .thenReturn(Future.successful(()))

    when(mockSubscriptionFlowManager.stepInformation(any())(any()))
      .thenReturn(SubscriptionFlowInfo(1, 2, ContactAddressSubscriptionFlowPage))

    when(mockSubscriptionDetailsService.clearContactAddress()(any()))
      .thenReturn(Future.successful(()))
  }

  override def afterEach(): Unit = {
    reset(mockSubscriptionFlowManager, mockSubscriptionDetailsService)
    super.afterEach()
  }

  "Displaying the Add Contact Address page" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.form(isInReviewMode = false, cdsService)
    )

    "display title as 'Do you want to add a contact address?'" in {
      showForm(isInReviewMode = false) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Do you want to add a contact address?")
      }
    }

    "display correctly in review mode" in {
      showForm(isInReviewMode = true) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Do you want to add a contact address?")
      }
    }

  }

  "Submitting the Add Contact Address form" should {

    "redirect to Contact Address page when user selects Yes" in {
      submitForm(Map(yesNoInputName -> answerYes), isInReviewMode = false) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).value should endWith(ContactAddressSubscriptionFlowPage.url(cdsService))
        verify(mockSubscriptionDetailsService).cacheAddContactAddressDetails(any())(any())
        verify(mockSubscriptionDetailsService, never()).clearContactAddress()(any())
      }
    }

    "redirect to Check Your Details page when user selects No" in {
      submitForm(Map(yesNoInputName -> answerNo), isInReviewMode = false) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).value should endWith(
          "/customs-enrolment-services/cds/subscribe/matching/check-your-details"
        )
        verify(mockSubscriptionDetailsService).clearContactAddress()(any())
      }
    }

    "redirect to Check Your Details page when user selects No in review mode" in {
      submitForm(Map(yesNoInputName -> answerNo), isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).value should endWith(
          "/customs-enrolment-services/cds/subscribe/matching/check-your-details"
        )
      }
    }

    "display error message when no answer is provided" in {
      submitForm(Map.empty, isInReviewMode = false) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.title() should include("Do you want to add a contact address?")
        page.getElementsText("//*[@class='govuk-error-message']") should include(
          "Select yes if you want to add a contact address"
        )
      }
    }

    "submit correctly in review mode when Yes selected & was previously selected" in {
      when(mockSubscriptionDetailsService.cachedContactDetails()(any[Request[_]])).thenReturn(Future.successful(
        Some(ContactAddressModel("line 1", None, "line 3", None, None, "France"))
      ))
      submitForm(Map(yesNoInputName -> answerYes), isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).value should endWith(ReviewDetailsPageSubscription.url(cdsService))
      }
    }

    "submit correctly in review mode when Yes selected but was not previously selected" in {
      when(mockSubscriptionDetailsService.cachedContactDetails()(any[Request[_]])).thenReturn(Future.successful(None))
      submitForm(Map(yesNoInputName -> answerYes), isInReviewMode = true) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).value should endWith(ContactAddressSubscriptionFlowPage.url(cdsService))
      }
    }
  }

  private def showForm(isInReviewMode: Boolean = false)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    val result = controller.form(isInReviewMode, cdsService)
      .apply(SessionBuilder.buildRequestWithSession(defaultUserId))

    test(result)
  }

  private def submitForm(
    formValues: Map[String, String],
    isInReviewMode: Boolean = false
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)

    val result = controller.submit(isInReviewMode, cdsService)(
      SessionBuilder.buildRequestWithSessionAndFormValues(defaultUserId, formValues)
    )

    test(result)
  }

}
