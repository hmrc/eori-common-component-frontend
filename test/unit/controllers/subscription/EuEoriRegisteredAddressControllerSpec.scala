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

import common.pages.subscription.{AddressPage, EuEoriRegisteredAddressPage}
import common.support.testdata.subscription.BusinessDatesOrganisationTypeTables
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{Request, Result}
import play.api.test.Helpers.*
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.EuEoriRegisteredAddressController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.EuEoriRegisteredAddressController.submit
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.UserLocationDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.EuEoriRegisteredAddressSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Country
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.eu_eori_registered_address
import unit.controllers.CdsPage
import util.StringThings.*
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EuEoriRegisteredAddressControllerSpec
    extends SubscriptionFlowTestSupport with BusinessDatesOrganisationTypeTables with BeforeAndAfterEach
    with SubscriptionFlowCreateModeTestSupport with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = EuEoriRegisteredAddressPage.formId

  protected override def submitInCreateModeUrl: String =
    submit(cdsService, isInReviewMode = false).url

  protected override def submitInReviewModeUrl: String =
    submit(cdsService, isInReviewMode = true).url

  private val mockCdsFrontendDataCache       = mock[SessionCache]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val emulatedFailure                = new UnsupportedOperationException("Emulation of service call failure")

  private val view = instanceOf[eu_eori_registered_address]

  private val controller = new EuEoriRegisteredAddressController(
    mockAuthAction,
    mcc,
    mockSubscriptionDetailsService,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    mockCdsFrontendDataCache,
    view
  )

  def stringOfLengthXGen(minLength: Int): Gen[String] =
    for {
      single: Char       <- Gen.alphaNumChar
      baseString: String <- Gen.listOfN(minLength, Gen.alphaNumChar).map(c => c.mkString)
      additionalEnding   <- Gen.alphaStr
    } yield s"$single$baseString$additionalEnding"

  val addressFields: Map[String, String] = Map(
    "line-1"      -> "addressLine1",
    "line-2"      -> "addressLine2",
    "line-3"      -> "addressLine3",
    "line-4"      -> "addressLine4",
    "postcode"    -> "SE28 1AA",
    "countryCode" -> "FR"
  )

  val mandatoryFields: Map[String, String] =
    Map("line-1" -> "addressLine1", "line-3" -> "addressLine3", "countryCode" -> "FR")

  val addressFieldsEmpty: Map[String, String] = Map("line-1" -> "", "line-3" -> "", "countryCode" -> "")

  val aFewCountries: List[Country] = List(
    Country("France", "country:FR"),
    Country("Germany", "country:DE"),
    Country("Italy", "country:IT"),
    Country("Japan", "country:JP")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()

    when(mockSubscriptionBusinessService.euEoriRegisteredAddress(any[Request[_]])).thenReturn(Future.successful(None))
    when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]])).thenReturn(Future.successful(None))
    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(EuEoriRegisteredAddressSubscriptionFlowPage)
  }

  override protected def afterEach(): Unit = {
    reset(mockSubscriptionBusinessService)
    reset(mockSubscriptionFlowManager)
    reset(mockSubscriptionDetailsService)
    reset(mockCdsFrontendDataCache)

    super.afterEach()
  }

  "Eu Eori Registered Address Controller form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.displayPage(cdsService))

    "display title as 'What is your organisation's registered address?'" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("What is your organisation's registered address?")
      }
    }

    "submit in create mode" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showCreateForm()(verifyFormActionInCreateMode)
    }

    "display the back link" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showCreateForm()(verifyBackLinkInCreateModeRegister)
    }

    "have Address input field without data if not cached previously" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        verifyAddressFieldExistsWithNoData(page)
      }
    }

    "have Address input field prepopulated if cached previously" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(Some("uk")))
      )
      when(mockSubscriptionBusinessService.euEoriRegisteredAddress(any[Request[_]]))
        .thenReturn(Future.successful(Some(EuEoriRegisteredAddressPage.filledValues)))
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        verifyAddressFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

  }

  "Contact Address Controller form in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewPage(cdsService))

    "display title as 'What is your organisation's registered address?'" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("What is your organisation's registered address?")
      }
    }

    "submit in review mode" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "display the back link" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showReviewForm()(verifyBackLinkInReviewMode)
    }

    "have Address input field without data if not cached previously" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        verifyAddressFieldExistsWithNoData(page)
      }
    }

    "have Address input field prepopulated if cached previously" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(Some("uk")))
      )
      when(mockSubscriptionBusinessService.euEoriRegisteredAddress(any[Request[_]]))
        .thenReturn(Future.successful(Some(EuEoriRegisteredAddressPage.filledValues)))
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        verifyAddressFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(Some("uk")))
      )
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }
  }

  "submitting the form with all mandatory fields filled when in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(cdsService, isInReviewMode = false)
    )

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitForm(mandatoryFields) { result =>
          await(result)
        }
      }

      caught shouldEqual emulatedFailure
    }

    "redirect to next screen" in {
      submitForm(mandatoryFields) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("https://www.gov.uk/check-eori-number")
      }
    }
  }

  "submitting the form with all mandatory fields filled when in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(cdsService, isInReviewMode = true)
    )

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitForm(mandatoryFields) { result =>
          await(result)
        }
      }

      caught shouldEqual emulatedFailure
    }

    "redirect to next screen" in {
      submitForm(mandatoryFields) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("https://www.gov.uk/check-eori-number")
      }
    }
  }

  "Eu Eori Registered Address Detail form" should {
    "be mandatory" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(None)))
      submitForm(addressFieldsEmpty) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(EuEoriRegisteredAddressPage.pageLevelErrorSummaryListXPath) should include(
          messages("eu.eori.registered.address.line-1.error.empty")
        )
        page.getElementsText(EuEoriRegisteredAddressPage.pageLevelErrorSummaryListXPath) should include(
          messages("eu.eori.registered.address.line-3.error.empty")
        )
        page.getElementsText(EuEoriRegisteredAddressPage.pageLevelErrorSummaryListXPath) should include(
          messages("eu.eori.registered.address.country.error.empty")
        )

        page.getElementsText(
          EuEoriRegisteredAddressPage.fieldLevelErrorAddressLineOne
        ) shouldBe "Error: Enter a street and number"
        page.getElementsText(
          EuEoriRegisteredAddressPage.fieldLevelErrorAddressLineThree
        ) shouldBe "Error: Enter a town or city"
        page.getElementsText(EuEoriRegisteredAddressPage.fieldLevelErrorCountry) shouldBe "Error: " + messages(
          "eu.eori.registered.address.country.error.empty"
        )
      }
    }

    "not allow spaces to satisfy minimum length requirements" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("FR"))))
      submitForm(Map("line-1" -> 7.spaces, "line-3" -> 10.spaces)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(EuEoriRegisteredAddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter a street and number"
        )
        page.getElementsText(EuEoriRegisteredAddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter a town or city"
        )

        page.getElementsText(
          EuEoriRegisteredAddressPage.fieldLevelErrorAddressLineOne
        ) shouldBe "Error: Enter a street and number"
        page.getElementsText(
          EuEoriRegisteredAddressPage.fieldLevelErrorAddressLineThree
        ) shouldBe "Error: Enter a town or city"
      }
    }

    "not allow special characters" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("FR"))))
      submitForm(Map("line-1" -> "#1", "line-2" -> "#2", "line-3" -> "#3", "line-4" -> "#4")) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(EuEoriRegisteredAddressPage.pageLevelErrorSummaryListXPath) should include(
          "Street and number must only include letters a to z, numbers, apostrophes, full stops, ampersands, hyphens and spaces"
        )
        page.getElementsText(EuEoriRegisteredAddressPage.pageLevelErrorSummaryListXPath) should include(
          "Town or city must only include letters a to z, numbers, apostrophes, full stops, ampersands, hyphens and spaces"
        )

        page.getElementsText(
          EuEoriRegisteredAddressPage.fieldLevelErrorAddressLineOne
        ) shouldBe "Error: Street and number must only include letters a to z, numbers, apostrophes, full stops, ampersands, hyphens and spaces"
      }
    }

    "be restricted to 35 character for street validation only" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("FR"))))
      val streetLine = stringOfLengthXGen(71)
      submitForm(addressFields ++ Map("line-1" -> streetLine.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          EuEoriRegisteredAddressPage.fieldLevelErrorAddressLineOne
        ) shouldBe "Error: Street and number must be 70 characters or less"
      }
    }

    "be restricted to 35 character for city validation only" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("FR"))))
      val city = stringOfLengthXGen(35)
      submitForm(addressFields ++ Map("line-3" -> city.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          EuEoriRegisteredAddressPage.fieldLevelErrorAddressLineThree
        ) shouldBe "Error: Town or city must be 35 characters or less"

      }
    }

    "be validating postcode length to 35" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("JE"))))
      submitForm(
        Map(
          "line-1"      -> "addressLine1",
          "line-3"      -> "addressLine3",
          "postcode"    -> stringOfLengthXGen(36).sample.get,
          "countryCode" -> "JE"
        )
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(EuEoriRegisteredAddressPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "eu.eori.registered.address.postcode.error.too-long"
        )
        page.getElementsText(EuEoriRegisteredAddressPage.fieldLevelErrorPostcode) shouldBe s"Error: ${messages("eu.eori.registered.address.postcode.error.too-long")}"
      }
    }
  }

  private def submitForm(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    test(
      controller.submit(cdsService, isInReviewMode = false)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def registerSaveDetailsMockSuccess(): Unit =
    when(mockSubscriptionDetailsService.cacheEuEoriRegisteredAddressDetails(any())(any[Request[_]]))
      .thenReturn(Future.successful(()))

  private def registerSaveDetailsMockFailure(exception: Throwable): Unit =
    when(mockSubscriptionDetailsService.cacheEuEoriRegisteredAddressDetails(any())(any[Request[_]]))
      .thenReturn(Future.failed(exception))

  private def showCreateForm(userId: String = defaultUserId)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.displayPage(cdsService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(userId: String = defaultUserId)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.reviewPage(cdsService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyAddressFieldExistsAndPopulatedCorrectly(page: CdsPage): Unit = {
    page.getElementValue(EuEoriRegisteredAddressPage.lineOneFieldXPath) shouldBe "Line 1"
    page.getElementValue(EuEoriRegisteredAddressPage.lineThreeFieldXPath) shouldBe "Town"
    page.getElementValue(EuEoriRegisteredAddressPage.postcodeFieldXPath) shouldBe "FR29 1AA"
  }

  private def verifyAddressFieldExistsWithNoData(page: CdsPage): Unit = {
    page.getElementValue(EuEoriRegisteredAddressPage.lineOneFieldXPath) shouldBe empty
    page.getElementValue(EuEoriRegisteredAddressPage.lineThreeFieldXPath) shouldBe empty
    page.getElementValue(EuEoriRegisteredAddressPage.postcodeFieldXPath) shouldBe empty
    page.getElementValue(EuEoriRegisteredAddressPage.countryFieldXPath) shouldBe empty
  }

}
