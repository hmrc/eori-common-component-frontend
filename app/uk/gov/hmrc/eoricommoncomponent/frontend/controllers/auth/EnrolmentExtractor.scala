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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth

import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.ExistingEori

trait EnrolmentExtractor {

  private val EoriIdentifier: String = "EORINumber"

  private def identifierFor(
    enrolmentKey: String,
    identifierName: String,
    loggedInUser: LoggedInUserWithEnrolments
  ): Option[ExistingEori] =
    loggedInUser.enrolments
      .getEnrolment(enrolmentKey)
      .map(
        enrolment =>
          ExistingEori(
            enrolment
              .getIdentifier(identifierName)
              .map(identifier => identifier.value),
            enrolment.key
          )
      )

  private def identifierForOtherEnrollments(
    identifierName: String,
    loggedInUser: LoggedInUserWithEnrolments
  ): Option[ExistingEori] = {

    val activatedState    = "Activated"
    val serviceList       = Service.supportedServicesMap.values.toList
    val serviceEnrolments = serviceList.map(_.enrolmentKey)
    loggedInUser.enrolments.enrolments.find(x => x.state == activatedState && serviceEnrolments.contains(x.key))
      .map(
        enrolment =>
          ExistingEori(
            enrolment
              .getIdentifier(identifierName)
              .map(identifier => identifier.value),
            enrolment.key
          )
      )

  }

  def enrolledForService(loggedInUser: LoggedInUserWithEnrolments, service: Service): Option[ExistingEori] =
    identifierFor(service.enrolmentKey, EoriIdentifier, loggedInUser)

  def enrolledForOtherServices(loggedInUser: LoggedInUserWithEnrolments): Option[ExistingEori] =
    identifierForOtherEnrollments(EoriIdentifier, loggedInUser)

  def activatedEnrolmentForService(loggedInUser: LoggedInUserWithEnrolments, service: Service): Option[Eori] =
    loggedInUser.enrolments
      .getEnrolment(service.enrolmentKey)
      .flatMap { enrolment =>
        if (enrolment.state.equalsIgnoreCase("Activated"))
          enrolment.getIdentifier(EoriIdentifier).map(identifier => Eori(identifier.value))
        else None
      }

  def enrolledCtUtr(loggedInUser: LoggedInUserWithEnrolments): Option[Utr] =
    identifierFor("IR-CT", "UTR", loggedInUser).map(existingEori => Utr(existingEori.id))

  def enrolledSaUtr(loggedInUser: LoggedInUserWithEnrolments): Option[Utr] =
    identifierFor("IR-SA", "UTR", loggedInUser).map(existingEori => Utr(existingEori.id))

  def enrolledNino(loggedInUser: LoggedInUserWithEnrolments): Option[Nino] =
    identifierFor("HMRC-NI", "NINO", loggedInUser).map(existingEori => Nino(existingEori.id))

  def existingEoriForUserOrGroup(
    loggedInUser: LoggedInUserWithEnrolments,
    groupEnrolments: List[EnrolmentResponse]
  ): Option[ExistingEori] = {
    val userEnrolmentWithEori = loggedInUser.enrolments.enrolments.find(_.identifiers.exists(_.key == EoriIdentifier))
    val existingEoriForUser = userEnrolmentWithEori.map(
      enrolment => ExistingEori(enrolment.getIdentifier(EoriIdentifier).map(_.value), enrolment.key)
    )
    existingEoriForUser.orElse(
      groupEnrolments.find(_.eori.exists(_.nonEmpty)).map(enrolment => ExistingEori(enrolment.eori, enrolment.service))
    )
  }

}
