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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriNumberViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm.eoriNumberForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhatIsYourEoriController @Inject() (
  authAction: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  mcc: MessagesControllerComponents,
  whatIsYourEoriView: what_is_your_eori,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        subscriptionBusinessService.cachedEoriNumber.map(
          eori => populateView(eori, isInReviewMode = false, service, journey)
        )
    }

  def reviewForm(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        subscriptionBusinessService.getCachedEoriNumber.map(
          eori => populateView(Some(eori), isInReviewMode = true, service, journey)
        )
    }

  def submit(isInReviewMode: Boolean, service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      eoriNumberForm.bindFromRequest.fold(
        formWithErrors =>
          Future.successful(
            BadRequest(
              whatIsYourEoriView(
                formWithErrors,
                isInReviewMode,
                UserLocation.isRow(requestSessionData),
                service,
                journey
              )
            )
          ),
        formData => submitNewDetails(formData, isInReviewMode, service, journey)
      )
    }

  private def populateView(
    eoriNumber: Option[String],
    isInReviewMode: Boolean,
    service: Service,
    journey: Journey.Value
  )(implicit request: Request[AnyContent]) = {
    val form = eoriNumber.map(EoriNumberViewModel).fold(eoriNumberForm)(eoriNumberForm.fill)
    Ok(whatIsYourEoriView(form, isInReviewMode, UserLocation.isRow(requestSessionData), service, journey))
  }

  private def submitNewDetails(
    formData: EoriNumberViewModel,
    isInReviewMode: Boolean,
    service: Service,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]) =
    subscriptionDetailsHolderService.cacheEoriNumber(formData.eoriNumber).map { _ =>
      if (isInReviewMode) Redirect(DetermineReviewPageController.determineRoute(service, journey))
      else Redirect(subscriptionFlowManager.stepInformation(EoriNumberSubscriptionFlowPage).nextPage.url(service))
    }

}
