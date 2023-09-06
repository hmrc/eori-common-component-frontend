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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription

import play.api.data.Form
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ContactAddressSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.ContactAddressForm.contactAddressCreateForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Countries
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.contact_address

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContactAddressController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  subscriptionDetailsService: SubscriptionDetailsService,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  contactAddressView: contact_address
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayPage(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.contactAddress.flatMap {
        populateOkView(_, isInReviewMode = false, service)
      }
    }

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.contactAddress.flatMap {
        populateOkView(_, isInReviewMode = true, service)
      }
    }

  private def populateOkView(contactAddress: Option[ContactAddressModel], isInReviewMode: Boolean, service: Service)(
    implicit request: Request[AnyContent]
  ): Future[Result] = {
    lazy val form = contactAddress.fold(contactAddressCreateForm())(contactAddressCreateForm().fill(_))
    populateCountriesToInclude(service, isInReviewMode, form, Ok)
  }

  private def populateCountriesToInclude(
    service: Service,
    isInReviewMode: Boolean,
    form: Form[ContactAddressModel],
    status: Status
  )(implicit request: Request[AnyContent]) = {
    val (countriesToInclude, countriesInCountryPicker) =
      Countries.getCountryParametersForAllCountries()
    Future.successful(
      status(contactAddressView(form, countriesToInclude, countriesInCountryPicker, isInReviewMode, service))
    )
  }

  def submit(service: Service, isInReviewMode: Boolean): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      contactAddressCreateForm().bindFromRequest()
        .fold(
          formWithErrors => populateCountriesToInclude(service, isInReviewMode, formWithErrors, BadRequest),
          address =>
            subscriptionDetailsService.cacheContactAddressDetails(address).map { _ =>
              if (isInReviewMode)
                Redirect(DetermineReviewPageController.determineRoute(service))
              else
                Redirect(
                  subscriptionFlowManager
                    .stepInformation(ContactAddressSubscriptionFlowPage)
                    .nextPage
                    .url(service)
                )
            }
        )
    }

}
