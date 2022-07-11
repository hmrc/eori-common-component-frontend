/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers.{INTERNAL_SERVER_ERROR, _}
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.GroupEnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.{FailedEnrolmentException, HasExistingEoriController}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EnrolmentResponse, ExistingEori, KeyValue}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{EnrolmentService, MissingEnrolmentException}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{eori_enrol_success, has_existing_eori}
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HasExistingEoriControllerSpec extends ControllerSpec with BeforeAndAfterEach with AuthActionMock {
  private val mockAuthConnector = mock[AuthConnector]

  private val mockAuthAction = authAction(mockAuthConnector)

  private val mockEnrolmentService = mock[EnrolmentService]

  private val mockSessionCache = mock[SessionCache]

  private val groupEnrolmentExtractor = mock[GroupEnrolmentExtractor]

  private val hasExistingEoriView = instanceOf[has_existing_eori]

  private val eoriEnrolSuccessView = instanceOf[eori_enrol_success]

  private val eoriElement = "//*[@id='eoriNum']"

  private val userEORI = "GB123456463324"

  private val groupEORI = "GB435474553564"

  private val atarEnrolment = Enrolment("HMRC-ATAR-ORG").withIdentifier("EORINumber", "GB134123")

  private val gvmsEnrolment = Enrolment("HMRC-GVMS-ORG").withIdentifier("EORINumber", "GB13412345")

  private val route1Enrolment = Enrolment("HMRC-CTS-ORG").withIdentifier("EORINumber", "GB13412346")

  private val controller = new HasExistingEoriController(
    mockAuthAction,
    hasExistingEoriView,
    eoriEnrolSuccessView,
    mcc,
    mockEnrolmentService,
    groupEnrolmentExtractor,
    mockSessionCache
  )

  override def beforeEach: Unit = {
    super.beforeEach()
    reset(mockAuthConnector, mockEnrolmentService, mockSessionCache, groupEnrolmentExtractor)
    userHasGroupEnrolmentToCds
    userDoesNotHaveGroupEnrolmentToService
  }

  "Has Existing EORI Controller display page" should {
    "throw exception when user does not have existing CDS enrolment" in {
      userDoesNotHaveGroupEnrolmentToCds

      intercept[DataUnavailableException](displayPage(atarService)(result => status(result))).getMessage shouldBe
        "No EORI found"
    }

    "redirect if user already has the requested service" in {
      displayPage(atarService, cdsEnrolmentId = None, otherEnrolments = Set(atarEnrolment)) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/enrolment-already-exists")
      }
    }

    "display page with user eori" in {
      displayPage(atarService, Some(userEORI)) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("Your Government Gateway user ID is linked to an EORI")

        page.getElementText(eoriElement) shouldBe userEORI
      }
    }

    "have redirection to Email Controller Check for CDS subscription" in {
      displayPage(cdsService, Some(userEORI)) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.formAction("form") shouldBe "/customs-enrolment-services/cds/subscribe/autoenrolment/check-user"
      }
    }

    "have redirection to Enrolment Action for non CDS subscription" in {
      displayPage(atarService, Some(userEORI)) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.formAction("form") shouldBe "/customs-enrolment-services/atar/subscribe/check-existing-eori"
      }
    }

    "pick the first enrollment apart from CDS and display correct EORI if the user has other enrollments and no CDS enrolment" in {
      displayPage(atarService, cdsEnrolmentId = None, otherEnrolments = Set(gvmsEnrolment, route1Enrolment)) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("Your Government Gateway user ID is linked to an EORI")

        page.getElementText(eoriElement) shouldBe "GB13412345"
      }
    }

    "pick the CDS and display correct EORI if the user has CDS enrolment along with other enrollments" in {
      displayPage(atarService, Some(userEORI), otherEnrolments = Set(gvmsEnrolment, route1Enrolment)) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("Your Government Gateway user ID is linked to an EORI")

        page.getElementText(eoriElement) shouldBe "GB123456463324"
      }
    }

    "display page with group eori" in {
      displayPage(atarService, None) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("Your Government Gateway user ID is linked to an EORI")

        page.getElementText(eoriElement) shouldBe groupEORI
      }
    }

    "display page with group eori having atar enrolment" in {
      userHasGroupEnrolmentToATAR
      displayPage(gvmsService, None) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("Your Government Gateway user ID is linked to an EORI")

        page.getElementText(eoriElement) shouldBe groupEORI
      }
    }
  }

  "Has Existing EORI Controller enrol" should {
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

    "redirect to enrolment exists if user has group enrolment to service" in {
      userHasGroupEnrolmentToService

      enrol(atarService, NO_CONTENT) { result =>
        status(result) shouldBe SEE_OTHER
        await(result).header.headers("Location") should endWith("/atar/subscribe/enrolment-already-exists-for-group")
      }
    }

    "throw exception on failure" in {
      intercept[FailedEnrolmentException](
        enrol(atarService, INTERNAL_SERVER_ERROR)(result => status(result))
      ).getMessage should endWith(INTERNAL_SERVER_ERROR.toString)
    }
  }

  "Has Existing EORI Controller enrol confirmation page" should {
    "throw exception when user does not have existing CDS enrolment" in {
      userDoesNotHaveGroupEnrolmentToCds

      intercept[DataUnavailableException](enrolSuccess(atarService)(result => status(result))).getMessage shouldBe
        "No EORI found"
    }

    "return Ok 200 when enrol confirmation page is requested" in {
      enrolSuccess(atarService, Some("GB123456463324")) { result =>
        status(result) shouldBe OK
        val page = CdsPage(contentAsString(result))
        page.title should startWith("Application complete")
      }
    }
  }

  private def displayPage(
    service: Service,
    cdsEnrolmentId: Option[String] = None,
    otherEnrolments: Set[Enrolment] = Set.empty
  )(test: Future[Result] => Any) = {
    withAuthorisedUser(
      defaultUserId,
      mockAuthConnector,
      cdsEnrolmentId = cdsEnrolmentId,
      otherEnrolments = otherEnrolments
    )
    await(
      test(
        controller.displayPage(service)
          .apply(SessionBuilder.buildRequestWithSessionAndPath("/atar/subscribe/", defaultUserId))
      )
    )
  }

  private def enrol(service: Service, responseStatus: Int)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(mockEnrolmentService.enrolWithExistingEnrolment(any[ExistingEori], any[Service])(any())).thenReturn(
      Future(responseStatus)
    )
    await(test(controller.enrol(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def enrolMissingEnrolment(service: Service)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector)
    when(mockEnrolmentService.enrolWithExistingEnrolment(any[ExistingEori], any[Service])(any())).thenReturn(
      Future.failed(MissingEnrolmentException("EORI"))
    )
    await(test(controller.enrol(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def enrolMissingEnrolmentForUser(service: Service)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector, otherEnrolments = Set(atarEnrolment))
    when(mockEnrolmentService.enrolWithExistingEnrolment(any[ExistingEori], any[Service])(any())).thenReturn(
      Future.failed(MissingEnrolmentException("EORI"))
    )
    await(test(controller.enrol(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
  }

  private def enrolSuccess(service: Service, cdsEnrolmentId: Option[String] = None)(test: Future[Result] => Any) = {
    withAuthorisedUser(defaultUserId, mockAuthConnector, cdsEnrolmentId = cdsEnrolmentId)
    await(test(controller.enrolSuccess(service).apply(SessionBuilder.buildRequestWithSession(defaultUserId))))
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

  private def userHasGroupEnrolmentToService =
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any())).thenReturn(Future.successful(true))

  private def userDoesNotHaveGroupEnrolmentToService =
    when(groupEnrolmentExtractor.hasGroupIdEnrolmentTo(any(), any())(any())).thenReturn(Future.successful(false))

}
