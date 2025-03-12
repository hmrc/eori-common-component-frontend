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

package uk.gov.hmrc.eoricommoncomponent.frontend.services

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{EnrolmentExtractor, GroupEnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{
  EnrolmentResponse,
  Eori,
  ExistingEori,
  LoggedInUserWithEnrolments
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service.supportedServiceEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, JourneyType, LongJourney, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EnrolmentJourneyService @Inject() (
  cache: SessionCache,
  groupEnrolment: GroupEnrolmentExtractor,
  enrolmentStoreProxyService: EnrolmentStoreProxyService
)(implicit ec: ExecutionContext)
    extends EnrolmentExtractor {

  private def getEnrolmentInUse(
    service: Service,
    groupEnrolments: List[EnrolmentResponse],
    loggedInUser: LoggedInUserWithEnrolments
  )(implicit hc: HeaderCarrier): Future[Option[ExistingEori]] =
    existingEoriForUserOrGroup(loggedInUser, groupEnrolments) match {
      case Some(existingEori) => enrolmentStoreProxyService.isEnrolmentInUse(service, existingEori)
      case _                  => Future.successful(None)
    }

  private def groupEnrolledForOtherServices(groupEnrolments: List[EnrolmentResponse]) =
    groupEnrolments.find(enrolment => supportedServiceEnrolments.contains(enrolment.service)) match {
      case Some(groupEnrolment) if groupEnrolment.eori.isDefined => Some(groupEnrolment)
      case _                                                     => None
    }

  def getJourney(loggedInUser: LoggedInUserWithEnrolments, groupId: String, service: Service)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[Either[JourneyError, JourneyType]] =
    groupEnrolment.groupIdEnrolments(groupId).flatMap {
      case groupEnrolments if groupEnrolments.exists(_.service == service.enrolmentKey) =>
        Future.successful(Left(EnrolmentExistsGroup))
      case groupEnrolments =>
        getEnrolmentInUse(service, groupEnrolments, loggedInUser).flatMap {
          case Some(existingEori) =>
            cache.saveEori(Eori(existingEori.id)).map(_ => Left(EnrolmentExistsUser))
          case None =>
            if (enrolledForOtherServices(loggedInUser).isDefined)
              Future.successful(Right(AutoEnrolment))
            else
              groupEnrolledForOtherServices(groupEnrolments) match {
                case Some(grEnr) => cache.saveGroupEnrolment(grEnr).map(_ => Right(AutoEnrolment))
                case None        => Future.successful(Right(LongJourney))
              }
        }
    }

}

sealed trait JourneyError
case object EnrolmentExistsGroup extends JourneyError
case object EnrolmentExistsUser  extends JourneyError
