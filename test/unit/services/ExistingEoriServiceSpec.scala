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

package unit.services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.{Assertion, BeforeAndAfterEach}
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, Enrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.GroupEnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{EnrolmentService, MissingEnrolmentException}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.{ExistingEoriService, FailedEnrolmentException}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{eori_enrol_success, has_existing_eori}
import uk.gov.hmrc.http.HeaderCarrier
import unit.controllers.CdsPage
import util.ControllerSpec
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExistingEoriServiceSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val mockAuthConnector = mock[AuthConnector]

  private val mockEnrolmentService = mock[EnrolmentService]

  private val mockSessionCache = mock[SessionCache]

  private val groupEnrolmentExtractor = mock[GroupEnrolmentExtractor]

  private val hasExistingEoriView = instanceOf[has_existing_eori]

  private val eoriEnrolSuccessView = instanceOf[eori_enrol_success]

  private val cdsEnrolment = Enrolment("HMRC-CUS-ORG").withIdentifier("EORINumber", "GB123456463324")

  private val groupEORI = "GB435474553564"

  private val atarEnrolment = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB134123")

  private val gvmsEnrolment = Enrolment("HMRC-GVMS-ORG").withIdentifier("EORINumber", "GB13412345")

  private val route1Enrolment = Enrolment("HMRC-CTS-ORG").withIdentifier("EORINumber", "GB13412346")

  private def loggedInUser(enrolments: Set[Enrolment]) =
    LoggedInUserWithEnrolments(None, None, Enrolments(enrolments), None, None, "credId")

  private val controller =
    new ExistingEoriService(mockSessionCache, hasExistingEoriView, mockEnrolmentService, eoriEnrolSuccessView)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector)
    reset(mockEnrolmentService)
    reset(mockSessionCache)
    reset(groupEnrolmentExtractor)
    userHasGroupEnrolmentToCds
    userDoesNotHaveGroupEnrolmentToService
  }

  "ExistingEORIService onDisplay" should {

    "throw exception when user does not have existing CDS enrolment" in {
      userDoesNotHaveGroupEnrolmentToCds

      implicit val user: LoggedInUserWithEnrolments = loggedInUser(Set.empty[Enrolment])
      implicit val req = SessionBuilder.buildRequestWithSessionAndPath("/atar/subscribe/", defaultUserId)

      val caught = intercept[DataUnavailableException] {
        await(controller.onDisplay(atarService))
      }

      caught.message shouldBe "No existing EORI found"
    }

    "display page with user eori" in {
      displayPage(atarService, Some(cdsEnrolment)) { result =>
        status(Future.successful(result)) shouldBe OK
        val page = CdsPage(contentAsString(Future.successful(result)))
        page.title() should startWith("We’ll subscribe you to this service with EORI number")
        page.h1() shouldBe s"We’ll subscribe you to this service with EORI number ${cdsEnrolment.identifiers.headOption.map(_.value).getOrElse("")}"
      }
    }

    "have redirection to Email Controller Check for CDS subscription" in {
      displayPage(cdsService, Some(cdsEnrolment)) { result =>
        status(Future.successful(result)) shouldBe OK
        val page = CdsPage(contentAsString(Future.successful(result)))
        page.formAction("form") shouldBe "/customs-enrolment-services/cds/subscribe/autoenrolment/check-user"
      }
    }

    "have redirection to Enrolment Action for non CDS subscription" in {
      displayPage(atarService, Some(cdsEnrolment)) { result =>
        status(Future.successful(result)) shouldBe OK
        val page = CdsPage(contentAsString(Future.successful(result)))
        page.formAction("form") shouldBe "/customs-enrolment-services/atar/subscribe/check-existing-eori"
      }
    }

    "pick the first enrollment apart from CDS and display correct EORI if the user has other enrollments and no CDS enrolment" in {
      displayPage(atarService, cdsEnrolmentId = None, otherEnrolments = Set(gvmsEnrolment, route1Enrolment)) { result =>
        status(Future.successful(result)) shouldBe OK
        val page = CdsPage(contentAsString(Future.successful(result)))
        page.title() should startWith("We’ll subscribe you to this service with EORI number")

      }
    }

    "pick the CDS and display correct EORI if the user has CDS enrolment along with other enrollments" in {
      displayPage(atarService, Some(cdsEnrolment), otherEnrolments = Set(gvmsEnrolment, route1Enrolment)) { result =>
        status(Future.successful(result)) shouldBe OK
        val page = CdsPage(contentAsString(Future.successful(result)))
        page.title() should startWith("We’ll subscribe you to this service with EORI number")

      }
    }

    "display page with group eori" in {
      displayPage(atarService, None) { result =>
        status(Future.successful(result)) shouldBe OK
        val page = CdsPage(contentAsString(Future.successful(result)))
        page.title() should startWith("We’ll subscribe you to this service with EORI number")

      }
    }

    "display page with group eori having atar enrolment" in {
      userHasGroupEnrolmentToATAR
      displayPage(gvmsService, None) { result =>
        status(Future.successful(result)) shouldBe OK
        val page = CdsPage(contentAsString(Future.successful(result)))
        page.title() should startWith("We’ll subscribe you to this service with EORI number")

      }
    }
  }

  "ExistingEORIService onEnrol" should {
    "redirect to confirmation page on success" in {
      enrol(atarService, NO_CONTENT) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/check-existing-eori/confirmation")
      }
    }

    "redirect to email page when enrolWithExistingEnrolment fails for group having CDS enrolment with missing known facts" in {
      enrolMissingEnrolment(atarService) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/longjourney/check-user")
      }
    }

    "redirect to email page when enrolWithExistingEnrolment fails for user having CDS enrolment with missing known facts" in {
      enrolMissingEnrolmentForUser(atarService) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/longjourney/check-user")
      }
    }

    "redirect to email page when enrolWithExistingEnrolment fails for group having ATAR enrolment with missing known facts" in {
      userHasGroupEnrolmentToATAR
      enrolMissingEnrolment(gvmsService) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/gagmr/subscribe/longjourney/check-user")
      }
    }

    "redirect to email page when enrolWithExistingEnrolment fails for user having ATAR enrolment with missing known facts" in {
      userDoesNotHaveGroupEnrolmentToService
      enrolMissingEnrolmentForUser(gvmsService) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/gagmr/subscribe/longjourney/check-user")
      }
    }

    "throw exception on failure" in {
      implicit val user: LoggedInUserWithEnrolments = loggedInUser(Set())
      implicit val req                              = SessionBuilder.buildRequestWithSession(defaultUserId)

      when(mockEnrolmentService.enrolWithExistingEnrolment(any[ExistingEori], any[Service])(any()))
        .thenReturn(Future(INTERNAL_SERVER_ERROR))

      val caught = intercept[FailedEnrolmentException] {
        await(controller.onEnrol(atarService))
      }

      caught.getMessage should endWith(INTERNAL_SERVER_ERROR.toString)
    }
  }

  "Has Existing EORI Controller enrol confirmation page" should {
    "throw exception when user does not have existing CDS enrolment" in {
      userDoesNotHaveGroupEnrolmentToCds

      intercept[DataUnavailableException](enrolSuccess(atarService)(result => status(result))).getMessage shouldBe
        "No existing EORI found"
    }

    "return Ok 200 when enrol confirmation page is requested" in {
      enrolSuccess(atarService, Some(cdsEnrolment)) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Subscription complete")
      }
    }
  }

  private def displayPage(
    service: Service,
    cdsEnrolmentId: Option[Enrolment] = None,
    otherEnrolments: Set[Enrolment] = Set.empty
  )(test: Result => Assertion) = {
    implicit val user: LoggedInUserWithEnrolments = loggedInUser(otherEnrolments ++ cdsEnrolmentId)
    implicit val req = SessionBuilder.buildRequestWithSessionAndPath("/atar/subscribe/", defaultUserId)

    test(await(controller.onDisplay(service)))
  }

  private def enrol(service: Service, responseStatus: Int)(test: Future[Result] => Assertion) = {
    when(mockEnrolmentService.enrolWithExistingEnrolment(any[ExistingEori], any[Service])(any())).thenReturn(
      Future(responseStatus)
    )
    implicit val user: LoggedInUserWithEnrolments = loggedInUser(Set())
    implicit val req                              = SessionBuilder.buildRequestWithSession(defaultUserId)

    test(controller.onEnrol(service))
  }

  private def enrolMissingEnrolment(service: Service)(test: Future[Result] => Any) = {
    when(mockEnrolmentService.enrolWithExistingEnrolment(any[ExistingEori], any[Service])(any())).thenReturn(
      Future.failed(MissingEnrolmentException("EORI"))
    )
    implicit val user: LoggedInUserWithEnrolments = loggedInUser(Set())
    implicit val req                              = SessionBuilder.buildRequestWithSession(defaultUserId)
    test(controller.onEnrol(service))
  }

  private def enrolMissingEnrolmentForUser(service: Service)(test: Future[Result] => Any) = {
    when(mockEnrolmentService.enrolWithExistingEnrolment(any[ExistingEori], any[Service])(any())).thenReturn(
      Future.failed(MissingEnrolmentException("EORI"))
    )
    implicit val user: LoggedInUserWithEnrolments = loggedInUser(Set(atarEnrolment))
    implicit val req                              = SessionBuilder.buildRequestWithSession(defaultUserId)
    test(controller.onEnrol(service))
  }

  private def enrolSuccess(service: Service, cdsEnrolmentId: Option[Enrolment] = None)(test: Future[Result] => Any) = {
    implicit val user: LoggedInUserWithEnrolments = loggedInUser(Set.empty[Enrolment] ++ cdsEnrolmentId)
    implicit val req                              = SessionBuilder.buildRequestWithSession(defaultUserId)
    test(controller.onEnrolSuccess(service))
  }

  private def userDoesNotHaveGroupEnrolmentToCds = {
    when(mockSessionCache.groupEnrolment(any[Request[AnyContent]]))
      .thenReturn(Future.successful(EnrolmentResponse(Service.cds.enrolmentKey, "Activated", List.empty)))

    when(mockSessionCache.saveEori(any())(any[Request[AnyContent]]()))
      .thenReturn(Future.successful(true))
  }

  private def userHasGroupEnrolmentToCds = {
    when(mockSessionCache.groupEnrolment(any[Request[AnyContent]]))
      .thenReturn(
        Future.successful(
          EnrolmentResponse(Service.cds.enrolmentKey, "Activated", List(KeyValue("EORINumber", groupEORI)))
        )
      )

    when(mockSessionCache.saveEori(any())(any[Request[AnyContent]]()))
      .thenReturn(Future.successful(true))
  }

  private def userHasGroupEnrolmentToATAR =
    when(mockSessionCache.groupEnrolment(any[Request[AnyContent]]))
      .thenReturn(
        Future.successful(
          EnrolmentResponse(atarService.enrolmentKey, "Activated", List(KeyValue("EORINumber", groupEORI)))
        )
      )

  private def userDoesNotHaveGroupEnrolmentToService =
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any())).thenReturn(Future.successful(false))

}
