/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.Save4LaterConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{CacheIds, GroupId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{CachedData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{
  enrolment_pending_against_group_id,
  enrolment_pending_for_user
}

import scala.concurrent.ExecutionContext

class EnrolmentPendingController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  sessionCache: SessionCache,
  save4LaterConnector: Save4LaterConnector,
  enrolmentPendingForUser: enrolment_pending_for_user,
  enrolmentPendingAgainstGroupId: enrolment_pending_against_group_id
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def pendingGroup(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => user: LoggedInUserWithEnrolments =>
        save4LaterConnector
          .get[CacheIds](GroupId(user.groupId).id, CachedData.groupIdKey)
          .flatMap {
            case Some(cacheIds) =>
              sessionCache.remove.map(
                _ =>
                  Ok(enrolmentPendingAgainstGroupId(service, journey, cacheIds.serviceCode.flatMap(Service.withName)))
              )
            case _ => throw new IllegalStateException("No details stored in cache for this group")
          }
    }

  def pendingUser(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => user: LoggedInUserWithEnrolments =>
        save4LaterConnector
          .get[CacheIds](GroupId(user.groupId).id, CachedData.groupIdKey)
          .flatMap {
            case Some(cacheIds) =>
              sessionCache.remove.map(
                _ => Ok(enrolmentPendingForUser(service, cacheIds.serviceCode.flatMap(Service.withName)))
              )
            case _ => throw new IllegalStateException("No details stored in cache for this group")
          }
    }

}
