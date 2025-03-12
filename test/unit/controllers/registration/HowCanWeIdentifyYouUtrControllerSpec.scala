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

import common.pages.SubscribeHowCanWeIdentifyYouPage
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.HowCanWeIdentifyYouUtrController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{
  AddressDetailsSubscriptionFlowPage,
  HowCanWeIdentifyYouSubscriptionFlowPage,
  SubscriptionFlowInfo
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.how_can_we_identify_you_utr
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HowCanWeIdentifyYouUtrControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector                    = mock[AuthConnector]
  private val mockAuthAction                       = authAction(mockAuthConnector)
  private val mockSubscriptionBusinessService      = mock[SubscriptionBusinessService]
  private val mockSubscriptionFlowManager          = mock[SubscriptionFlowManager]
  private val mockRequestSessionData               = mock[RequestSessionData]
  private val mockSubscriptionDetailsHolderService = mock[SubscriptionDetailsService]

  private val howCanWeIdentifyYouView = instanceOf[how_can_we_identify_you_utr]

  private val controller = new HowCanWeIdentifyYouUtrController(
    mockAuthAction,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    mockRequestSessionData,
    mcc,
    howCanWeIdentifyYouView,
    mockSubscriptionDetailsHolderService
  )

  override def beforeEach(): Unit = {
    super.beforeEach()

    Mockito.reset(mockSubscriptionDetailsHolderService)

    when(mockSubscriptionDetailsHolderService.cacheCustomsId(any[CustomsId])(any[Request[AnyContent]]))
      .thenReturn(Future.successful(()))
    when(mockSubscriptionBusinessService.getCachedCustomsId(any[Request[AnyContent]]))
      .thenReturn(Future.successful(None))
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(
      Some(CdsOrganisationType.Company)
    )

    when(
      mockSubscriptionFlowManager.stepInformation(ArgumentMatchers.eq(HowCanWeIdentifyYouSubscriptionFlowPage))(
        any[Request[AnyContent]]
      )
    ).thenReturn(SubscriptionFlowInfo(3, 5, AddressDetailsSubscriptionFlowPage))
  }

  "Loading the page" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(atarService))

    "show the form without errors" in {
      showForm(Map.empty) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe empty
      }
    }

    "populate the form values when session cache is having Nino details" in {
      reviewForm(Map.empty, customsId = Utr("1111111111k")) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.getElementValue("//*[@id='utr']") shouldBe "1111111111K"
      }
    }
  }
  "Loading the page on review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.createForm(atarService))

    "show the form without errors" in {
      reviewForm(Map.empty, customsId = Utr("1111111111K")) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe empty
      }
    }

  }

  "Submitting the form" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(isInReviewMode = false, atarService)
    )

    "give a page level error when utr not provided" in {
      submitForm(Map("utr" -> "")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.matching-error.business-details.utr.isEmpty"
        )
      }
    }

    "give a page and field level error when a utr of the wrong length is provided" in {
      submitForm(Map("utr" -> "12345678901")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.matching-error.utr.length"
        )
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorUtr) shouldBe s"Error: ${messages("cds.matching-error.utr.length")}"
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorNino) shouldBe empty
      }
    }

    "give a page and field level error when an invalid utr is provided" in {
      submitForm(Map("utr" -> "ABCDE12345")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.matching-error.utr.invalid"
        )
        page.getElementsText(SubscribeHowCanWeIdentifyYouPage.fieldLevelErrorUtr) shouldBe s"Error: ${messages("cds.matching-error.utr.invalid")}"
      }
    }

    "redirect to the 'Enter your business address' page when a valid utr is provided" in {
      submitForm(Map("utr" -> "1111111111k", "ninoOrUtrRadio" -> "nino")) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe "/customs-enrolment-services/atar/subscribe/address"
      }
    }

    "allow a UTR with spaces and lower case" in {
      submitForm(Map("utr" -> "21 08 83 45 03k")) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe "/customs-enrolment-services/atar/subscribe/address"
      }
      verify(mockSubscriptionDetailsHolderService).cacheCustomsId(meq(Utr("2108834503K")))(any())
    }

    "redirect to 'Check your details' page when valid Nino/ Utr is provided" in {
      submitFormInReviewMode(Map("utr" -> "2108834503")) { result =>
        status(result) shouldBe SEE_OTHER
        header(LOCATION, result).value shouldBe "/customs-enrolment-services/atar/subscribe/matching/review-determine"
      }
    }

    "redirect to 'Address Lookup' page" when {

      "user is during UK journey" in {

        when(mockRequestSessionData.isUKJourney(any())).thenReturn(true)

        submitForm(Map("utr" -> "2108834503")) { result =>
          status(result) shouldBe SEE_OTHER
          header(LOCATION, result).value shouldBe "/customs-enrolment-services/atar/subscribe/address-postcode"
        }
      }
    }
  }

  def showForm(form: Map[String, String], userId: String = defaultUserId)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    test(controller.createForm(atarService).apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)))
  }

  def submitForm(form: Map[String, String], userId: String = defaultUserId, isInReviewMode: Boolean = false)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(isInReviewMode, atarService)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  def submitFormInReviewMode(form: Map[String, String], userId: String = defaultUserId, isInReviewMode: Boolean = true)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      controller
        .submit(isInReviewMode, atarService)
        .apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form))
    )
  }

  def reviewForm(form: Map[String, String], userId: String = defaultUserId, customsId: CustomsId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockSubscriptionBusinessService.getCachedCustomsId(any[Request[AnyContent]]))
      .thenReturn(Future.successful(Some(customsId)))

    test(controller.reviewForm(atarService).apply(SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)))
  }

}
