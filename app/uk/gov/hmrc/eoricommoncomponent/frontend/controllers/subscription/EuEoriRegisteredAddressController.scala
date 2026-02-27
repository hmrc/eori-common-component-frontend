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

import play.api.data.Form
import play.api.mvc.*
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EuEoriRegisteredAddressModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.EuEoriRegisteredAddressForm.euEoriRegisteredAddressCreateForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Countries
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.eu_eori_registered_address

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EuEoriRegisteredAddressController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  subscriptionDetailsService: SubscriptionDetailsService,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  sessionCache: SessionCache,
  view: eu_eori_registered_address
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayPage(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      subscriptionBusinessService.euEoriRegisteredAddress.flatMap {
        populateOkView(_, isInReviewMode = false, service)
      }
    }

  def reviewPage(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      subscriptionBusinessService.euEoriRegisteredAddress.flatMap {
        populateOkView(_, isInReviewMode = true, service)
      }
    }

  private def populateOkView(
    euEoriRegisteredAddressModel: Option[EuEoriRegisteredAddressModel],
    isInReviewMode: Boolean,
    service: Service
  )(
    implicit request: Request[AnyContent]
  ): Future[Result] = {
    lazy val form = euEoriRegisteredAddressModel.fold(euEoriRegisteredAddressCreateForm())(
      euEoriRegisteredAddressCreateForm().fill(_)
    )
    populateCountriesToInclude(service, isInReviewMode, form, Ok)
  }

  private def populateCountriesToInclude(
    service: Service,
    isInReviewMode: Boolean,
    form: Form[EuEoriRegisteredAddressModel],
    status: Status
  )(implicit request: Request[AnyContent]): Future[Result] =
    sessionCache.userLocation.map { userLocation =>
      val (countriesToInclude, countriesInCountryPicker) = Countries.getCountryParameters(userLocation.location)
      status(view(form, countriesToInclude, countriesInCountryPicker, isInReviewMode, service))
    }

  def submit(service: Service, isInReviewMode: Boolean): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      euEoriRegisteredAddressCreateForm().bindFromRequest()
        .fold(
          formWithErrors => populateCountriesToInclude(service, isInReviewMode, formWithErrors, BadRequest),
          address =>
            subscriptionDetailsService.cacheEuEoriRegisteredAddressDetails(address).map { _ =>
              if (isInReviewMode)
//                TODO: Redirect(DetermineReviewPageController.determineRoute(service))
                Redirect("https://www.gov.uk/eori")
              else
//                TODO: Redirect(
//                  subscriptionFlowManager
//                    .stepInformation(EuEoriRegisteredAddressSubscriptionFlowPage)
//                    .nextPage
//                    .url(service))
                Redirect("https://www.gov.uk/check-eori-number")
            }
        )
    }

}
