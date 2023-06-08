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

package uk.gov.hmrc.eoricommoncomponent.frontend.services

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{EnrolmentExtractor, GroupEnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{Eori, ExistingEori, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, JourneyType, LongJourney, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationService @Inject() (
  cache: SessionCache,
  groupEnrolment: GroupEnrolmentExtractor,
  enrolmentStoreProxyService: EnrolmentStoreProxyService
)(implicit ec: ExecutionContext)
    extends EnrolmentExtractor {

  private def isEnrolmentInUse(service: Service, groupId: String, loggedInUser: LoggedInUserWithEnrolments)(implicit
    hc: HeaderCarrier
  ): Future[Option[ExistingEori]] =
    groupEnrolment.groupIdEnrolments(groupId).flatMap { groupEnrolments =>
      existingEoriForUserOrGroup(loggedInUser, groupEnrolments) match {
        case Some(existingEori) => enrolmentStoreProxyService.isEnrolmentInUse(service, existingEori)
        case _                  => Future.successful(None)
      }
    }

  private def isUserEnrolledFor(loggedInUser: LoggedInUserWithEnrolments, service: Service): Boolean =
    enrolledForService(loggedInUser, service).isDefined

  private def isUserEnrolledForOtherServices(loggedInUser: LoggedInUserWithEnrolments): Boolean =
    enrolledForOtherServices(loggedInUser).isDefined

  private def checkAllServiceEnrolments(loggedInUser: LoggedInUserWithEnrolments, groupId: String)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[Either[JourneyError, JourneyType]] =
    if (isUserEnrolledForOtherServices(loggedInUser))
      Future.successful(Right(AutoEnrolment))
    else
      groupEnrolment.checkAllServiceEnrolments(groupId).flatMap {
        case Some(groupEnrolment) if groupEnrolment.eori.isDefined =>
          cache.saveGroupEnrolment(groupEnrolment).map(_ => Right(AutoEnrolment))
        case _ =>
          Future.successful(Right(LongJourney))
      }

  private def checkCDSEnrolments(loggedInUser: LoggedInUserWithEnrolments, groupId: String)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[Either[JourneyError, JourneyType]] =
    if (isUserEnrolledFor(loggedInUser, Service.cds))
      Future.successful(Right(AutoEnrolment))
    else
      groupEnrolment.groupIdEnrolmentTo(groupId, Service.cds).flatMap {
        case Some(groupEnrolment) if groupEnrolment.eori.isDefined =>
          cache.saveGroupEnrolment(groupEnrolment).map(_ => Right(AutoEnrolment))
        case _ =>
          checkAllServiceEnrolments(loggedInUser, groupId)
      }

  def getJourney(loggedInUser: LoggedInUserWithEnrolments, groupId: String, service: Service)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[Either[JourneyError, JourneyType]] =
    groupEnrolment.hasGroupIdEnrolmentTo(groupId, service).flatMap {
      case true =>
        Future.successful(Left(EnrolmentExistsGroup))
      case false =>
        isEnrolmentInUse(service, groupId, loggedInUser).flatMap {
          case Some(existingEori) =>
            cache.saveEori(Eori(existingEori.id)).map(_ => Left(EnrolmentExistsUser))
          case None =>
            checkCDSEnrolments(loggedInUser, groupId)
        }

    }

}

sealed trait JourneyError
case object EnrolmentExistsGroup extends JourneyError
case object EnrolmentExistsUser  extends JourneyError
