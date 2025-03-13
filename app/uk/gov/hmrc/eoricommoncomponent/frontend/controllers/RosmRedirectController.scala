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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.eori_exists_rosm
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{ExistingEori, GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.EnrolmentStoreProxyService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.{Inject, Singleton}

@Singleton
class RosmRedirectController @Inject() (
  authorise: AuthAction,
  eoriExistsView: eori_exists_rosm,
  enrolmentStoreProxyService: EnrolmentStoreProxyService,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  def redirectToStart: Action[AnyContent] = Action { _ =>
    Redirect(routes.ApplicationController.startSubscription(Service.cds))
  }

  def checkEoriNumber: Action[AnyContent] = authorise.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => implicit user: LoggedInUserWithEnrolments =>
      userOrGroupHasAnEori(GroupId(user.groupId))
        .map {
          case Some(eori) => Ok(eoriExistsView(eori.id, Service.cds))
          case None       => Redirect(routes.ApplicationController.startSubscription(Service.cds))
        }
  }

  def userOrGroupHasAnEori(
    groupId: GroupId
  )(implicit hc: HeaderCarrier, user: LoggedInUserWithEnrolments): Future[Option[ExistingEori]] =
    enrolmentStoreProxyService.enrolmentsForGroup(groupId).map {
      existingEoriForUserOrGroup(user, _)
    }

}
