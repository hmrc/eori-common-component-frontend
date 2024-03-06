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

package unit.controllers.subscription

import common.pages.subscription.ConfirmContactAddressPage
import common.support.testdata.subscription.BusinessDatesOrganisationTypeTables
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc._
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.ConfirmContactAddressController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ConfirmContactAddressController.submit
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ConfirmContactAddressSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.confirm_contact_address
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder
import util.builders.SubscriptionContactDetailsFormBuilder.contactDetailsModel

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConfirmContactAddressControllerSpec
    extends SubscriptionFlowTestSupport with BusinessDatesOrganisationTypeTables with BeforeAndAfterEach
    with SubscriptionFlowCreateModeTestSupport {
  protected override val formId: String = ConfirmContactAddressPage.formId

  protected override def submitInCreateModeUrl: String =
    submit(atarService).url

  private val mockRequestSessionData    = mock[RequestSessionData]
  private val confirmContactDetailsView = instanceOf[confirm_contact_address]

  private val controller = new ConfirmContactAddressController(
    mockAuthAction,
    mcc,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    confirmContactDetailsView
  )

  val yesForm: Map[String, String]   = Map("yes-no-answer" -> "true")
  val noForm: Map[String, String]    = Map("yes-no-answer" -> "false")
  val emptyForm: Map[String, String] = Map("yes-no-answer" -> " ")

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(
      mockSubscriptionBusinessService
        .cachedContactDetailsModel(any[Request[_]])
    ).thenReturn(Future.successful(Some(contactDetailsModel)))
    when(mockSubscriptionBusinessService.contactAddress(any[Request[_]]))
      .thenReturn(Future.successful(Some(ConfirmContactAddressPage.filledValues)))
    setupMockSubscriptionFlowManager(ConfirmContactAddressSubscriptionFlowPage)
  }

  override protected def afterEach(): Unit = {
    reset(mockSubscriptionBusinessService)
    reset(mockSubscriptionFlowManager)
    reset(mockRequestSessionData)

    super.afterEach()
  }

  "Reviewing the details" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.displayPage(atarService))

    "display title as 'Is this your contact address?'" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Is this your contact address?")
        status(result) shouldBe OK
      }
    }

    "display hint correctly" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          ConfirmContactAddressPage.hintTextXpath
        ) shouldBe "This is the address we will use to contact you about your application"
      }
    }

    "display all fields from the cache" in {

      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(ConfirmContactAddressPage.addressTextXpath) shouldBe strim(
          """Line 1 Line 2 Town Region SE28 1AA United Kingdom""".stripMargin
        )
      }
    }

    "redirect to contact address page" when {

      "contact address cache returns None during page load" in {

        when(mockSubscriptionBusinessService.contactAddress(any[Request[_]]))
          .thenReturn(Future.successful(None))

        showCreateForm() { result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/customs-enrolment-services/atar/subscribe/contact-address")
        }
      }

      "contact address cache returns None during submit" in {

        when(mockSubscriptionBusinessService.contactAddress(any[Request[_]]))
          .thenReturn(Future.successful(None))

        submitForm(yesForm) { result =>
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/customs-enrolment-services/atar/subscribe/contact-address")
        }
      }
    }

    "display the back link" in {
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }
  }

  "Selecting Yes" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.displayPage(atarService))

    "redirect to next screen" in {
      submitForm(yesForm) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("next-page-url")
      }
    }
  }

  "Selecting No" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.displayPage(atarService))

    "redirect to contact address screen" in {
      submitForm(noForm) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/customs-enrolment-services/atar/subscribe/contact-address")
      }
    }
  }
  "The Yes No WrongAddress Radio Button " should {
    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.displayPage(atarService))
    "display a relevant error if nothing is chosen" in {
      submitForm(emptyForm) { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          ConfirmContactAddressPage.pageLevelErrorSummaryListXPath
        ) shouldBe "Select yes if this address is correct"
        page.getElementsText(
          ConfirmContactAddressPage.fieldLevelErrorYesNoAnswer
        ) shouldBe "Error: Select yes if this address is correct"
      }
    }
  }

  private def showCreateForm(userId: String = defaultUserId)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.displayPage(atarService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def submitForm(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.submit(atarService)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)))
  }

}
