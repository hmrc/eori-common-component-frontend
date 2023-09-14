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

import java.time.LocalDate
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{AddressViewModel, ContactAddressModel}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.{ContactDetailsAdaptor, RegistrationDetailsCreator}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionBusinessService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future
import scala.util.Random

class SubscriptionBusinessServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier            = mock[HeaderCarrier]
  implicit val request: Request[AnyContent] = mock[Request[AnyContent]]

  private val mockCdsFrontendDataCache       = mock[SessionCache]
  private val mockRegistrationDetailsCreator = mock[RegistrationDetailsCreator]
  private val mockRegistrationDetails        = mock[RegistrationDetails]
  private val mockContactDetailsAdaptor      = mock[ContactDetailsAdaptor]
  private val mockSubscriptionDetailsHolder  = mock[SubscriptionDetails]
  private val mockContactDetailsModel        = mock[ContactDetailsModel]

  private val expectedDate      = LocalDate.now()
  private val maybeExpectedDate = Some(expectedDate)

  val sicCode: Option[String] = Some("someSicCode")

  private val subscriptionBusinessService =
    new SubscriptionBusinessService(mockCdsFrontendDataCache)(global)

  private val eoriNumericLength   = 15
  private val eoriId              = "GB" + Random.nextString(eoriNumericLength)
  val maybeEoriId: Option[String] = Some(eoriId)

  val mayBeCachedAddressViewModel: Option[AddressViewModel] = Some(
    AddressViewModel("Address Line 1", "city", Some("postcode"), "GB")
  )

  val mayBeCachedContactAddressModel: Option[ContactAddressModel] = Some(
    ContactAddressModel("Line 1", Some("Line 2"), "Town", Some("Region"), Some("SE28 1AA"), "GB")
  )

  val nameIdOrganisationDetails: Option[NameIdOrganisationMatchModel] = Some(
    NameIdOrganisationMatchModel("OrgName", "ID")
  )

  val customsIDUTR: Option[Utr] = Some(Utr("ID"))

  val email: Option[String] = Some("OrgName@example.com")

  val emulatedFailure = new UnsupportedOperationException("Emulation of failure")

  override def beforeEach(): Unit = {
    reset(mockCdsFrontendDataCache)
    reset(mockRegistrationDetailsCreator)
    reset(mockRegistrationDetails)
    reset(mockSubscriptionDetailsHolder)
    reset(mockContactDetailsAdaptor)

    when(
      mockCdsFrontendDataCache
        .saveRegistrationDetails(ArgumentMatchers.any[RegistrationDetails])(ArgumentMatchers.any[Request[AnyContent]])
    ).thenReturn(Future.successful(true))

    when(
      mockCdsFrontendDataCache
        .saveSubscriptionDetails(ArgumentMatchers.any[SubscriptionDetails])(ArgumentMatchers.any[Request[AnyContent]])
    ).thenReturn(Future.successful(true))

    val existingHolder = SubscriptionDetails(contactDetails = Some(mock[ContactDetailsModel]))

    when(mockCdsFrontendDataCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(existingHolder)
  }

  "Calling maybeCachedContactDetailsModel" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      val contactDetailsModel = Some(mockContactDetailsModel)

      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.contactDetails).thenReturn(contactDetailsModel)
      await(subscriptionBusinessService.cachedContactDetailsModel) shouldBe contactDetailsModel
      verify(mockCdsFrontendDataCache).subscriptionDetails
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.contactDetails).thenReturn(None)
      await(subscriptionBusinessService.cachedContactDetailsModel) shouldBe None
    }
  }

  "Calling getCachedDateEstablished" should {
    "retrieve any previously cached Date Of Establishment Details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.dateEstablished).thenReturn(Some(expectedDate))
      await(subscriptionBusinessService.getCachedDateEstablished) shouldBe expectedDate

    }

    "throw exception when there are no Date Of Establishment details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.dateEstablished).thenReturn(None)
      val thrown = intercept[DataUnavailableException] {
        await(subscriptionBusinessService.getCachedDateEstablished)
      }
      thrown.getMessage shouldBe "No Date Of Establishment Cached"
    }
  }

  "Calling maybeCachedDateEstablished" should {
    "retrieve cached date established if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.dateEstablished).thenReturn(maybeExpectedDate)
      await(subscriptionBusinessService.maybeCachedDateEstablished) shouldBe maybeExpectedDate
      verify(mockCdsFrontendDataCache).subscriptionDetails
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.dateEstablished).thenReturn(None)
      await(subscriptionBusinessService.maybeCachedDateEstablished) shouldBe None
    }
  }

  "Calling maybeCachedEoriNumber" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(maybeEoriId)
      await(subscriptionBusinessService.cachedEoriNumber) shouldBe maybeEoriId
      verify(mockCdsFrontendDataCache).subscriptionDetails
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.eoriNumber).thenReturn(None)
      await(subscriptionBusinessService.cachedEoriNumber) shouldBe None
    }
  }

  "Calling mayBeCachedAddressViewModel" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.addressDetails).thenReturn(mayBeCachedAddressViewModel)
      await(subscriptionBusinessService.address) shouldBe mayBeCachedAddressViewModel
      verify(mockCdsFrontendDataCache).subscriptionDetails
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.addressDetails).thenReturn(None)
      await(subscriptionBusinessService.address) shouldBe None
    }
  }

  "Calling maybeCachedNameIdViewModel" should {
    "retrieve cached data if already stored in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails).thenReturn(nameIdOrganisationDetails)
      await(subscriptionBusinessService.cachedNameIdOrganisationViewModel) shouldBe nameIdOrganisationDetails
      verify(mockCdsFrontendDataCache).subscriptionDetails
    }

    "return None if no data has been found in the cache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails).thenReturn(None)
      await(subscriptionBusinessService.cachedNameIdOrganisationViewModel) shouldBe None
    }
  }

  "Calling getCachedAddressViewModel" should {
    "retrieve any previously cached Address Details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.addressDetails).thenReturn(mayBeCachedAddressViewModel)
      await(subscriptionBusinessService.addressOrException) shouldBe mayBeCachedAddressViewModel.get
    }

    "throw exception when cache address details is not saved in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.addressDetails).thenReturn(None)
      val thrown = intercept[DataUnavailableException] {
        await(subscriptionBusinessService.addressOrException)
      }
      thrown.getMessage shouldBe "No Address Details Cached"
    }
  }

  "Calling getCachedAddressViewModel" should {
    "retrieve any previously cached Named Id from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails).thenReturn(nameIdOrganisationDetails)
      await(subscriptionBusinessService.getCachedNameIdViewModel) shouldBe nameIdOrganisationDetails.get
    }

    "throw exception when cache Name Id is not saved in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameIdOrganisationDetails).thenReturn(None)
      val thrown = intercept[DataUnavailableException] {
        await(subscriptionBusinessService.getCachedNameIdViewModel)
      }
      thrown.getMessage shouldBe "No Name/Utr/Id Details Cached"
    }
  }

  "Calling getCachedCustomsId" should {
    "retrieve any previously cached Named Id from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.customsId).thenReturn(customsIDUTR)
      await(subscriptionBusinessService.getCachedCustomsId) shouldBe customsIDUTR
    }

    "return None when cache Name Id is not saved in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.customsId).thenReturn(None)
      await(subscriptionBusinessService.getCachedCustomsId) shouldBe None
    }
  }

  "Calling getCachedNameViewModel" should {
    "retrieve any previously cached organisation details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameOrganisationDetails).thenReturn(Some(NameOrganisationMatchModel("name")))
      await(subscriptionBusinessService.getCachedNameViewModel) shouldBe NameOrganisationMatchModel("name")
    }

    "throw exception when there is no organisation details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameOrganisationDetails).thenReturn(None)
      val thrown = intercept[DataUnavailableException] {
        await(subscriptionBusinessService.getCachedNameViewModel)
      }
      thrown.getMessage shouldBe "No Name Cached"
    }
  }

  "Calling getCachedSubscriptionNameDobViewModel" should {
    "retrieve any previously cached dob details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameDobDetails)
        .thenReturn(Some(NameDobMatchModel("fname", Some("mName"), "lname", LocalDate.parse("2019-01-01"))))
      await(subscriptionBusinessService.getCachedSubscriptionNameDobViewModel) shouldBe NameDobMatchModel(
        "fname",
        Some("mName"),
        "lname",
        LocalDate.parse("2019-01-01")
      )
    }

    "throw exception when there is no dob details in the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.nameDobDetails).thenReturn(None)
      val thrown = intercept[DataUnavailableException] {
        await(subscriptionBusinessService.getCachedSubscriptionNameDobViewModel)
      }
      thrown.getMessage shouldBe "No Name/Dob Details Cached"
    }
  }

  "Calling contactAddress" should {
    "retrieve any previously cached Address Details from the cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.contactAddress).thenReturn(mayBeCachedContactAddressModel)
      await(subscriptionBusinessService.contactAddress) shouldBe mayBeCachedContactAddressModel
      verify(mockCdsFrontendDataCache).subscriptionDetails
    }

    "throw exception when cache address details is not saved in cdsFrontendCache" in {
      when(mockCdsFrontendDataCache.subscriptionDetails).thenReturn(mockSubscriptionDetailsHolder)
      when(mockSubscriptionDetailsHolder.contactAddress).thenReturn(None)
      await(subscriptionBusinessService.contactAddress) shouldBe None
    }
  }
}
