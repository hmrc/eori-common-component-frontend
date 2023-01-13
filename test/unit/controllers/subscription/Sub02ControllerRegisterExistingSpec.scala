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

import common.pages.RegistrationCompletePage
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.Sub02Controller
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.ResponseCommon
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.migration_success
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.AuthBuilder._
import util.builders.{AuthActionMock, SessionBuilder}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class Sub02ControllerRegisterExistingSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {

  private val mockAuthConnector              = mock[AuthConnector]
  private val mockAuthAction                 = authAction(mockAuthConnector)
  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockSessionCache               = mock[SessionCache]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]

  private val migrationSuccessView = instanceOf[migration_success]

  private val subscriptionController = new Sub02Controller(
    mockAuthAction,
    mockRequestSessionData,
    mockSessionCache,
    mockSubscriptionDetailsService,
    mcc,
    migrationSuccessView
  )(global)

  val eoriNumberResponse: String     = "EORI-Number"
  val formBundleIdResponse: String   = "Form-Bundle-Id"
  val processingDateResponse: String = "19 April 2018"

  val statusReceived = "Received"
  val statusReview   = "Review"
  val statusDecision = "Decision"

  val emulatedFailure = new UnsupportedOperationException("Emulated service call failure.")

  override protected def afterEach(): Unit = {
    reset(mockAuthConnector, mockSessionCache)

    super.afterEach()
  }

  private def stubRegisterWithEoriAndIdResponse(outcomeType: String = "PASS"): RegisterWithEoriAndIdResponse = {
    val processingDate = LocalDateTime.now()
    val responseCommon = ResponseCommon(status = "OK", processingDate = processingDate)
    val trader         = Trader(fullName = "Name", shortName = "nt")
    val establishmentAddress =
      EstablishmentAddress(streetAndNumber = "Street", city = "city", postalCode = Some("NE1 1BG"), countryCode = "GB")
    val responseData: ResponseData = ResponseData(
      SAFEID = "SafeID123",
      trader = trader,
      establishmentAddress = establishmentAddress,
      hasInternetPublication = true,
      startDate = "2018-01-01",
      dateOfEstablishmentBirth = Some("018-01-01")
    )
    val registerWithEoriAndIdResponseDetail = RegisterWithEoriAndIdResponseDetail(
      outcome = Some(outcomeType),
      caseNumber = Some("case no 1"),
      responseData = Some(responseData)
    )
    RegisterWithEoriAndIdResponse(responseCommon, Some(registerWithEoriAndIdResponseDetail))
  }

  "clicking on the register button" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      subscriptionController.migrationEnd(atarService)
    )

    "allow authenticated users to access the regExistingEnd page" in {

      invokeRegExistingEndPageWithAuthenticatedUser() {
        when(mockRequestSessionData.selectedUserLocation(any())).thenReturn(Some(UserLocation.Uk))

        when(mockSessionCache.registerWithEoriAndIdResponse(any[Request[AnyContent]]))
          .thenReturn(Future.successful(stubRegisterWithEoriAndIdResponse()))
        when(mockSessionCache.remove(any[Request[AnyContent]])).thenReturn(Future.successful(true))
        when(mockSessionCache.saveSub02Outcome(any[Sub02Outcome])(any[Request[AnyContent]]))
          .thenReturn(Future.successful(true))

        val mockSubscribeOutcome = mock[Sub02Outcome]
        when(mockSessionCache.sub02Outcome(any[Request[AnyContent]])).thenReturn(
          Future.successful(mockSubscribeOutcome)
        )
        when(mockSubscribeOutcome.processedDate).thenReturn("22 May 2016")
        when(mockSubscribeOutcome.eori).thenReturn(Some("ZZZ1ZZZZ23ZZZZZZZ"))
        when(mockSubscribeOutcome.fullName).thenReturn("Name")

        result =>
          status(result) shouldBe OK
          val page = CdsPage(contentAsString(result))
          page.title should startWith("Subscription request received")
          page.getElementsText(
            RegistrationCompletePage.pageHeadingXpath
          ) shouldBe "Subscription request received for Name"
          page.getElementsText(RegistrationCompletePage.activeFromXpath) shouldBe "on 22 May 2016"
          page.getElementsText(RegistrationCompletePage.eoriNumberXpath) shouldBe "ZZZ1ZZZZ23ZZZZZZZ"

          page.getElementsText(RegistrationCompletePage.additionalInformationXpath) should include(
            "We will send you an email when your subscription is ready to use. This can take up to 2 hours."
          )
          page.getElementsText(RegistrationCompletePage.DownloadEoriLinkXpath) should include(
            "Download an accessible text file with your registration details (1 kb)"
          )
          page.getElementsHref(RegistrationCompletePage.DownloadEoriLinkXpath) should endWith(
            "/customs-enrolment-services/atar/subscribe/download/text"
          )
          page.elementIsPresent(RegistrationCompletePage.LeaveFeedbackLinkXpath) shouldBe true
          page.getElementsText(RegistrationCompletePage.LeaveFeedbackLinkXpath) should include(
            "What did you think of this service?"
          )
          page.getElementsHref(RegistrationCompletePage.LeaveFeedbackLinkXpath) should endWith(
            "/feedback/eori-common-component-subscribe-atar"
          )
      }
    }
  }

  "SUB02Controller" should {

    "redirect user to the completed enrolment page" when {

      "enrolment happened immediately" in {

        val atarEnrolment =
          Enrolment("HMRC-ATAR-ORG", Seq(EnrolmentIdentifier("EORINumber", "GB123456789012")), "Activated")

        withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(atarEnrolment))

        val result = subscriptionController.migrationEnd(atarService)(getRequest)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe "/customs-enrolment-services/atar/subscribe/completed-enrolment"
      }
    }
  }

  def invokeRegExistingEndPageWithAuthenticatedUser(userId: String = defaultUserId)(test: Future[Result] => Any) {
    withAuthorisedUser(userId, mockAuthConnector)
    test(
      subscriptionController.migrationEnd(atarService).apply(
        SessionBuilder.buildRequestWithSessionAndPath("/atar/subscribe", userId)
      )
    )
  }

}
