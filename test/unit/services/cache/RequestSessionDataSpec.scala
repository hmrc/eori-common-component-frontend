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

package unit.services.cache

import base.UnitSpec
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContent, Request, Session}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType.CompanyId
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, RequestSessionData}

class RequestSessionDataSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {

  private val requestSessionData                        = new RequestSessionData()
  private implicit val mockRequest: Request[AnyContent] = mock[Request[AnyContent]]
  private val existingSessionValues                     = Map("someExistingValue" -> "value")
  private val existingSession: Session                  = Session(existingSessionValues)
  private val mockOrganisationType                      = mock[CdsOrganisationType]
  private val testOrganisationTypeId                    = "arbitrary_organisation_type"

  override def beforeEach(): Unit = {
    when(mockRequest.session).thenReturn(existingSession)
    when(mockOrganisationType.id).thenReturn(testOrganisationTypeId)
  }

  "RequestSessionData" should {
    "add correct flow name in request cache" in {
      val newSession = requestSessionData.storeUserSubscriptionFlow(OrganisationFlow, "")
      newSession shouldBe Session(
        existingSessionValues ++ Map("subscription-flow" -> OrganisationFlow.name, "uri-before-subscription-flow" -> "")
      )
    }

    "return correct flow cached" in {
      when(mockRequest.session).thenReturn(Session(Map("subscription-flow" -> OrganisationFlow.name)))
      requestSessionData.userSubscriptionFlow shouldBe OrganisationFlow
    }

    "return Organisation Type cached" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-organisation-type" -> "company")))
      requestSessionData.userSelectedOrganisationType shouldBe Some(CdsOrganisationType(CompanyId))
    }

    "return Session with Organisation Type added" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-organisation-type" -> "company")))
      val session =
        requestSessionData.sessionWithOrganisationTypeAdded(mockRequest.session, CdsOrganisationType(CompanyId))
      session.data.get("selected-organisation-type") shouldBe Some("company")
    }

    "return User Location cached" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-user-location" -> "uk")))
      requestSessionData.selectedUserLocation shouldBe Some("uk")
    }
    "return Session with User Location added" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-user-location" -> "uk")))
      val session =
        requestSessionData.sessionWithUserLocationAdded("uk")
      session.data.get("selected-user-location") shouldBe Some("uk")
    }

    "return islands as selected userLocation for Channel Islands and third-country for eu from cache" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-user-location" -> "islands")))
      requestSessionData.selectedUserLocation shouldBe Some("islands")
      when(mockRequest.session).thenReturn(Session(Map("selected-user-location" -> "eu")))
      requestSessionData.selectedUserLocation shouldBe Some("third-country")
    }

    "return true when isPartnership is invoked if the orgType is of type partnership" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-organisation-type" -> "partnership")))
      requestSessionData.isPartnership shouldBe true
      when(mockRequest.session).thenReturn(
        Session(Map("selected-organisation-type" -> "limited-liability-partnership"))
      )
      requestSessionData.isPartnership shouldBe true
    }
    "return false when isPartnership is invoked if the orgType is Not present in session data" in {
      requestSessionData.isPartnership shouldBe false
      requestSessionData.isPartnership shouldBe false
    }
    "return true when isCompany is invoked if the orgType is company" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-organisation-type" -> "company")))
      requestSessionData.isCompany shouldBe true
    }
    "return false when isCompany is invoked if the orgType is Not present in session data" in {
      requestSessionData.isCompany shouldBe false
    }

    "return true when isIndividual is invoked if the orgType is of type Individual" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-organisation-type" -> "individual")))
      requestSessionData.isIndividualOrSoleTrader shouldBe true
      when(mockRequest.session).thenReturn(Session(Map("selected-organisation-type" -> "sole-trader")))
      requestSessionData.isIndividualOrSoleTrader shouldBe true
      when(mockRequest.session).thenReturn(Session(Map("selected-organisation-type" -> "third-country-individual")))
      requestSessionData.isIndividualOrSoleTrader shouldBe true
      when(mockRequest.session).thenReturn(Session(Map("selected-organisation-type" -> "third-country-sole-trader")))
      requestSessionData.isIndividualOrSoleTrader shouldBe true
    }
    "return true when isIndividual is invoked if the orgType is Not present in session data" in {
      requestSessionData.isIndividualOrSoleTrader shouldBe false
      requestSessionData.isIndividualOrSoleTrader shouldBe false
      requestSessionData.isIndividualOrSoleTrader shouldBe false
      requestSessionData.isIndividualOrSoleTrader shouldBe false
    }

    "throw exception when flow is not cached" in {
      when(mockRequest.session).thenReturn(Session())
      val caught = intercept[DataUnavailableException](requestSessionData.userSubscriptionFlow)
      caught.getMessage shouldBe "Subscription flow is not cached"
    }

    "add organisation type to session" in {
      val newSession = requestSessionData.sessionWithOrganisationTypeAdded(existingSession, mockOrganisationType)
      newSession shouldBe Session(existingSessionValues + ("selected-organisation-type" -> testOrganisationTypeId))
    }

    "return session third country" in {
      when(mockRequest.session).thenReturn(Session(Map("selected-user-location" -> "iom")))
      requestSessionData.selectedUserLocationWithIslands shouldBe Some("iom")
    }

    "return true for isUKJourney method" when {

      "user is during organisation UK subscription journey" in {

        when(mockRequest.session).thenReturn(Session(Map("subscription-flow" -> OrganisationFlow.name)))

        requestSessionData.isUKJourney shouldBe true
      }

      "user is during sole trader UK subscription journey" in {

        when(mockRequest.session).thenReturn(Session(Map("subscription-flow" -> SoleTraderFlow.name)))

        requestSessionData.isUKJourney shouldBe true
      }

      "user is during individual UK subscription journey" in {

        when(mockRequest.session).thenReturn(Session(Map("subscription-flow" -> IndividualFlow.name)))

        requestSessionData.isUKJourney shouldBe true
      }
    }

    "return false for isUKJourney method" when {

      "user is on different journey" in {

        when(mockRequest.session).thenReturn(Session(Map("subscription-flow" -> RowOrganisationFlow.name)))

        requestSessionData.isUKJourney shouldBe false
      }

      "session does not have subscription flow in session data" in {

        requestSessionData.isUKJourney shouldBe false
      }
    }
  }
}
