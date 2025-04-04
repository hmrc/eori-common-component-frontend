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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.NameDobDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.{enterNameDobForm, enterNameDobFormRow}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameDobSoleTraderController @Inject() (
  authAction: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  requestSessionData: RequestSessionData,
  cdsFrontendDataCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  enterYourDetails: enter_your_details,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) {
      implicit request => _: LoggedInUserWithEnrolments =>
        subscriptionBusinessService.cachedSubscriptionNameDobViewModel flatMap { maybeCachedNameDobViewModel =>
          populateOkView(maybeCachedNameDobViewModel, isInReviewMode = false, service)
        }
    }

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) {
      implicit request => _: LoggedInUserWithEnrolments =>
        subscriptionBusinessService.getCachedSubscriptionNameDobViewModel flatMap { cdm =>
          populateOkView(Some(cdm), isInReviewMode = true, service)
        }
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      val form = if (UserLocation.isRow(requestSessionData)) enterNameDobFormRow else enterNameDobForm
      form.bindFromRequest().fold(
        formWithErrors =>
          cdsFrontendDataCache.registrationDetails map { _ =>
            BadRequest(
              enterYourDetails(
                formWithErrors,
                isInReviewMode,
                service,
                requestSessionData.selectedUserLocationWithIslands
              )
            )
          },
        formData => storeNameDobDetails(formData, isInReviewMode, service)
      )
    }

  private def populateOkView(nameDobViewModel: Option[NameDobMatchModel], isInReviewMode: Boolean, service: Service)(
    implicit request: Request[AnyContent]
  ): Future[Result] = {
    lazy val form = nameDobViewModel.fold(enterNameDobForm) {
      enterNameDobForm.fill
    }

    cdsFrontendDataCache.registrationDetails map { _ =>
      Ok(enterYourDetails(form, isInReviewMode, service, requestSessionData.selectedUserLocationWithIslands))
    }
  }

  private def storeNameDobDetails(formData: NameDobMatchModel, inReviewMode: Boolean, service: Service)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    subscriptionDetailsHolderService.cacheNameDobDetails(formData).map { _ =>
      if (inReviewMode)
        Redirect(
          uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController.determineRoute(
            service
          )
        )
      else
        Redirect(subscriptionFlowManager.stepInformation(NameDobDetailsSubscriptionFlowPage).nextPage.url(service))
    }

}
