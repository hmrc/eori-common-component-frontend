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

package unit.services.subscription

import base.UnitSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.Save4LaterConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{FormData, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{
  AddressViewModel,
  CompanyRegisteredCountry,
  ContactAddressModel
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.{ContactDetailsAdaptor, RegistrationDetailsCreator}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.http.HeaderCarrier
import util.builders.RegistrationDetailsBuilder._
import util.builders.SubscriptionInfoBuilder._
import util.builders.{RegistrationDetailsBuilder, SubscriptionContactDetailsFormBuilder}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future
import scala.util.Random

class SubscriptionDetailsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier            = mock[HeaderCarrier]
  implicit val request: Request[AnyContent] = mock[Request[AnyContent]]

  private val mockSessionCache               = mock[SessionCache]
  private val mockRegistrationDetailsCreator = mock[RegistrationDetailsCreator]
  private val mockRegistrationDetails        = mock[RegistrationDetails]
  private val mockSave4LaterConnector        = mock[Save4LaterConnector]

  private val mockContactDetailsAdaptor     = mock[ContactDetailsAdaptor]
  private val mockSubscriptionDetailsHolder = mock[SubscriptionDetails]

  private val expectedDate = LocalDate.now()

  private val addressDetails =
    AddressViewModel(street = "street", city = "city", postcode = Some("postcode"), countryCode = "GB")

  private val contactAddressDetails =
    ContactAddressModel("Line 1", Some("Line 2"), "Town", Some("Region"), Some("SE28 1AA"), "GB")

  private val nameId        = NameIdOrganisationMatchModel(name = "orgname", id = "ID")
  private val customsIdUTR  = Utr("utrxxxxx")
  private val customsIdEori = Eori("GB123456789123")
  private val utrMatch      = UtrMatchModel(Some(true), Some("utrxxxxx"))
  private val ninoMatch     = NinoMatchModel(Some(true), Some("ninoxxxxx"))

  private val subscriptionDetailsHolderService =
    new SubscriptionDetailsService(mockSessionCache, mockContactDetailsAdaptor, mockSave4LaterConnector)(global)

  private val eoriNumericLength = 15
  private val eoriId            = "GB" + Random.nextString(eoriNumericLength)

  private val contactDetailsViewModelWhenNotUsingRegisteredAddress =
    SubscriptionContactDetailsFormBuilder.createContactDetailsViewModelWhenNotUsingRegAddress

  private val contactDetailsViewModelWhenUsingRegisteredAddress =
    SubscriptionContactDetailsFormBuilder.createContactDetailsViewModelWhenUseRegAddress

  override def beforeEach(): Unit = {
    reset(mockSessionCache)
    reset(mockRegistrationDetailsCreator)
    reset(mockRegistrationDetails)
    reset(mockSubscriptionDetailsHolder)
    reset(mockContactDetailsAdaptor)
    reset(mockSave4LaterConnector)

    when(mockSessionCache.saveRegistrationDetails(any[RegistrationDetails])(any[Request[AnyContent]]))
      .thenReturn(Future.successful(true))

    when(mockSessionCache.saveSub01Outcome(any[Sub01Outcome])(any[Request[AnyContent]]))
      .thenReturn(Future.successful(true))

    when(mockSessionCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[Request[AnyContent]]))
      .thenReturn(Future.successful(true))

    val existingHolder = SubscriptionDetails(contactDetails = Some(mock[ContactDetailsModel]))

    when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(Future.successful(existingHolder))
  }

  "Calling saveKeyIdentifiers" should {
    "save saveKeyIdentifiers in mongo" in {
      val groupId    = GroupId("groupId")
      val internalId = InternalId("internalId")
      val safeId     = SafeId("safeId")
      val key        = "cachedGroupId"
      val cacheIds   = CacheIds(internalId, safeId, Some("atar"))
      when(mockSessionCache.safeId).thenReturn(Future.successful(SafeId("safeId")))
      when(
        mockSave4LaterConnector.put[CacheIds](
          ArgumentMatchers.eq(groupId.id),
          ArgumentMatchers.eq(key),
          ArgumentMatchers.eq(cacheIds)
        )(any())
      ).thenReturn(Future.successful(()))
      val expected: Unit = await(subscriptionDetailsHolderService.saveKeyIdentifiers(groupId, internalId, atarService))
      expected shouldBe ((): Unit)
    }
    "throw IllegalArgumentException if InternalId  are invalid  in mongo" in {
      intercept[IllegalArgumentException] {
        InternalId(None)
      }
    }

    "throw IllegalArgumentException if groupId  are invalid  in mongo" in {
      intercept[IllegalArgumentException] {
        GroupId(None)
      }
    }

  }

  "Calling cacheAddressDetails" should {
    "save Address Details in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheAddressDetails(addressDetails))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.addressDetails shouldBe Some(addressDetails)

    }

    "should not save emptry strings in postcode field" in {

      await(subscriptionDetailsHolderService.cacheAddressDetails(addressDetails.copy(postcode = Some(""))))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.addressDetails shouldBe Some(addressDetails.copy(postcode = None))
    }
  }

  "Calling cacheNameIdDetails" should {
    "save Name Id Details in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheNameIdDetails(nameId))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.nameIdOrganisationDetails shouldBe Some(nameId)

    }
  }

  "Calling cacheNameAndCustomsId" should {
    "save Name and customs Id Details in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheNameAndCustomsId("orgname", customsIdUTR))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.nameIdOrganisationDetails shouldBe Some(NameIdOrganisationMatchModel("orgname", "UTRXXXXX"))
      holder.customsId shouldBe Some(Utr("utrxxxxx"))
    }
  }

  "Calling cacheCustomsIdAndNinoMatch" should {
    "save CustomsId an NinoMatchModel in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheNinoMatchForNoAnswer(Some(ninoMatch)))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.customsId shouldBe None
      holder.formData.ninoMatch shouldBe Some(ninoMatch)
    }
  }

  "Calling cacheUtrMatchForNoAnswer" should {
    "save utrMatch in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheUtrMatchForNoAnswer(Some(utrMatch)))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.formData.utrMatch shouldBe Some(utrMatch)
    }
  }

  "Calling cache Utr match" should {
    "save Utr match in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheUtrMatch(Some(utrMatch)))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.formData.utrMatch shouldBe Some(utrMatch)

    }
  }

  "Calling cache Nino match" should {
    "save Nino match in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheNinoMatch(Some(ninoMatch)))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.formData.ninoMatch shouldBe Some(ninoMatch)

    }
  }

  "Calling cache UtrOrNino match" should {
    "save UtrOrNino match in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheNinoOrUtrChoice(NinoOrUtrChoice(Some("Utr"))))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.formData.ninoOrUtrChoice shouldBe Some("Utr")

    }
  }

  "Calling cache EORI number" should {
    "save EORI number in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheEoriNumber(eoriId))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.eoriNumber shouldBe Some(eoriId)

    }
  }

  "Calling cacheDateEstablished" should {
    "save Date Of Establishment Details in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheDateEstablished(expectedDate))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.dateEstablished shouldBe Some(expectedDate)

    }
  }

  "Calling createContactDetails" should {
    "create and cache SubscriptionDetailsHolder with contact details when not using registered address" in {

      await(subscriptionDetailsHolderService.cacheContactDetails(contactDetailsViewModelWhenNotUsingRegisteredAddress))

      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.contactDetails shouldBe Some(contactDetailsViewModelWhenNotUsingRegisteredAddress)
    }

    "Update SubscriptionDetailsHolder with contact details when using registered address" in {
      when(mockSessionCache.registrationDetails)
        .thenReturn(Future.successful(RegistrationDetailsBuilder.withBusinessAddress(defaultAddress)))

      when(
        mockContactDetailsAdaptor.toContactDetailsModelWithRegistrationAddress(
          contactDetailsViewModelWhenUsingRegisteredAddress,
          defaultAddress
        )
      ).thenReturn(contactDetailsViewModelWhenUsingRegisteredAddress)

      when(mockSessionCache.subscriptionDetails).thenReturn(Future.successful(SubscriptionDetails()))
      when(mockSessionCache.subscriptionDetails).thenReturn(
        Future.successful(SubscriptionDetails(dateEstablished = Some(dateOfEstablishment)))
      )

      await(subscriptionDetailsHolderService.cacheContactDetails(contactDetailsViewModelWhenUsingRegisteredAddress))

      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.contactDetails shouldBe Some(contactDetailsViewModelWhenUsingRegisteredAddress)
      holder.dateEstablished shouldBe Some(dateOfEstablishment)
    }
  }

  "Calling cache CustomsId" should {
    "save UTR CustomsId in frontend cache" in {
      await(subscriptionDetailsHolderService.cacheCustomsId(customsIdUTR))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])
      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.customsId shouldBe Some(customsIdUTR)
    }

    "save EORI CustomsId in frontend cache" in {
      await(subscriptionDetailsHolderService.cacheCustomsId(customsIdEori))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])
      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.customsId shouldBe Some(customsIdEori)
    }
  }

  "Calling cachedCustomsId" should {
    "return Some of customsID when found in subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(Future.successful(SubscriptionDetails(customsId = Option(Utr("12345")))))
      await(subscriptionDetailsHolderService.cachedCustomsId(request)) shouldBe Some(Utr("12345"))
    }

    "return None for customsId when no value found for subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(
        Future.successful(SubscriptionDetails())
      )
      await(subscriptionDetailsHolderService.cachedCustomsId(request)) shouldBe None
    }
  }

  "Calling cachedUtrMatch" should {
    "return Some utrMatch when found in subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(Future.successful(SubscriptionDetails(formData = FormData(utrMatch = Option(utrMatch)))))
      await(subscriptionDetailsHolderService.cachedUtrMatch(request)) shouldBe Some(utrMatch)
    }

    "return None for utrMatch when no value found for subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(
        Future.successful(SubscriptionDetails())
      )
      await(subscriptionDetailsHolderService.cachedUtrMatch(request)) shouldBe None
    }
  }

  "Calling cachedNinoMatch" should {
    "return Some ninoMatch when found in subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(Future.successful(SubscriptionDetails(formData = FormData(ninoMatch = Option(ninoMatch)))))
      await(subscriptionDetailsHolderService.cachedNinoMatch(request)) shouldBe Some(ninoMatch)
    }

    "return None for ninoMatch when no value found for subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(
        Future.successful(SubscriptionDetails())
      )
      await(subscriptionDetailsHolderService.cachedNinoMatch(request)) shouldBe None
    }
  }

  "Calling cachedOrganisationType" should {
    "return Some company when found in subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(
          Future.successful(
            SubscriptionDetails(formData = FormData(organisationType = Option(CdsOrganisationType.Company)))
          )
        )
      await(subscriptionDetailsHolderService.cachedOrganisationType(request)) shouldBe Some(CdsOrganisationType.Company)
    }

    "return None for utrMatch when no value found for subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(
        Future.successful(SubscriptionDetails())
      )
      await(subscriptionDetailsHolderService.cachedOrganisationType(request)) shouldBe None
    }
  }

  "Operating on nameDetails" should {
    val nameOrganisationMatchModel = NameOrganisationMatchModel("testName")

    "save Name Details in frontend cache when cacheNameDetails is called" in {
      await(subscriptionDetailsHolderService.cacheNameDetails(nameOrganisationMatchModel))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])
      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.nameOrganisationDetails shouldBe Some(nameOrganisationMatchModel)
    }

    "retrieve Name Details from frontend cache when cachedNameDetails is called" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(Future.successful(SubscriptionDetails(nameOrganisationDetails = Some(nameOrganisationMatchModel))))
      await(subscriptionDetailsHolderService.cachedNameDetails(request)) shouldBe Some(nameOrganisationMatchModel)
    }
  }

  "Calling cachedNameIdDetails" should {
    "return Some name Id details when found in subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(Future.successful(SubscriptionDetails(nameIdOrganisationDetails = Some(nameId))))
      await(subscriptionDetailsHolderService.cachedNameIdDetails) shouldBe Some(nameId)
    }

    "return None for Name Id details when no value found in subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(
        Future.successful(SubscriptionDetails())
      )
      await(subscriptionDetailsHolderService.cachedNameIdDetails(request)) shouldBe None
    }
  }
  "Calling cache Existing EORI number" should {
    "save ExistingEori in frontend cache" in {
      await(subscriptionDetailsHolderService.cacheExistingEoriNumber(ExistingEori("GB123456789123", "HMRC-CUS-ORG")))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])
      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.existingEoriNumber shouldBe Some(ExistingEori("GB123456789123", "HMRC-CUS-ORG"))
    }
    "throw IllegalArgumentException when eori Number is missing in ExistingEori" in {
      intercept[IllegalArgumentException] {
        await(subscriptionDetailsHolderService.cacheExistingEoriNumber(ExistingEori(None, "HMRC-CUS-ORG")))
      }

    }
  }
  "Calling cachedExistingEoriNumber" should {
    "return Some company when found in subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(
          Future.successful(
            SubscriptionDetails(existingEoriNumber = Some(ExistingEori("GB123456789123", "HMRC-CUS-ORG")))
          )
        )
      await(subscriptionDetailsHolderService.cachedExistingEoriNumber(request)) shouldBe Some(
        ExistingEori("GB123456789123", "HMRC-CUS-ORG")
      )
    }

    "return None for utrMatch when no value found for subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(
        Future.successful(SubscriptionDetails())
      )
      await(subscriptionDetailsHolderService.cachedExistingEoriNumber(request)) shouldBe None
    }
  }

  "Calling cache nameDobDetails" should {
    "save nameDobDetails in frontend cache" in {
      await(
        subscriptionDetailsHolderService
          .cacheNameDobDetails(NameDobMatchModel("fname", Some("mname"), "lname", LocalDate.of(2019, 1, 1)))
      )
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])
      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder = requestCaptor.getValue
      holder.nameDobDetails shouldBe Some(NameDobMatchModel("fname", Some("mname"), "lname", LocalDate.of(2019, 1, 1)))
    }
  }

  "Calliing cacheRegisteredCountry" should {
    "save country value in frontend cache" in {
      await(subscriptionDetailsHolderService.cacheRegisteredCountry(CompanyRegisteredCountry("United Kingdom")))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])
      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val details = requestCaptor.getValue
      details.registeredCompany shouldBe Some(CompanyRegisteredCountry("United Kingdom"))
    }
  }

  "Calling cacheContactAddressDetails" should {
    "save Contact Address Details in frontend cache" in {

      await(subscriptionDetailsHolderService.cacheContactAddressDetails(contactAddressDetails))
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.contactAddress shouldBe Some(contactAddressDetails)

    }

    "should not save emptry strings in postcode field" in {

      await(
        subscriptionDetailsHolderService.cacheContactAddressDetails(contactAddressDetails.copy(postcode = Some("")))
      )
      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(request))
      val holder: SubscriptionDetails = requestCaptor.getValue
      holder.contactAddress shouldBe Some(contactAddressDetails.copy(postcode = None))
    }
  }

  "Calling cachedRegisteredCountry" should {
    "return Some company when found in subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]]))
        .thenReturn(
          Future.successful(SubscriptionDetails(registeredCompany = Some(CompanyRegisteredCountry("United Kingdom"))))
        )
      await(subscriptionDetailsHolderService.cachedRegisteredCountry()) shouldBe Some(
        CompanyRegisteredCountry("United Kingdom")
      )
    }

    "return None for registered country when no value found for subscription Details" in {
      when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(
        Future.successful(SubscriptionDetails())
      )
      await(subscriptionDetailsHolderService.cachedRegisteredCountry()) shouldBe None
    }
  }

}
