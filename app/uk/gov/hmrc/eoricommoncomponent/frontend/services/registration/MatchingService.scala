/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import java.time.LocalDate
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.DateConverter._
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.MatchingServiceConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Individual
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.matching._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.RequestCommonGenerator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MatchingService @Inject() (
  matchingConnector: MatchingServiceConnector,
  requestCommonGenerator: RequestCommonGenerator,
  detailsCreator: RegistrationDetailsCreator,
  cache: SessionCache,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext) {

  private val UTR  = "UTR"
  private val EORI = "EORI"
  private val NINO = "NINO"

  private val CustomsIdsMap: Map[Class[_ <: CustomsId], String] =
    Map(classOf[Utr] -> UTR, classOf[Eori] -> EORI, classOf[Nino] -> NINO)

  private def convert(customsId: CustomsId, capturedDate: Option[LocalDate])(
    response: MatchingResponse
  ): RegistrationDetails =
    detailsCreator.registrationDetails(response.registerWithIDResponse, customsId, capturedDate)

  def sendOrganisationRequestForMatchingService(implicit
    request: Request[AnyContent],
    loggedInUser: LoggedInUserWithEnrolments,
    headerCarrier: HeaderCarrier,
    originatingService: Service
  ): Future[Boolean] =
    for {
      subscriptionDetailsHolder <- cache.subscriptionDetails
      orgType = EtmpOrganisationType(
        requestSessionData.userSelectedOrganisationType
          .getOrElse(throw DataUnavailableException("OrganisationType number missing"))
      ).toString
      org = Organisation(subscriptionDetailsHolder.name, orgType)
      eori = subscriptionDetailsHolder.eoriNumber.getOrElse(
        throw DataUnavailableException("EORI number missing from subscription")
      )
      result <- matchBusiness(Eori(eori), org, subscriptionDetailsHolder.dateEstablished, GroupId(loggedInUser.groupId))
    } yield result

  def sendIndividualRequestForMatchingService(implicit
    loggedInUser: LoggedInUserWithEnrolments,
    headerCarrier: HeaderCarrier,
    originatingService: Service
  ): Future[Boolean] =
    for {
      subscription <- cache.subscriptionDetails
      nameDob = subscription.nameDobDetails.getOrElse(
        throw DataUnavailableException("Name / DOB missing from subscription")
      )
      eori       = subscription.eoriNumber.getOrElse(throw DataUnavailableException("EORI number missing from subscription"))
      individual = Individual(nameDob.firstName, None, nameDob.lastName, nameDob.dateOfBirth.toString)
      result <- matchIndividualWithId(Eori(eori), individual, GroupId(loggedInUser.groupId))
    } yield result

  def matchBusiness(customsId: CustomsId, org: Organisation, establishmentDate: Option[LocalDate], groupId: GroupId)(
    implicit
    request: Request[AnyContent],
    hc: HeaderCarrier,
    originatingService: Service
  ): Future[Boolean] = {
    def stripKFromUtr: CustomsId => CustomsId = {
      case Utr(id) => Utr(id.stripSuffix("k").stripSuffix("K"))
      case other   => other
    }

    val orgWithCode = org.copy(organisationType = EtmpOrganisationType.orgTypeToEtmpOrgCode(org.organisationType))

    matchingConnector
      .lookup(idAndNameMatchRequest(stripKFromUtr(customsId), orgWithCode))
      .flatMap(
        storeInCacheIfFound(
          convert(customsId, establishmentDate),
          groupId,
          requestSessionData.userSelectedOrganisationType
        )
      )
  }

  def matchIndividualWithId(customsId: CustomsId, individual: Individual, groupId: GroupId)(implicit
    hc: HeaderCarrier,
    originatingService: Service
  ): Future[Boolean] =
    matchingConnector
      .lookup(individualIdMatchRequest(customsId, individual))
      .flatMap(storeInCacheIfFound(convert(customsId, toLocalDate(individual.dateOfBirth)), groupId))

  private def storeInCacheIfFound(
    convert: MatchingResponse => RegistrationDetails,
    groupId: GroupId,
    orgType: Option[CdsOrganisationType] = None
  )(mayBeMatchSuccess: Option[MatchingResponse])(implicit hc: HeaderCarrier): Future[Boolean] =
    mayBeMatchSuccess.map(convert).fold(Future.successful(false)) { details =>
      cache.saveRegistrationDetails(details, groupId, orgType)
    }

  private def idAndNameMatchRequest(customsId: CustomsId, org: Organisation): MatchingRequestHolder =
    MatchingRequestHolder(
      MatchingRequest(
        requestCommonGenerator.generate(),
        RequestDetail(
          nameOfCustomsIdType(customsId),
          customsId.id,
          requiresNameMatch = true,
          isAnAgent = false,
          Some(org)
        )
      )
    )

  private def nameOfCustomsIdType(customsId: CustomsId): String =
    CustomsIdsMap.getOrElse(customsId.getClass, throw new IllegalArgumentException(s"Invalid matching id $customsId"))

  private def individualIdMatchRequest(customsId: CustomsId, individual: Individual): MatchingRequestHolder =
    MatchingRequestHolder(
      MatchingRequest(
        requestCommonGenerator.generate(),
        RequestDetail(
          nameOfCustomsIdType(customsId),
          customsId.id,
          requiresNameMatch = true,
          isAnAgent = false,
          individual = Some(individual)
        )
      )
    )

}
