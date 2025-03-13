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

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{GroupId, InternalId, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.email.EmailJourneyService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.{Save4LaterService, UserGroupIdSubscriptionStatusCheckService}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{
  enrolment_pending_against_group_id,
  enrolment_pending_for_user
}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  save4LaterService: Save4LaterService,
  userGroupIdSubscriptionStatusCheckService: UserGroupIdSubscriptionStatusCheckService,
  enrolmentPendingForUser: enrolment_pending_for_user,
  enrolmentPendingAgainstGroupId: enrolment_pending_against_group_id,
  emailJourneyService: EmailJourneyService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  def form(implicit service: Service, subscribeJourney: SubscribeJourney): Action[AnyContent] =
    authAction.enrolledUserClearingCacheOnCompletionAction {
      implicit request => implicit user: LoggedInUserWithEnrolments =>
        userGroupIdSubscriptionStatusCheckService
          .checksToProceed(GroupId(user.groupId), InternalId(user.internalId))(
            emailJourneyService.continue(service, subscribeJourney)
          )(userIsInProcess(service))(otherUserWithinGroupIsInProcess(service))
    }

  private def userIsInProcess(
    service: Service
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    save4LaterService
      .fetchProcessingService(GroupId(user.groupId))
      .map(processingService => Ok(enrolmentPendingForUser(service, processingService)))

  private def otherUserWithinGroupIsInProcess(
    service: Service
  )(implicit request: Request[AnyContent], user: LoggedInUserWithEnrolments): Future[Result] =
    save4LaterService
      .fetchProcessingService(GroupId(user.groupId))
      .map(processingService => Ok(enrolmentPendingAgainstGroupId(service, processingService)))

}
