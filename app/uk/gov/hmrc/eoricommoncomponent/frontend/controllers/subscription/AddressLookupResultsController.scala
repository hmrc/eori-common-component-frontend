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
import play.api.mvc._
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.AddressLookupConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.AddressLookupErrorController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.AddressDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{AddressLookupParams, AddressResultsForm}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.models.address.{
  AddressLookup,
  AddressLookupFailure,
  AddressLookupSuccess
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.address_lookup_results
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddressLookupResultsController @Inject() (
  authAction: AuthAction,
  sessionCache: SessionCache,
  subscriptionDetailsService: SubscriptionDetailsService,
  subscriptionFlowManager: SubscriptionFlowManager,
  addressLookupConnector: AddressLookupConnector,
  mcc: MessagesControllerComponents,
  addressLookupResultsPage: address_lookup_results
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayPage(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      displayPage(service, isInReviewMode = false)
    }

  def reviewPage(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      displayPage(service, isInReviewMode = true)
    }

  private def displayPage(service: Service, isInReviewMode: Boolean)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    sessionCache.addressLookupParams.flatMap {
      case Some(addressLookupParams) =>
        addressLookupConnector.lookup(
          addressLookupParams.postcode.replaceAll(" ", ""),
          addressLookupParams.line1
        ).flatMap {
          case AddressLookupSuccess(addresses) if addresses.nonEmpty && addresses.forall(_.nonEmpty) =>
            Future.successful(
              Ok(
                prepareView(AddressResultsForm.form(addresses.map(_.dropDownView)), addresses, isInReviewMode, service)
              )
            )
          case AddressLookupSuccess(_) if addressLookupParams.line1.exists(_.nonEmpty) =>
            repeatQueryWithoutLine1(addressLookupParams, service, isInReviewMode)
          case AddressLookupSuccess(_) =>
            Future.successful(redirectToNoResultsPage(service, isInReviewMode))
          case AddressLookupFailure => throw AddressLookupException
        }.recoverWith {
          case _: AddressLookupException.type => Future.successful(redirectToErrorPage(service, isInReviewMode))
        }
      case _ => Future.successful(redirectToPostcodePage(service, isInReviewMode))
    }

  private def prepareView(
    form: Form[AddressResultsForm],
    addresses: Seq[AddressLookup],
    isInReviewMode: Boolean,
    service: Service
  )(implicit request: Request[AnyContent]): HtmlFormat.Appendable =
    addressLookupResultsPage(form, addresses, isInReviewMode, service)

  private def repeatQueryWithoutLine1(
    addressLookupParams: AddressLookupParams,
    service: Service,
    isInReviewMode: Boolean
  )(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] = {
    val addressLookupParamsWithoutLine1 = AddressLookupParams(addressLookupParams.postcode, None, skippedLine1 = true)

    addressLookupConnector.lookup(addressLookupParamsWithoutLine1.postcode.replaceAll(" ", ""), None).flatMap {
      case AddressLookupSuccess(addresses) if addresses.nonEmpty && addresses.forall(_.nonEmpty) =>
        sessionCache.saveAddressLookupParams(addressLookupParamsWithoutLine1).map { _ =>
          Ok(prepareView(AddressResultsForm.form(addresses.map(_.dropDownView)), addresses, isInReviewMode, service))
        }
      case AddressLookupSuccess(_) => Future.successful(redirectToNoResultsPage(service, isInReviewMode))
      case AddressLookupFailure    => throw AddressLookupException
    }
  }

  def submit(service: Service, isInReviewMode: Boolean): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      sessionCache.addressLookupParams.flatMap {
        case Some(addressLookupParams) =>
          addressLookupConnector.lookup(
            addressLookupParams.postcode.replaceAll(" ", ""),
            addressLookupParams.line1
          ).flatMap {
            case AddressLookupSuccess(addresses) if addresses.nonEmpty && addresses.forall(_.nonEmpty) =>
              val addressesMap  = addresses.map(address => address.dropDownView -> address).toMap
              val addressesList = addressesMap.keys.toSeq
              AddressResultsForm.form(addressesList).bindFromRequest().fold(
                formWithErrors =>
                  Future.successful(BadRequest(prepareView(formWithErrors, addresses, isInReviewMode, service))),
                validAnswer => {
                  val address = addressesMap(validAnswer.address).toAddressViewModel

                  subscriptionDetailsService.cacheAddressDetails(address).map { _ =>
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
              )
            case AddressLookupSuccess(_) => Future.successful(redirectToNoResultsPage(service, isInReviewMode))
            case AddressLookupFailure    => throw AddressLookupException
          }.recoverWith {
            case _: AddressLookupException.type => Future.successful(redirectToErrorPage(service, isInReviewMode))
          }
        case _ => Future.successful(redirectToPostcodePage(service, isInReviewMode))

      }
    }

  private def redirectToNoResultsPage(service: Service, isInReviewMode: Boolean): Result =
    if (isInReviewMode) Redirect(AddressLookupErrorController.reviewNoResultsPage(service))
    else Redirect(AddressLookupErrorController.displayNoResultsPage(service))

  private def redirectToPostcodePage(service: Service, isInReviewMode: Boolean): Result =
    if (isInReviewMode) Redirect(routes.AddressLookupPostcodeController.reviewPage(service))
    else Redirect(routes.AddressLookupPostcodeController.displayPage(service))

  private def redirectToErrorPage(service: Service, isInReviewMode: Boolean): Result =
    if (isInReviewMode) Redirect(AddressLookupErrorController.reviewErrorPage(service))
    else Redirect(AddressLookupErrorController.displayErrorPage(service))

  case object AddressLookupException extends Exception("Address Lookup service is not available")
}
