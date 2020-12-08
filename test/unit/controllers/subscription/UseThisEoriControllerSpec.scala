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

package unit.controllers.subscription

import common.pages.subscription.{EoriNumberPage, SubscriptionContactDetailsPage}
import common.support.testdata.subscription.BusinessDatesOrganisationTypeTables
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.UseThisEoriController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.EoriNumberSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.use_this_eori
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UseThisEoriControllerSpec
    extends SubscriptionFlowCreateModeTestSupport with BusinessDatesOrganisationTypeTables with BeforeAndAfterEach
    with SubscriptionFlowReviewModeTestSupport {
  protected override val formId: String = EoriNumberPage.formId

  protected override def submitInCreateModeUrl: String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhatIsYourEoriController
      .submit(isInReviewMode = false, atarService)
      .url

  protected override def submitInReviewModeUrl: String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhatIsYourEoriController
      .submit(isInReviewMode = true, atarService)
      .url

  private val mockRequestSessionData = mock[RequestSessionData]
  private val useThisEoriView        = instanceOf[use_this_eori]

  private val controller = new UseThisEoriController(
    mockAuthAction,
    mockSubscriptionFlowManager,
    mockSubscriptionDetailsHolderService,
    mcc,
    useThisEoriView
  )

  val existingGroupEnrolment: EnrolmentResponse =
    EnrolmentResponse("HMRC-OTHER-ORG", "Active", List(KeyValue("EORINumber", "GB1234567890")))

  val existingEori = ExistingEori("GB1234567890", "HMRC-OTHER-ORG")

  override def beforeEach: Unit = {
    reset(mockSubscriptionFlowManager, mockSubscriptionDetailsHolderService)
    when(mockSubscriptionDetailsHolderService.cachedExistingEoriNumber(any[HeaderCarrier])).thenReturn(
      Future.successful(Some(existingEori))
    )
    setupMockSubscriptionFlowManager(EoriNumberSubscriptionFlowPage)
  }

  "Subscription Use This Eori Number" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.display(atarService))

    "display title as 'What is your GB EORI number?'" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Your GB Economic Operator Registration and Identification (EORI) number")
      }
    }

    "display the back link" in {
      showCreateForm(journey = Journey.Subscribe)(verifyBackLinkInCreateModeSubscribe)
    }

    "display the back link for subscribe user journey" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        verifyBackLinkIn(result)
      }
    }

    "display existing Eori Number" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(contentAsString(result))
        verifyExistinEoriNumber(page)
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm(journey = Journey.Subscribe) { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementValue(EoriNumberPage.continueButtonXpath) shouldBe "Confirm and continue"
      }
    }

  }

  "submitting the form all organisation types" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.submit(atarService))

    "redirect to next screen" in {
      submitForm()(verifyRedirectToNextPageIn(_)("next-page-url"))
    }

  }

  private def submitForm(userId: String = defaultUserId, userSelectedOrgType: Option[CdsOrganisationType] = None)(
    test: Future[Result] => Any
  ) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]])).thenReturn(userSelectedOrgType)
    when(mockSubscriptionDetailsHolderService.cacheEoriNumber(any())(any())).thenReturn(Future.successful(()))

    test(controller.submit(atarService)(SessionBuilder.buildRequestWithSessionAndFormValues(userId, Map.empty)))
  }

  private def showCreateForm(
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None,
    journey: Journey.Value
  )(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(userSelectedOrganisationType)

    test(controller.display(atarService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyExistinEoriNumber(page: CdsPage): Unit =
    page.getElementsText("//*[@class='eori-number']") should be(existingEori.id)

  private def verifyBackLinkIn(result: Result) = {
    val page = CdsPage(contentAsString(result))
    page.getElementAttributeHref(SubscriptionContactDetailsPage.backLinkXPath) shouldBe previousPageUrl
  }

  private def verifyRedirectToNextPageIn(result: Result)(linkToVerify: String) = {
    status(result) shouldBe SEE_OTHER
    result.header.headers(LOCATION) should endWith(linkToVerify)
  }

}
