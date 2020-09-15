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

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.data.Form
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.AddressDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.YesNoWrongAddress
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.AddressDetailsForm.addressDetailsCreateForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.confirm_contact_details
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddressController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  cdsFrontendDataCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  requestSessionData: RequestSessionData,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  countries: Countries,
  mcc: MessagesControllerComponents,
  subscriptionDetailsService: SubscriptionDetailsService,
  confirmContactDetails: confirm_contact_details,
  addressView: address
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.address.flatMap {
        populateOkView(_, isInReviewMode = false, journey)
      }
    }

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.addressOrException flatMap { cdm =>
        populateOkView(Some(cdm), isInReviewMode = true, journey)
      }
    }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      addressDetailsCreateForm().bindFromRequest
        .fold(
          formWithErrors => populateCountriesToInclude(isInReviewMode, journey, formWithErrors, BadRequest),
          address => {
            subscriptionDetailsHolderService.cacheAddressDetails(address)
            journey match {
              case Journey.Subscribe =>
                subscriptionDetailsHolderService
                  .cacheAddressDetails(address)
                  .map(
                    _ =>
                      if (isInReviewMode)
                        Redirect(DetermineReviewPageController.determineRoute(journey))
                      else
                        Redirect(
                          subscriptionFlowManager
                            .stepInformation(AddressDetailsSubscriptionFlowPage)
                            .nextPage
                            .url
                        )
                  )
              case _ =>
                showReviewPage(address, isInReviewMode, journey)
            }
          }
        )
    }

  private def populateCountriesToInclude(
    isInReviewMode: Boolean,
    journey: Journey.Value,
    form: Form[AddressViewModel],
    status: Status
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]) =
    cdsFrontendDataCache.registrationDetails flatMap { rd =>
      subscriptionDetailsService.cachedCustomsId flatMap { cid =>
        val (countriesToInclude, countriesInCountryPicker) =
          (rd.customsId, cid, journey) match {
            case (_, _, Journey.Subscribe) =>
              countries.getCountryParametersForAllCountries()
            case (Some(_: Utr | _: Nino), _, _) | (_, Some(_: Utr | _: Nino), _) =>
              countries.getCountryParameters(None)
            case _ =>
              countries.getCountryParameters(requestSessionData.selectedUserLocationWithIslands)
          }
        val isRow = requestSessionData.selectedUserLocationWithIslands == Some("third-country")
        Future.successful(
          status(
            addressView(
              form,
              countriesToInclude,
              countriesInCountryPicker,
              isInReviewMode,
              journey,
              isIndividualOrSoleTrader,
              requestSessionData.isPartnership,
              requestSessionData.isCompany,
              isRow
            )
          )
        )
      }
    }

  private def populateOkView(
    address: Option[AddressViewModel],
    isInReviewMode: Boolean,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    lazy val form = address.fold(addressDetailsCreateForm())(addressDetailsCreateForm().fill(_))
    populateCountriesToInclude(isInReviewMode, journey, form, Ok)
  }

  private def showReviewPage(address: AddressViewModel, inReviewMode: Boolean, journey: Journey.Value)(implicit
    hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] = {
    val etmpOrgType = requestSessionData.userSelectedOrganisationType
      .map(EtmpOrganisationType(_))
      .getOrElse(throw new IllegalStateException("No Etmp org type"))

    subscriptionDetailsHolderService.cacheAddressDetails(address).flatMap { _ =>
      if (inReviewMode)
        Future.successful(Redirect(DetermineReviewPageController.determineRoute(journey)))
      else
        cdsFrontendDataCache.registrationDetails map {
          case RegistrationDetailsIndividual(customsId, _, _, name, _, _) =>
            Ok(confirmContactDetails(name, address, customsId, None, YesNoWrongAddress.createForm(), journey))
          case RegistrationDetailsOrganisation(customsId, _, _, name, _, _, _) =>
            Ok(
              confirmContactDetails(
                name,
                address,
                customsId,
                Some(etmpOrgType),
                YesNoWrongAddress.createForm(),
                journey
              )
            )
          case _ =>
            throw new IllegalStateException("No details stored in cache for this session")
        }
    }
  }

  private def isIndividualOrSoleTrader(implicit request: Request[AnyContent]) =
    requestSessionData.userSelectedOrganisationType.fold(false) { oType =>
      oType == CdsOrganisationType.Individual ||
      oType == CdsOrganisationType.SoleTrader ||
      oType == CdsOrganisationType.ThirdCountryIndividual ||
      oType == CdsOrganisationType.ThirdCountrySoleTrader
    }

}
