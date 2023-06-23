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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription

import play.api.Logging
import play.api.data.Form
import play.api.mvc.{AnyContent, MessagesControllerComponents, Request, Result}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.AddressDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.AddressDetailsForm.addressDetailsCreateForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Countries
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.address

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AddressService @Inject() (
  subscriptionDetailsService: SubscriptionDetailsService,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  addressView: address,
  mcc: MessagesControllerComponents,
  requestSessionData: RequestSessionData,
  sessionCache: SessionCache
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with Logging {

  private def populateCountriesToInclude(
    isInReviewMode: Boolean,
    service: Service,
    form: Form[AddressViewModel],
    status: Status
  )(implicit request: Request[AnyContent]) = {
    val (countriesToInclude, countriesInCountryPicker) = Countries.getCountryParametersForAllCountries()
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
          UserLocation.isRow(requestSessionData)
        )
      )
    )
  }

  def handleFormDataAndRedirect(form: Form[AddressViewModel], isInReviewMode: Boolean, service: Service)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    form.bindFromRequest()
      .fold(
        formWithErrors => populateCountriesToInclude(isInReviewMode, service, formWithErrors, BadRequest),
        address =>
          for {
            _ <- subscriptionDetailsService.cacheAddressDetails(address)
            _ <- sessionCache.clearAddressLookupParams
          } yield
            if (isInReviewMode)
              Redirect(DetermineReviewPageController.determineRoute(service))
            else
              Redirect(
                subscriptionFlowManager
                  .stepInformation(AddressDetailsSubscriptionFlowPage)
                  .nextPage
                  .url(service)
              )
      )

  private def populateOkView(address: Option[AddressViewModel], isInReviewMode: Boolean, service: Service)(implicit
    request: Request[AnyContent]
  ): Future[Result] = {
    lazy val form = address.fold(addressDetailsCreateForm())(addressDetailsCreateForm().fill(_))
    populateCountriesToInclude(isInReviewMode, service, form, Ok)
  }

  def populateFormViewCached(isInReviewMode: Boolean, service: Service)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    for {
      address <- subscriptionBusinessService.address
      viewRes <- populateOkView(address, isInReviewMode, service)
    } yield viewRes

  def populateReviewViewCached(isInReviewMode: Boolean, service: Service)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    for {
      address <- subscriptionBusinessService.addressOrException
      viewRes <- populateOkView(Some(address), isInReviewMode, service)
    } yield viewRes

}
