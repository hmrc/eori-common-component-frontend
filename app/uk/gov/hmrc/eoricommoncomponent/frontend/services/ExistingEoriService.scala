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

import play.api.http.Status.NO_CONTENT
import play.api.i18n.Lang.logger
import play.api.i18n.Messages
import play.api.mvc.Results.{Ok, Redirect}
import play.api.mvc.{Call, Request, Result}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.EnrolmentExtractor
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney, Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{EnrolmentService, MissingEnrolmentException}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{eori_enrol_success, has_existing_eori}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ExistingEoriService @Inject() (
  cache: SessionCache,
  hasExistingEoriView: has_existing_eori,
  enrolmentService: EnrolmentService,
  enrolSuccessView: eori_enrol_success
)(implicit ec: ExecutionContext)
    extends EnrolmentExtractor {

  def onDisplay(
    service: Service
  )(implicit request: Request[_], loggedInUser: LoggedInUserWithEnrolments, msg: Messages): Future[Result] =
    for {
      eori <- getExistingEORI
      _    <- cache.saveEori(Eori(eori.id))
      route = getRoute(service)
    } yield Ok(hasExistingEoriView(service, eori.id, route))

  def onEnrol(
    service: Service
  )(implicit request: Request[_], loggedInUser: LoggedInUserWithEnrolments, hc: HeaderCarrier): Future[Result] =
    getExistingEORI.flatMap { eori =>
      enrolmentService.enrolWithExistingEnrolment(eori, service).map {
        case NO_CONTENT => Redirect(routes.HasExistingEoriController.enrolSuccess(service))
        case status     => throw FailedEnrolmentException(status)
      } recover {
        case e: MissingEnrolmentException =>
          logger.info(s"EnrolWithExistingEnrolment : ${e.getMessage}")
          Redirect(
            routes.EmailController.form(service, subscribeJourney = SubscribeJourney(LongJourney))
          ) //If Sync Enrolment fails we want to try the Long Journey
      }
    }

  def onEnrolSuccess(
    service: Service
  )(implicit request: Request[_], loggedInUser: LoggedInUserWithEnrolments, msg: Messages): Future[Result] =
    getExistingEORI.map(eori => Ok(enrolSuccessView(eori.id, service)))

  private def getRoute(service: Service): Call =
    if (service.enrolmentKey == Service.cds.enrolmentKey)
      routes.EmailController.form(service, subscribeJourney = SubscribeJourney(AutoEnrolment))
    else
      routes.HasExistingEoriController.enrol(service)

  private def getExistingEORI(implicit
    loggedInUser: LoggedInUserWithEnrolments,
    request: Request[_]
  ): Future[ExistingEori] =
    enrolledForService(loggedInUser, Service.cds) match {
      case Some(eori) => Future.successful(eori)
      case None =>
        enrolledForOtherServices(loggedInUser) match {
          case Some(eori) => Future.successful(eori)
          case None =>
            cache.groupEnrolment.map { enrolment =>
              ExistingEori(enrolment.eori.getOrElse(throw DataUnavailableException("No EORI found")), enrolment.service)
            }
        }
    }

}

case class FailedEnrolmentException(status: Int) extends Exception(s"Enrolment failed with status $status")
