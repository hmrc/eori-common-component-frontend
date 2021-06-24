/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.migration_success
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Sub02Controller @Inject() (
  authAction: AuthAction,
  requestSessionData: RequestSessionData,
  sessionCache: SessionCache,
  subscriptionDetailsService: SubscriptionDetailsService,
  mcc: MessagesControllerComponents,
  migrationSuccessView: migration_success
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  def migrationEnd(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithServiceAction {
    implicit request => user: LoggedInUserWithEnrolments =>
      activatedEnrolmentForService(user, service) match {
        case Some(_) => Future.successful(Redirect(CompletedEnrolmentController.enrolSuccess(service)))
        case _ =>
          if (UserLocation.isRow(requestSessionData))
            subscriptionDetailsService.cachedCustomsId flatMap {
              case Some(_) => renderPageWithName(service)
              case _       => renderPageWithNameRow(service)
            }
          else renderPageWithName(service)
      }
  }

  private def renderPageWithName(service: Service)(implicit hc: HeaderCarrier, request: Request[_]): Future[Result] =
    for {
      name <- sessionCache.registerWithEoriAndIdResponse.map(
        _.responseDetail.flatMap(_.responseData.map(_.trader.fullName))
      )
      sub02Outcome <- sessionCache.sub02Outcome
      _            <- sessionCache.remove
      _ <- sessionCache.saveSub02Outcome(
        Sub02Outcome(sub02Outcome.processedDate, name.getOrElse(""), sub02Outcome.eori)
      )
    } yield Ok(
      migrationSuccessView(
        sub02Outcome.eori,
        name.getOrElse(throw new IllegalStateException("Name not populated from reg06")),
        sub02Outcome.processedDate,
        service
      )
    ).withSession(newUserSession)

  private def renderPageWithNameRow(service: Service)(implicit hc: HeaderCarrier, request: Request[_]): Future[Result] =
    for {
      sub02Outcome <- sessionCache.sub02Outcome
      _            <- sessionCache.remove
      _            <- sessionCache.saveSub02Outcome(sub02Outcome)
    } yield Ok(
      migrationSuccessView(sub02Outcome.eori, sub02Outcome.fullName, sub02Outcome.processedDate, service)
    ).withSession(newUserSession)

}
