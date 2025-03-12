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

package unit.services.registration

import base.UnitSpec
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.TableFor1
import org.scalatest.prop.Tables.Table
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request}
import play.api.test.Helpers._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Address
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{FormData, SubscriptionDetails}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class RegistrationDetailsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  implicit val hc: HeaderCarrier                = mock[HeaderCarrier]
  implicit val mockRequest: Request[AnyContent] = mock[Request[AnyContent]]

  private def startingDate = LocalDate.now

  private val mockSessionCache = mock[SessionCache]

  private val emptyRegDetailsIndividual = RegistrationDetailsIndividual(
    None,
    TaxPayerId(""),
    SafeId(""),
    "",
    Address("", None, None, None, None, ""),
    startingDate
  )

  private val emptyRegDetailsOrganisation = RegistrationDetailsOrganisation(
    None,
    TaxPayerId(""),
    SafeId(""),
    "",
    Address("", None, None, None, None, ""),
    None,
    None
  )

  private val registrationDetailsService = new RegistrationDetailsService(mockSessionCache)(global)

  val individualOrganisationTypes: TableFor1[CdsOrganisationType] =
    Table("organisationType", ThirdCountryIndividual, ThirdCountrySoleTrader, Individual, SoleTrader)

  val nonIndividualOrganisationTypes: TableFor1[CdsOrganisationType] = Table(
    "organisationType",
    Company,
    ThirdCountryOrganisation,
    CdsOrganisationType.Partnership,
    LimitedLiabilityPartnership,
    CharityPublicBodyNotForProfit
  )

  override def beforeEach(): Unit = {
    reset(mockSessionCache)
    when(mockSessionCache.saveRegistrationDetails(any[RegistrationDetails])(any[Request[AnyContent]]))
      .thenReturn(Future.successful(true))
    when(mockSessionCache.subscriptionDetails(any[Request[AnyContent]])).thenReturn(
      Future.successful(SubscriptionDetails())
    )
  }

  "Calling initialiseCacheWithRegistrationDetails" should {

    forAll(individualOrganisationTypes) { organisationType =>
      s"initialise session cache with RegistrationDetailsIndividual for organisation type $organisationType" in {

        await(registrationDetailsService.initialiseCacheWithRegistrationDetails(organisationType))

        val requestCaptor = ArgumentCaptor.forClass(classOf[RegistrationDetails])

        verify(mockSessionCache).saveRegistrationDetails(requestCaptor.capture())(ArgumentMatchers.eq(mockRequest))
        val holder: RegistrationDetails = requestCaptor.getValue

        holder shouldBe emptyRegDetailsIndividual
      }
    }

    forAll(nonIndividualOrganisationTypes) { organisationType =>
      s"initialise session cache with RegistrationDetailsOrganisation for remaining organisation types as $organisationType" in {

        await(registrationDetailsService.initialiseCacheWithRegistrationDetails(organisationType))

        val requestCaptor = ArgumentCaptor.forClass(classOf[RegistrationDetails])

        verify(mockSessionCache).saveRegistrationDetails(requestCaptor.capture())(ArgumentMatchers.eq(mockRequest))
        val holder: RegistrationDetails = requestCaptor.getValue

        holder shouldBe emptyRegDetailsOrganisation
      }
    }
  }

  "initialise" should {
    "initialise session cache with details containing the user location" in {
      when(mockSessionCache.saveSubscriptionDetails(any[SubscriptionDetails])(any[Request[AnyContent]])).thenReturn(
        Future.successful(true)
      )
      await(registrationDetailsService.initialise(UserLocationDetails(Some("GB"))))
      val expectedSubDetailsContainingUserLocation = SubscriptionDetails(
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        FormData(None, None, None, None, Some(UserLocationDetails(Some("GB")))),
        None,
        None
      )

      val requestCaptor = ArgumentCaptor.forClass(classOf[SubscriptionDetails])

      verify(mockSessionCache).saveSubscriptionDetails(requestCaptor.capture())(ArgumentMatchers.eq(mockRequest))
      val holder: SubscriptionDetails = requestCaptor.getValue

      holder shouldBe expectedSubDetailsContainingUserLocation
    }
  }
}
