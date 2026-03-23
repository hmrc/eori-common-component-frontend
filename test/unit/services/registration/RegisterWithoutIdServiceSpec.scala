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

package unit.services.registration

import base.UnitSpec
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.RegisterWithoutIdConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{NameModel, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EuEoriRegisteredAddressModel
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RequestCommonGenerator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegisterWithoutIdService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RegisterWithoutIdServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val mockConnector      = mock[RegisterWithoutIdConnector]
  private val mockReqCommonGen   = mock[RequestCommonGenerator]
  private val mockDetailsCreator = mock[RegistrationDetailsCreator]
  private val mockSessionCache   = mock[SessionCache]

  implicit val hc: HeaderCarrier     = mock[HeaderCarrier]
  implicit val request: Request[Any] = mock[Request[Any]]

  private val service = new RegisterWithoutIdService(
    mockConnector,
    mockReqCommonGen,
    mockDetailsCreator,
    mockSessionCache
  )

  private val processingDate = LocalDateTime.now()
  private val responseCommon = ResponseCommon(status = "OK", processingDate = processingDate)

  override def beforeEach(): Unit =
    reset(mockConnector, mockReqCommonGen, mockDetailsCreator, mockSessionCache)

  "registerOrganisation" should {

    "call connector and save organisation registration details when subscription details present" in {
      val contact = ContactDetailsModel(
        fullName = "Contact",
        emailAddress = "a@b.com",
        telephone = Some("111"),
        fax = None,
        useAddressFromRegistrationDetails = true,
        street = None,
        city = None,
        postcode = None,
        countryCode = None
      )
      val euAddress =
        EuEoriRegisteredAddressModel(lineOne = "L1", lineThree = "L3", postcode = Some("12345"), country = "FR")
      val subDetails = SubscriptionDetails(
        contactDetails = Some(contact),
        euEoriRegisteredAddress = Some(euAddress),
        nameOrganisationDetails = Some(NameOrganisationMatchModel("OrgName"))
      )

      val requestCommon = RequestCommon(acknowledgementReference = "ack", regime = "CDS", receiptDate = processingDate)
      when(mockReqCommonGen.generate()).thenReturn(requestCommon)

      val rwResponseDetail = RegisterWithoutIdResponseDetail(SAFEID = "safe123", ARN = Some("A1"))
      val rwResponse       = RegisterWithoutIDResponse(responseCommon, Some(rwResponseDetail))

      when(mockConnector.register(any[RegisterWithoutIDRequest])(meq(hc))).thenReturn(Future.successful(rwResponse))

      val expectedRegistrationDetails = RegistrationDetailsOrganisation(
        customsId = None,
        sapNumber = TaxPayerId(""),
        safeId = SafeId("safe123"),
        name = "OrgName",
        address = Address("L1", None, Some("L3"), None, Some("12345"), "FR"),
        dateOfEstablishment = Some(LocalDate.parse("1990-01-01")),
        etmpOrganisationType = None
      )

      when(mockDetailsCreator.registrationDetails(meq(rwResponse), meq("OrgName"), any())).thenReturn(
        expectedRegistrationDetails
      )
      when(mockSessionCache.saveRegistrationDetailsWithoutId(any(), any(), any())(meq(hc), meq(request))).thenReturn(
        Future.successful(true)
      )

      val loggedInUser =
        LoggedInUserWithEnrolments(None, None, Enrolments(Set.empty[Enrolment]), None, Some("groupId"), "cred")

      val result = await(service.registerOrganisation(subDetails, loggedInUser, None))

      result shouldBe rwResponse
      verify(mockConnector).register(any[RegisterWithoutIDRequest])(meq(hc))
      verify(mockSessionCache).saveRegistrationDetailsWithoutId(any(), any(), any())(meq(hc), meq(request))
    }

    "fail with DataUnavailableException when subscription details missing" in {
      val subDetails = SubscriptionDetails()
      val loggedInUser =
        LoggedInUserWithEnrolments(None, None, Enrolments(Set.empty[Enrolment]), None, Some("group"), "cred")

      intercept[DataUnavailableException] {
        await(service.registerOrganisation(subDetails, loggedInUser, None))
      }
    }
  }

  "registerIndividual" should {

    "call connector and save individual registration details when subscription details present" in {
      val names = NameModel(givenName = "Given", familyName = "Family")
      val contact = ContactDetailsModel(
        fullName = "Contact",
        emailAddress = "a@b.com",
        telephone = Some("111"),
        fax = None,
        useAddressFromRegistrationDetails = true,
        street = None,
        city = None,
        postcode = None,
        countryCode = None
      )
      val euAddress =
        EuEoriRegisteredAddressModel(lineOne = "L1", lineThree = "L3", postcode = Some("12345"), country = "FR")
      val subDetails = SubscriptionDetails(
        euNameDetails = Some(names),
        dateEstablished = Some(LocalDate.parse("1990-01-01")),
        contactDetails = Some(contact),
        euEoriRegisteredAddress = Some(euAddress)
      )

      val requestCommon = RequestCommon(acknowledgementReference = "ack", regime = "CDS", receiptDate = processingDate)
      when(mockReqCommonGen.generate()).thenReturn(requestCommon)

      val rwResponseDetail = RegisterWithoutIdResponseDetail(SAFEID = "safe123", ARN = None)
      val rwResponse       = RegisterWithoutIDResponse(responseCommon, Some(rwResponseDetail))

      when(mockConnector.register(any[RegisterWithoutIDRequest])(meq(hc))).thenReturn(Future.successful(rwResponse))

      val individualNameAndDateOfBirth =
        IndividualNameAndDateOfBirth("Given", None, "Family", LocalDate.parse("1990-01-01"))
      val expectedRegistrationDetails = RegistrationDetailsIndividual(
        customsId = None,
        sapNumber = TaxPayerId(""),
        safeId = SafeId("safe123"),
        name = "Given Family",
        address = Address("L1", None, Some("L3"), None, Some("12345"), "FR"),
        dateOfBirth = LocalDate.parse("1990-01-01")
      )

      when(
        mockDetailsCreator.registrationDetails(meq(rwResponse), meq(individualNameAndDateOfBirth), any())
      ).thenReturn(expectedRegistrationDetails)
      when(mockSessionCache.saveRegistrationDetailsWithoutId(any(), any(), any())(meq(hc), meq(request))).thenReturn(
        Future.successful(true)
      )

      val loggedInUser =
        LoggedInUserWithEnrolments(None, None, Enrolments(Set.empty[Enrolment]), None, Some("groupId"), "cred")

      val result = await(service.registerIndividual(subDetails, loggedInUser, None))

      result shouldBe rwResponse
      verify(mockConnector).register(any[RegisterWithoutIDRequest])(meq(hc))
      verify(mockSessionCache).saveRegistrationDetailsWithoutId(any(), any(), any())(meq(hc), meq(request))
    }

    "fail with DataUnavailableException when subscription details missing" in {
      val subDetails = SubscriptionDetails()
      val loggedInUser =
        LoggedInUserWithEnrolments(None, None, Enrolments(Set.empty[Enrolment]), None, Some("group"), "cred")

      intercept[DataUnavailableException] {
        await(service.registerIndividual(subDetails, loggedInUser, None))
      }
    }
  }

}
