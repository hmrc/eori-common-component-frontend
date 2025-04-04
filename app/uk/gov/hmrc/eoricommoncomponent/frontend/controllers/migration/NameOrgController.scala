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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.NameDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.nameOrganisationForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.nameOrg

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameOrgController @Inject() (
  authAction: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  sessionCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  nameOrgView: nameOrg,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.cachedNameOrganisationViewModel flatMap { maybeCachedNameViewModel =>
        populateOkView(maybeCachedNameViewModel, isInReviewMode = false, service)
      }
    }

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedNameViewModel flatMap { nameOrgMatchModel =>
        populateOkView(Some(nameOrgMatchModel), isInReviewMode = true, service)
      }
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      nameOrganisationForm.bindFromRequest().fold(
        formWithErrors =>
          sessionCache.registrationDetails map { registrationDetails =>
            BadRequest(nameOrgView(formWithErrors, registrationDetails, isInReviewMode, service))
          },
        formData => storeNameDetails(formData, isInReviewMode, service)
      )
    }

  private def populateOkView(
    maybeNameViewModel: Option[NameOrganisationMatchModel],
    isInReviewMode: Boolean,
    service: Service
  )(implicit request: Request[AnyContent]): Future[Result] = {
    lazy val form =
      maybeNameViewModel.fold(nameOrganisationForm)(nameOrganisationForm.fill)

    sessionCache.registrationDetails map { registrationDetails =>
      Ok(nameOrgView(form, registrationDetails, isInReviewMode, service))
    }
  }

  private def storeNameDetails(formData: NameOrganisationMatchModel, inReviewMode: Boolean, service: Service)(implicit
    request: Request[AnyContent]
  ): Future[Result] = {
    for {
      _             <- subscriptionDetailsService.cacheNameDetails(formData)
      nameIdDetails <- subscriptionDetailsService.cachedNameIdDetails
    } yield (nameIdDetails, inReviewMode) match {
      case (Some(details), true) =>
        subscriptionDetailsService
          .cacheNameIdDetails(NameIdOrganisationMatchModel(formData.name, details.id))
          .map { _ =>
            Redirect(DetermineReviewPageController.determineRoute(service))
          }
      case (_, _) =>
        Future.successful(
          Redirect(
            subscriptionFlowManager
              .stepInformation(NameDetailsSubscriptionFlowPage)
              .nextPage
              .url(service)
          )
        )
    }
  }.flatMap(identity)

}
