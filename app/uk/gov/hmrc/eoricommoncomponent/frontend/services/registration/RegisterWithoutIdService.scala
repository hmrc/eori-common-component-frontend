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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.registration

import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.RegisterWithoutIdConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.*
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.{Address, Individual}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RequestCommonGenerator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.http.HeaderCarrier
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterWithoutIdService @Inject() (
  connector: RegisterWithoutIdConnector,
  requestCommonGenerator: RequestCommonGenerator,
  detailsCreator: RegistrationDetailsCreator,
  sessionCache: SessionCache
)(implicit ec: ExecutionContext)
    extends Logging {

  def registerOrganisation(
    subDetails: SubscriptionDetails,
    loggedInUser: LoggedInUserWithEnrolments,
    orgType: Option[CdsOrganisationType]
  )(implicit hc: HeaderCarrier, request: Request[_]): Future[RegisterWithoutIDResponse] = {
    val regResponse =
      for {
        contactDetail <- subDetails.contactDetails
        euAddress     <- subDetails.euEoriRegisteredAddress
        orgName = subDetails.name
        address = Address(
          euAddress.lineOne,
          None,
          Some(euAddress.lineThree),
          None,
          euAddress.postcode,
          euAddress.country
        )
        reqDetails = RegisterWithoutIdReqDetails.organisation(
          organisation = OrganisationName(orgName),
          address = address,
          contactDetail = contactDetail
        )
        requestWithoutId = RegisterWithoutIDRequest(requestCommonGenerator.generate(), reqDetails)
      } yield for {
        response <- connector.register(requestWithoutId)
        registrationDetails = detailsCreator.registrationDetails(
          response,
          orgName,
          createSixLineAddress(address)
        )
        _ <- save(registrationDetails, loggedInUser, orgType)
      } yield response

    regResponse.fold(
      Future.failed[RegisterWithoutIDResponse] {
        val error = "registerIndividual: Missing details"
        // $COVERAGE-OFF$Loggers
        logger.warn(error)
        // $COVERAGE-ON
        DataUnavailableException(error)
      }
    )(identity)

  }

  def registerIndividual(
    subDetails: SubscriptionDetails,
    loggedInUser: LoggedInUserWithEnrolments,
    orgType: Option[CdsOrganisationType]
  )(implicit request: Request[_], hc: HeaderCarrier): Future[RegisterWithoutIDResponse] = {
    val regResponse =
      for {
        names         <- subDetails.euNameDetails
        dob           <- subDetails.dateEstablished
        contactDetail <- subDetails.contactDetails
        euAddress     <- subDetails.euEoriRegisteredAddress
        individualNameAndDateOfBirth = IndividualNameAndDateOfBirth(names.givenName, None, names.familyName, dob)
        individual                   = Individual.withLocalDate(names.givenName, names.familyName, dob)
        address = Address(
          euAddress.lineOne,
          None,
          Some(euAddress.lineThree),
          None,
          euAddress.postcode,
          euAddress.country
        )
        reqDetails = RegisterWithoutIdReqDetails.individual(
          individual = individual,
          address = address,
          contactDetail = contactDetail
        )
        requestWithoutId = RegisterWithoutIDRequest(requestCommonGenerator.generate(), reqDetails)
      } yield for {
        response <- connector.register(requestWithoutId)
        registrationDetails = detailsCreator.registrationDetails(
          response,
          individualNameAndDateOfBirth,
          createSixLineAddress(address)
        )
        _ <- save(registrationDetails, loggedInUser, orgType)
      } yield response

    regResponse.fold(
      Future.failed[RegisterWithoutIDResponse] {
        val error = "registerIndividual: Missing details"
        // $COVERAGE-OFF$Loggers
        logger.warn(error)
        // $COVERAGE-ON
        DataUnavailableException(error)
      }
    )(identity)
  }

  private def save(
    registrationDetails: RegistrationDetails,
    loggedInUser: LoggedInUserWithEnrolments,
    orgType: Option[CdsOrganisationType]
  )(implicit hc: HeaderCarrier, request: Request[_]) =
    if (registrationDetails.safeId.id.nonEmpty)
      sessionCache.saveRegistrationDetailsWithoutId(
        registrationDetails: RegistrationDetails,
        GroupId(loggedInUser.groupId),
        orgType
      )
    else
      sessionCache.saveRegistrationDetails(registrationDetails: RegistrationDetails)

  private def createSixLineAddress(addr: Address): SixLineAddressMatchModel =
    SixLineAddressMatchModel(
      addr.addressLine1,
      addr.addressLine2,
      addr.addressLine3.getOrElse(""),
      addr.addressLine4,
      addr.postalCode,
      addr.countryCode
    )

}
