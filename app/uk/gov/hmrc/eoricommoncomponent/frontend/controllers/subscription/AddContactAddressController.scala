/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.mvc.*
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddContactAddressForm.confirmAddContactAddressYesNoAnswerForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.add_contact_address
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{
  AddContactAddressSubscriptionFlowPage,
  ContactAddressSubscriptionFlowPage
}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.CheckYourDetailsController as CheckYourDetailsRoutes
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService

class AddContactAddressController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  subscriptionDetailsService: SubscriptionDetailsService,
  subscriptionFlowManager: SubscriptionFlowManager,
  addContactAddressView: add_contact_address
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      subscriptionDetailsService.cachedAddressContactDetails().map { addContactAddress =>
        val form = addContactAddress.map(yesNo => confirmAddContactAddressYesNoAnswerForm().fill(yesNo)).getOrElse(
          confirmAddContactAddressYesNoAnswerForm()
        )
        Ok(addContactAddressView(form, addContactAddress, isInReviewMode, service))
      }
    }

  def submit(isInReviewMode: Boolean = false, service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      confirmAddContactAddressYesNoAnswerForm().bindFromRequest().fold(
        formWithErrors =>
          Future(BadRequest(addContactAddressView(formWithErrors, None, isInReviewMode, service))),
        addContactAddress =>
          subscriptionDetailsService.cacheAddContactAddressDetails(addContactAddress).flatMap { _ =>
            if (addContactAddress.isNo) {
              subscriptionDetailsService.clearContactAddress().map { _ =>
                Redirect(CheckYourDetailsRoutes.reviewDetails(service))
              }
            } else {
              if (isInReviewMode) {
                subscriptionDetailsService.cachedContactDetails().flatMap {
                  case Some(_) => Future.successful(Redirect(DetermineReviewPageController.determineRoute(service)))
                  case None =>
                    Future.successful(Redirect(subscriptionFlowManager
                      .stepInformation(ContactAddressSubscriptionFlowPage)
                      .nextPage
                      .url(service)))
                }
              } else {
                Future.successful(Redirect(subscriptionFlowManager
                  .stepInformation(AddContactAddressSubscriptionFlowPage)
                  .nextPage
                  .url(service)))
              }
            }
          }
      )
    }

}
