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
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{
  EnrolmentResponse,
  Eori,
  ExistingEori,
  LoggedInUserWithEnrolments
}
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

  private def getEnrolmentInUse(
    service: Service,
    groupEnrolments: List[EnrolmentResponse],
    loggedInUser: LoggedInUserWithEnrolments
  )(implicit hc: HeaderCarrier): Future[Option[ExistingEori]] =
    existingEoriForUserOrGroup(loggedInUser, groupEnrolments) match {
      case Some(existingEori) => enrolmentStoreProxyService.isEnrolmentInUse(service, existingEori)
      case _                  => Future.successful(None)
    }

  private def isUserEnrolledToOtherService(loggedInUser: LoggedInUserWithEnrolments) =
    enrolledForOtherServices(loggedInUser).isDefined

  private def isGroupEnrolledToOtherService(groupEnrolments: List[EnrolmentResponse])(implicit request: Request[_]) = {
    def checkSupportedServiceEnrolments(groupEnrolments: List[EnrolmentResponse]) = {
      val serviceList = Service.supportedServicesMap.values.toList
      groupEnrolments.find(enrolment => serviceList.contains(enrolment.service))
    }

    checkSupportedServiceEnrolments(groupEnrolments) match {
      case Some(groupEnrolment) if groupEnrolment.eori.isDefined =>
        cache.saveGroupEnrolment(groupEnrolment).map(_ => true)
      case None => Future.successful(false)
    }
  }

  private def isUserOrGroupEnrolledToOtherServices(
    loggedInUser: LoggedInUserWithEnrolments,
    groupEnrolments: List[EnrolmentResponse]
  )(implicit request: Request[_]) =
    if (isUserEnrolledToOtherService(loggedInUser))
      Future.successful(true)
    else
      isGroupEnrolledToOtherService(groupEnrolments).flatMap { isGroupEnrolled =>
        if (isGroupEnrolled) Future.successful(true)
        else Future.successful(false)
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
            isUserOrGroupEnrolledToOtherServices(loggedInUser, groupEnrolments).map {
              isEnrolled => if (isEnrolled) Right(AutoEnrolment) else Right(LongJourney)
            }
        }
    }

}

sealed trait JourneyError
case object EnrolmentExistsGroup extends JourneyError
case object EnrolmentExistsUser  extends JourneyError
