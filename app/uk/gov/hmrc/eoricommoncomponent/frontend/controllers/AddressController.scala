/*
 * Copyright 2022 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.AddressDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.AddressDetailsForm.addressDetailsCreateForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddressController @Inject() (
  authorise: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  sessionCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  requestSessionData: RequestSessionData,
  subscriptionDetailsService: SubscriptionDetailsService,
  mcc: MessagesControllerComponents,
  addressView: address
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service): Action[AnyContent] =
    authorise.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.address.flatMap {
        populateOkView(_, isInReviewMode = false, service)
      }
    }

  def reviewForm(service: Service): Action[AnyContent] =
    authorise.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.addressOrException flatMap { cdm =>
        populateOkView(Some(cdm), isInReviewMode = true, service)
      }
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authorise.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      addressDetailsCreateForm().bindFromRequest
        .fold(
          formWithErrors => populateCountriesToInclude(isInReviewMode, service, formWithErrors, BadRequest),
          address =>
            subscriptionDetailsService.cacheAddressDetails(address).flatMap { _ =>
              subscriptionDetailsService
                .cacheAddressDetails(address)
                .flatMap { _ =>
                  sessionCache.clearAddressLookupParams.map { _ =>
                    if (isInReviewMode)
                      Redirect(DetermineReviewPageController.determineRoute(service))
                    else
                      Redirect(
                        subscriptionFlowManager
                          .stepInformation(AddressDetailsSubscriptionFlowPage)
                          .nextPage
                          .url(service)
                      )
                  }
                }
            }
        )
    }

  private def populateCountriesToInclude(
    isInReviewMode: Boolean,
    service: Service,
    form: Form[AddressViewModel],
    status: Status
  )(implicit request: Request[AnyContent]) = {
    val (countriesToInclude, countriesInCountryPicker) = Countries.getCountryParametersForAllCountries()
    val isRow                                          = UserLocation.isRow(requestSessionData)
    Future.successful(
      status(
        addressView(
          form,
          countriesToInclude,
          countriesInCountryPicker,
          isInReviewMode,
          service,
          requestSessionData.isIndividualOrSoleTrader,
          requestSessionData.isPartnership,
          requestSessionData.isCompany,
          isRow
        )
      )
    )
  }

  private def populateOkView(address: Option[AddressViewModel], isInReviewMode: Boolean, service: Service)(implicit
    request: Request[AnyContent]
  ): Future[Result] = {
    lazy val form = address.fold(addressDetailsCreateForm())(addressDetailsCreateForm().fill(_))
    populateCountriesToInclude(isInReviewMode, service, form, Ok)
  }

}
