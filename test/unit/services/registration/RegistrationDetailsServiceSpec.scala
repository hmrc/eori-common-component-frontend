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

package unit.services.registration

import base.UnitSpec

import java.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.prop.Tables.Table
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Address
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class RegistrationDetailsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  implicit val hc          = mock[HeaderCarrier]
  implicit val mockRequest = mock[Request[AnyContent]]

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

  val individualOrganisationTypes =
    Table("organisationType", ThirdCountryIndividual, ThirdCountrySoleTrader, Individual, SoleTrader)

  val nonIndividualOrganisationTypes = Table(
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
}
