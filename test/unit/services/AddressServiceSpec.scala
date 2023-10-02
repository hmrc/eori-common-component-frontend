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

package unit.services

import common.pages.subscription.AddressPage
import common.support.testdata.subscription.BusinessDatesOrganisationTypeTables
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController.submit
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.AddressDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{CdsOrganisationType, RegistrationDetails, SafeId}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.AddressDetailsForm.addressDetailsCreateForm
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{AddressService, SubscriptionDetailsService}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.address
import unit.controllers.CdsPage
import unit.controllers.subscription.{
  SubscriptionFlowCreateModeTestSupport,
  SubscriptionFlowReviewModeTestSupport,
  SubscriptionFlowTestSupport
}
import util.StringThings._
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.RegistrationDetailsBuilder._
import util.builders.SessionBuilder

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AddressServiceSpec
    extends SubscriptionFlowTestSupport with BusinessDatesOrganisationTypeTables with BeforeAndAfterEach
    with SubscriptionFlowCreateModeTestSupport with SubscriptionFlowReviewModeTestSupport {

  protected override val formId: String = AddressPage.formId

  protected override def submitInCreateModeUrl: String =
    submit(isInReviewMode = false, atarService).url

  protected override def submitInReviewModeUrl: String =
    submit(isInReviewMode = true, atarService).url

  private val mockRequestSessionData         = mock[RequestSessionData]
  private val mockCdsFrontendDataCache       = mock[SessionCache]
  private val mockSubscriptionDetailsService = mock[SubscriptionDetailsService]
  private val emulatedFailure                = new UnsupportedOperationException("Emulation of service call failure")
  private val mockOrganisationType           = mock[CdsOrganisationType]

  private val viewAddress = instanceOf[address]

  private val service = new AddressService(
    mockSubscriptionDetailsService,
    mockSubscriptionBusinessService,
    mockSubscriptionFlowManager,
    viewAddress,
    mcc,
    mockRequestSessionData,
    mockCdsFrontendDataCache
  )

  def stringOfLengthXGen(minLength: Int): Gen[String] =
    for {
      single: Char       <- Gen.alphaNumChar
      baseString: String <- Gen.listOfN(minLength, Gen.alphaNumChar).map(c => c.mkString)
      additionalEnding   <- Gen.alphaStr
    } yield s"$single$baseString$additionalEnding"

  val mandatoryFields      = Map("city" -> "city", "street" -> "street", "postcode" -> "SE28 1AA", "countryCode" -> "GB")
  val mandatoryFieldsEmpty = Map("city" -> "", "street" -> "", "postcode" -> "", "countryCode" -> "")

  val fakeRequest = SessionBuilder.buildRequestWithSessionAndPathNoUser(
    method = "GET",
    path = s"/customs-enrolment-services/atar/subscribe/"
  )

  override def beforeEach(): Unit = {
    super.beforeEach()

    when(mockSubscriptionBusinessService.address(any[Request[_]])).thenReturn(Future.successful(None))
    when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]])).thenReturn(Future.successful(None))
    when(mockSubscriptionDetailsService.cacheAddressDetails(any())(any())).thenReturn(Future.successful((): Unit))
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(
      Future.successful(organisationRegistrationDetails)
    )
    when(mockCdsFrontendDataCache.saveRegistrationDetails(any[RegistrationDetails])(any[Request[_]]))
      .thenReturn(Future.successful(true))
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(mockOrganisationType))
    registerSaveDetailsMockSuccess()
    setupMockSubscriptionFlowManager(AddressDetailsSubscriptionFlowPage)
  }

  override protected def afterEach(): Unit = {
    reset(mockSubscriptionBusinessService)
    reset(mockSubscriptionFlowManager)
    reset(mockRequestSessionData)
    reset(mockSubscriptionDetailsService)
    reset(mockCdsFrontendDataCache)

    super.afterEach()
  }

  "Subscription Address Controller form in create mode" should {

    "submit in create mode" in {
      showCreateForm()(verifyFormActionInCreateMode)
    }

    "have Address input field without data if not cached previously" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        verifyAddressFieldExistsWithNoData(page)
      }
    }

    "have Address input field prepopulated if cached previously" in {
      when(mockSubscriptionBusinessService.address(any[Request[_]]))
        .thenReturn(Future.successful(Some(AddressPage.filledValues)))
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        verifyAddressFieldExistsAndPopulatedCorrectly(page)
      }
    }

    "display the correct text for the continue button" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.continueButtonXpath) shouldBe ContinueButtonTextInCreateMode
      }
    }

  }

  "Subscription Address Controller form in create mode for Individual" should {

    "display title as 'Enter your address'" in {
      showCreateForm(userSelectedOrganisationType = Some(CdsOrganisationType.Individual)) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Enter your address")
        page.h1() shouldBe "Enter your address"
      }
    }
  }
  "Subscription Address Controller form in create mode for Partnership" should {

    "display title as 'Enter your address'" in {
      showCreateForm(userSelectedOrganisationType = Some(CdsOrganisationType.Partnership)) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Enter your organisation address")
        page.h1() shouldBe "Enter your organisation address"
      }
    }
  }

  "Subscription Address form in review mode for Individual" should {

    "display title as 'Enter your address'" in {
      showReviewForm(userSelectedOrganisationType = Some(CdsOrganisationType.Individual)) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Enter your address")
        page.h1() shouldBe "Enter your address"
      }
    }
  }
  "Subscription Address form in review mode for Partnership" should {

    "display title as 'Enter your address'" in {
      showReviewForm(userSelectedOrganisationType = Some(CdsOrganisationType.Partnership)) { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Enter your organisation address")
        page.h1() shouldBe "Enter your organisation address"
      }
    }
  }

  "Subscription Address form in review mode" should {

    "display title as 'Enter your business address'" in {
      showCreateForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.title() should startWith("Enter your organisation address")
      }
    }

    "submit in review mode" in {
      showReviewForm()(verifyFormSubmitsInReviewMode)
    }

    "retrieve the cached data" in {
      showReviewForm() { result =>
        CdsPage(contentAsString(result))
        verify(mockSubscriptionBusinessService).addressOrException(any[Request[_]])
      }
    }

    "have all the required input fields without data" in {
      showReviewForm(AddressViewModel("", "", None, "")) { result =>
        val page = CdsPage(contentAsString(result))
        verifyAddressFieldExistsWithNoData(page)
      }
    }

    "display the correct text for the continue button" in {
      showReviewForm() { result =>
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.continueButtonXpath) shouldBe ContinueButtonTextInReviewMode
      }
    }
  }

  "submitting the form with all mandatory fields filled when in create mode for organisation type" should {

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)

      val caught = intercept[RuntimeException] {
        submitFormInCreateModeForOrganisation(mandatoryFields) { result =>
          await(result)
        }
      }

      caught shouldEqual emulatedFailure
    }

    "redirect to next screen" in {
      when(mockCdsFrontendDataCache.clearAddressLookupParams(any())).thenReturn(Future.successful((): Unit))
      submitFormInCreateModeForOrganisation(mandatoryFields) { result =>
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("next-page-url")
      }
    }
  }

  "submitting the form for Migration journey" should {

    "redirect to email address page when No Nino and No Utr is provided" in {
      when(mockCdsFrontendDataCache.clearAddressLookupParams(any())).thenReturn(Future.successful((): Unit))
      submitFormInCreateModeForIndividualSubscription(
        Map("street" -> "My street", "city" -> "My city", "postcode" -> "SE281AA", "countryCode" -> "GB")
      ) { result =>
        status(result) shouldBe SEE_OTHER

        verify(mockCdsFrontendDataCache).clearAddressLookupParams(any())
      }
    }
  }

  "submitting the form with all mandatory fields filled when in review mode for all organisation types" should {

    "wait until the saveSubscriptionDetailsHolder is completed before progressing" in {
      registerSaveDetailsMockFailure(emulatedFailure)
      the[UnsupportedOperationException] thrownBy {
        submitFormInReviewMode(mandatoryFields)(await(_))
      } should have message emulatedFailure.getMessage
    }

    "redirect to review screen" in {
      when(mockCdsFrontendDataCache.clearAddressLookupParams(any())).thenReturn(Future.successful((): Unit))
      submitFormInReviewMode(mandatoryFields)(verifyRedirectToReviewPage())
    }
  }

  "Address Detail form" should {
    "be mandatory" in {
      submitFormInCreateModeForOrganisation(mandatoryFieldsEmpty) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter the first line of your address"
        )
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include("Enter your town or city")
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include("Enter your country")

        page.getElementsText(
          AddressPage.streetFieldLevelErrorXPath
        ) shouldBe "Error: Enter the first line of your address"
        page.getElementsText(AddressPage.cityFieldLevelErrorXPath) shouldBe "Error: Enter your town or city"
        page.getElementsText(AddressPage.countryFieldLevelErrorXPath) shouldBe "Error: Enter your country"
      }
    }

    "have postcode mandatory when country is GB" in {
      submitFormInCreateModeForOrganisation(
        Map("street" -> "My street", "city" -> "My city", "postcode" -> "", "countryCode" -> "GB")
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.subscription.contact-details.error.postcode"
        )
        page.getElementsText(AddressPage.postcodeFieldLevelErrorXPath) shouldBe s"Error: ${messages("cds.subscription.contact-details.error.postcode")}"
      }
    }

    "have postcode mandatory when country is a channel island" in {
      submitFormInCreateModeForOrganisation(
        Map("street" -> "My street", "city" -> "My city", "postcode" -> "", "countryCode" -> "JE")
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.subscription.contact-details.error.postcode"
        )
        page.getElementsText(AddressPage.postcodeFieldLevelErrorXPath) shouldBe s"Error: ${messages("cds.subscription.contact-details.error.postcode")}"
      }
    }

    "not allow spaces to satisfy minimum length requirements" in {
      submitFormInCreateModeForOrganisation(Map("city" -> 10.spaces, "street" -> 7.spaces)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include(
          "Enter the first line of your address"
        )
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) should include("Enter your town or city")

        page.getElementsText(
          AddressPage.streetFieldLevelErrorXPath
        ) shouldBe "Error: Enter the first line of your address"
        page.getElementsText(AddressPage.cityFieldLevelErrorXPath) shouldBe "Error: Enter your town or city"
      }
    }

    "be restricted to 70 character for street validation only" in {
      val streetLine = stringOfLengthXGen(70)
      submitFormInCreateModeForOrganisation(mandatoryFields ++ Map("street" -> streetLine.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          AddressPage.streetFieldLevelErrorXPath
        ) shouldBe "Error: The first line of the address must be 70 characters or less"
      }
    }

    "be restricted to 35 character for city validation only" in {
      val city = stringOfLengthXGen(35)
      submitFormInCreateModeForOrganisation(mandatoryFields ++ Map("city" -> city.sample.get)) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          AddressPage.cityFieldLevelErrorXPath
        ) shouldBe "Error: The town or city must be 35 characters or less"

      }
    }

    "be validating postcode length to 8 when country is a channel island" in {
      submitFormInCreateModeForOrganisation(
        Map("street" -> "My street", "city" -> "City", "postcode" -> "123456789", "countryCode" -> "JE")
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(AddressPage.pageLevelErrorSummaryListXPath) shouldBe messages(
          "cds.subscription.contact-details.error.postcode"
        )
        page.getElementsText(AddressPage.postcodeFieldLevelErrorXPath) shouldBe s"Error: ${messages("cds.subscription.contact-details.error.postcode")}"
      }
    }

    "be validating postcode length to 9 when country is not GB" in {
      submitFormInCreateModeForOrganisation(
        Map("street" -> "My street", "city" -> "City", "postcode" -> "1234567890", "countryCode" -> "FR")
      ) { result =>
        status(result) shouldBe BAD_REQUEST
        val page = CdsPage(contentAsString(result))
        page.getElementsText(
          AddressPage.pageLevelErrorSummaryListXPath
        ) shouldBe "The postcode must be 9 characters or less"
        page.getElementsText(
          AddressPage.postcodeFieldLevelErrorXPath
        ) shouldBe "Error: The postcode must be 9 characters or less"
      }
    }
  }

  private def submitFormInCreateModeForOrganisation(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(
      Future.successful(organisationRegistrationDetails)
    )

    test(
      service.handleFormDataAndRedirect(addressDetailsCreateForm(), false, atarService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInCreateModeForIndividualSubscription(
    form: Map[String, String],
    userId: String = defaultUserId
  )(test: Future[Result] => Any): Unit = {
    val individualRegistrationDetails = RegistrationDetails.individual(
      sapNumber = "0123456789",
      safeId = SafeId("safe-id"),
      name = "John Doe",
      address = defaultAddress,
      dateOfBirth = LocalDate.parse("1980-07-23"),
      customsId = None
    )

    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(CdsOrganisationType("individual")))
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(
      Future.successful(individualRegistrationDetails)
    )
    when(mockSubscriptionDetailsService.cachedCustomsId(any[Request[_]])).thenReturn(Future.successful(None))

    test(
      service.handleFormDataAndRedirect(addressDetailsCreateForm(), true, atarService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def submitFormInReviewMode(form: Map[String, String], userId: String = defaultUserId)(
    test: Future[Result] => Any
  ): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.userSelectedOrganisationType(any[Request[AnyContent]]))
      .thenReturn(Some(CdsOrganisationType("company")))

    test(
      service.handleFormDataAndRedirect(addressDetailsCreateForm(), true, atarService)(
        SessionBuilder.buildRequestWithSessionAndFormValues(userId, form)
      )
    )
  }

  private def registerSaveDetailsMockSuccess(): Unit =
    when(mockSubscriptionDetailsService.cacheAddressDetails(any())(any[Request[_]]))
      .thenReturn(Future.successful(()))

  private def registerSaveDetailsMockFailure(exception: Throwable): Unit =
    when(mockSubscriptionDetailsService.cacheAddressDetails(any())(any[Request[_]]))
      .thenReturn(Future.failed(exception))

  private def showCreateForm(
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)
    when(mockRequestSessionData.isIndividualOrSoleTrader(any[Request[AnyContent]]))
      .thenReturn(isIndividual(userSelectedOrganisationType))
    test(service.populateFormViewCached(false, atarService)(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def showReviewForm(
    dataToEdit: AddressViewModel = AddressPage.filledValues,
    userId: String = defaultUserId,
    userSelectedOrganisationType: Option[CdsOrganisationType] = None
  )(test: Future[Result] => Any): Unit = {
    withAuthorisedUser(userId, mockAuthConnector)

    when(mockRequestSessionData.isIndividualOrSoleTrader(any[Request[AnyContent]]))
      .thenReturn(isIndividual(userSelectedOrganisationType))
    when(mockSubscriptionBusinessService.addressOrException(any[Request[_]])).thenReturn(Future.successful(dataToEdit))
    when(mockCdsFrontendDataCache.registrationDetails(any[Request[_]])).thenReturn(
      Future.successful(individualRegistrationDetails)
    )

    test(service.populateReviewViewCached(true, atarService)(SessionBuilder.buildRequestWithSession(userId)))
  }

  private def verifyAddressFieldExistsAndPopulatedCorrectly(page: CdsPage): Unit = {
    page.getElementValue(AddressPage.cityFieldXPath) shouldBe "city name"
    page.getElementValue(AddressPage.streetFieldXPath) shouldBe "Line 1"
    page.getElementValue(AddressPage.postcodeFieldXPath) shouldBe "SE28 1AA"
  }

  private def verifyAddressFieldExistsWithNoData(page: CdsPage): Unit = {
    page.getElementValue(AddressPage.cityFieldXPath) shouldBe empty
    page.getElementValue(AddressPage.streetFieldXPath) shouldBe empty
    page.getElementValue(AddressPage.postcodeFieldXPath) shouldBe empty
    page.getElementValue(AddressPage.countryCodeFieldXPath) shouldBe empty
  }

  private def isIndividual(userSelectedOrganisationType: Option[CdsOrganisationType]) =
    userSelectedOrganisationType.contains(CdsOrganisationType.Individual)

}
