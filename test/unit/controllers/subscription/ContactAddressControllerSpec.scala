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

import common.pages.subscription.{AddressPage, ContactAddressPage}
import common.support.testdata.subscription.BusinessDatesOrganisationTypeTables
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.ContactAddressController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ContactAddressController.submit
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.UserLocationDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ContactAddressSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Country
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.contact_address
import unit.controllers.CdsPage
import util.StringThings._
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.SessionBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ContactAddressControllerSpec
    extends SubscriptionFlowTestSupport with BusinessDatesOrganisationTypeTables with BeforeAndAfterEach
    with SubscriptionFlowCreateModeTestSupport with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = ContactAddressPage.formId

  protected override def submitInCreateModeUrl: String =
    submit(atarService, isInReviewMode = false).url

  protected override def submitInReviewModeUrl: String =
    submit(atarService, isInReviewMode = true).url

  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockCdsFrontendDataCache       = mock[SessionCache]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val emulatedFailure                = new UnsupportedOperationException("Emulation of service call failure")

  private val viewAddress = instanceOf[contact_address]

  private val controller = new ContactAddressController(
    mockAuthAction,
    mcc,
    mockSubscriptionDetailsService,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    mockRequestSessionData,
    mockCdsFrontendDataCache,
    viewAddress
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
    "countryCode" -> "GB"
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

    when(mockSubscriptionBusinessService.contactAddress(any[Request[_]])).thenReturn(Future.successful(None))
    when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]])).thenReturn(Future.successful(None))
    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(ContactAddressSubscriptionFlowPage)
  }

  override protected def afterEach(): Unit = {
    reset(mockSubscriptionBusinessService)
    reset(mockSubscriptionFlowManager)
    reset(mockRequestSessionData)
    reset(mockSubscriptionDetailsService)
    reset(mockCdsFrontendDataCache)

    super.afterEach()
  }

  "Contact Address Controller form in create mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.displayPage(atarService))

    "display title as 'What is your contact address?'" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("What is your contact address?")
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
      when(mockSubscriptionBusinessService.contactAddress(any[Request[_]]))
        .thenReturn(Future.successful(Some(ContactAddressPage.filledValues)))
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

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(mockAuthConnector, controller.reviewForm(atarService))

    "display title as 'What is your contact address?'" in {
      when(mockCdsFrontendDataCache.userLocation(any[Request[_]])).thenReturn(
        Future.successful(UserLocationDetails(None))
      )
      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("What is your contact address?")
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
      when(mockSubscriptionBusinessService.contactAddress(any[Request[_]]))
        .thenReturn(Future.successful(Some(ContactAddressPage.filledValues)))
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
      controller.submit(atarService, isInReviewMode = false)
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
        redirectLocation(result) shouldBe Some("next-page-url")
      }
    }
  }

  "submitting the form with all mandatory fields filled when in review mode" should {

    assertNotLoggedInAndCdsEnrolmentChecksForSubscribe(
      mockAuthConnector,
      controller.submit(atarService, isInReviewMode = true)
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
        redirectLocation(result) shouldBe Some("next-page-url")
      }
    }
  }

  "Contact Address Detail form" should {
    "be mandatory" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(None)))
      submitForm(addressFieldsEmpty) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(ContactAddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter the first line of your address"
        )
        page.getElementsText(ContactAddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter your town or city"
        )
        page.getElementsText(ContactAddressPage.pageLevelErrorSummaryListXPath) should include(
          messages("cds.matching-error.country.invalid")
        )

        page.getElementsText(
          ContactAddressPage.fieldLevelErrorAddressLineOne
        ) shouldBe "Error: Enter the first line of your address"
        page.getElementsText(
          ContactAddressPage.fieldLevelErrorAddressLineThree
        ) shouldBe "Error: Enter your town or city"
        page.getElementsText(ContactAddressPage.fieldLevelErrorCountry) shouldBe "Error: " + messages(
          "cds.matching-error.country.invalid"
        )
      }
    }

    "have postcode mandatory when country is GB" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
      submitForm(
        Map(
          "line-1"      -> "addressLine1",
          "line-2"      -> "addressLine2",
          "line-3"      -> "addressLine3",
          "line-4"      -> "addressLine4",
          "postcode"    -> " ",
          "countryCode" -> "GB"
        )
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(ContactAddressPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.subscription.contact-details.error.postcode"
        )

        page.getElementsText(ContactAddressPage.fieldLevelErrorPostcode) shouldBe s"Error: ${messages("cds.subscription.contact-details.error.postcode")}"
      }
    }

    "have postcode mandatory when country is a channel island" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GG"))))
      submitForm(
        Map(
          "line-1"      -> "addressLine1",
          "line-2"      -> "addressLine2",
          "line-3"      -> "addressLine3",
          "line-4"      -> "addressLine4",
          "postcode"    -> " ",
          "countryCode" -> "JE"
        )
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(ContactAddressPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.subscription.contact-details.error.postcode"
        )
        page.getElementsText(ContactAddressPage.fieldLevelErrorPostcode) shouldBe s"Error: ${messages("cds.subscription.contact-details.error.postcode")}"
      }
    }

    "not allow spaces to satisfy minimum length requirements" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
      submitForm(Map("line-1" -> 7.spaces, "line-3" -> 10.spaces)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(ContactAddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter the first line of your address"
        )
        page.getElementsText(ContactAddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter your town or city"
        )

        page.getElementsText(
          ContactAddressPage.fieldLevelErrorAddressLineOne
        ) shouldBe "Error: Enter the first line of your address"
        page.getElementsText(
          ContactAddressPage.fieldLevelErrorAddressLineThree
        ) shouldBe "Error: Enter your town or city"
      }
    }

    "be restricted to 35 character for street validation only" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
      val streetLine = stringOfLengthXGen(35)
      submitForm(addressFields ++ Map("line-1" -> streetLine.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          ContactAddressPage.fieldLevelErrorAddressLineOne
        ) shouldBe "Error: The first line of the address must be 35 characters or less"
      }
    }

    "be restricted to 35 character for city validation only" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
      val city = stringOfLengthXGen(35)
      submitForm(addressFields ++ Map("line-3" -> city.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          ContactAddressPage.fieldLevelErrorAddressLineThree
        ) shouldBe "Error: The town or city must be 35 characters or less"

      }
    }
    "be restricted to 35 character for region validation only" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("GB"))))
      val city = stringOfLengthXGen(35)
      submitForm(addressFields ++ Map("line-4" -> city.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          ContactAddressPage.fieldLevelErrorAddressLineFour
        ) shouldBe "Error: The Region or state must be 35 characters or less"

      }
    }

    "be validating postcode length to 8 when country is a channel island" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("JE"))))
      submitForm(
        Map(
          "line-1"      -> "addressLine1",
          "line-2"      -> "addressLine2",
          "line-3"      -> "addressLine3",
          "line-4"      -> "addressLine4",
          "postcode"    -> "123456789",
          "countryCode" -> "JE"
        )
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(ContactAddressPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.subscription.contact-details.error.postcode"
        )
        page.getElementsText(ContactAddressPage.fieldLevelErrorPostcode) shouldBe s"Error: ${messages("cds.subscription.contact-details.error.postcode")}"
      }
    }

    "be validating postcode length to 9 when country is not GB" in {
      when(mockCdsFrontendDataCache.userLocation(any())).thenReturn(Future.successful(UserLocationDetails(Some("FR"))))
      submitForm(
        Map(
          "line-1"      -> "addressLine1",
          "line-2"      -> "addressLine2",
          "line-3"      -> "addressLine3",
          "line-4"      -> "addressLine4",
          "postcode"    -> "1234567890",
          "countryCode" -> "FR"
        )
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          ContactAddressPage.pageLevelErrorSummaryListXPath
        ) shouldBe "The postcode must be 9 characters or less"
        page.getElementsText(
          ContactAddressPage.fieldLevelErrorPostcode
        ) shouldBe "Error: The postcode must be 9 characters or less"
      }
    }
  }

  private def submitForm(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    test(
      controller.submit(atarService, isInReviewMode = false)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def registerSaveDetailsMockSuccess(): Unit =
    when(mockSubscriptionDetailsService.cacheContactAddressDetails(any())(any[Request[_]]))
      .thenReturn(Future.successful(()))

  private def registerSaveDetailsMockFailure(exception: Throwable): Unit =
    when(mockSubscriptionDetailsService.cacheContactAddressDetails(any())(any[Request[_]]))
      .thenReturn(Future.failed(exception))

  private def showCreateForm(userId: String = defaultUserId)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.displayPage(atarService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(userId: String = defaultUserId)(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    test(controller.reviewForm(atarService).apply(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyAddressFieldExistsAndPopulatedCorrectly(page: CdsPage): Unit = {
    page.getElementValue(ContactAddressPage.lineThreeFieldXPath) shouldBe "Town"
    page.getElementValue(ContactAddressPage.lineOneFieldXPath) shouldBe "Line 1"
    page.getElementValue(ContactAddressPage.postcodeFieldXPath) shouldBe "SE28 1AA"
  }

  private def verifyAddressFieldExistsWithNoData(page: CdsPage): Unit = {
    page.getElementValue(ContactAddressPage.lineOneFieldXPath) shouldBe empty
    page.getElementValue(ContactAddressPage.lineTwoFieldXPath) shouldBe empty
    page.getElementValue(ContactAddressPage.lineThreeFieldXPath) shouldBe empty
    page.getElementValue(ContactAddressPage.lineFourFieldXPath) shouldBe empty
    page.getElementValue(ContactAddressPage.postcodeFieldXPath) shouldBe empty
    page.getElementValue(ContactAddressPage.countryFieldXPath) shouldBe empty
  }

}
