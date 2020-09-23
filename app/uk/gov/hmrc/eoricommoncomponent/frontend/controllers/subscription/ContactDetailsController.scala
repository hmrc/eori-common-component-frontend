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
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{ContactDetailsSubscriptionFlowPageGetEori, ContactDetailsSubscriptionFlowPageMigrate}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{LoggedInUserWithEnrolments, NA}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{AddressViewModel, ContactDetailsViewModel}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.ContactDetailsForm.contactDetailsCreateForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Countries
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.RegistrationDetailsCreator
import uk.gov.hmrc.eoricommoncomponent.frontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.contact_details
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ContactDetailsController @Inject() (
  authAction: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  cdsFrontendDataCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionDetailsService: SubscriptionDetailsService,
  countries: Countries,
  orgTypeLookup: OrgTypeLookup,
  registrationDetailsService: RegistrationDetailsService,
  mcc: MessagesControllerComponents,
  contactDetailsView: contact_details,
  regDetailsCreator: RegistrationDetailsCreator
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      journey match {
        case Journey.Subscribe =>
          val f = for {
            orgType <- orgTypeLookup.etmpOrgType

            cachedCustomsId <- orgType match {
              case Some(NA) => subscriptionDetailsService.cachedCustomsId
              case _        => Future.successful(None)
            }

            cachedNameIdDetails <- orgType match {
              case Some(NA) => Future.successful(None)
              case _        => subscriptionDetailsService.cachedNameIdDetails
            }
          } yield (cachedCustomsId, cachedNameIdDetails) match {
            case (None, None) => populateFormGYE(service, journey)(false)
            case _ =>
              Future.successful(
                Redirect(
                  subscriptionFlowManager
                    .stepInformation(ContactDetailsSubscriptionFlowPageMigrate)
                    .nextPage
                    .url(service)
                )
              )
          }
          f.flatMap(identity)
        case Journey.Register => populateFormGYE(service, journey)(false)
      }
    }

  def reviewForm(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      journey match {
        case Journey.Subscribe => populateFormGYE(service, journey)(true)
        case _                 => populateFormGYE(service, journey)(true)
      }
    }

  private def populateFormGYE(service: Service, journey: Journey.Value)(
    isInReviewMode: Boolean
  )(implicit request: Request[AnyContent]) = {
    for {
      email          <- cdsFrontendDataCache.email
      contactDetails <- subscriptionBusinessService.cachedContactDetailsModel
    } yield populateOkView(
      contactDetails.map(_.toContactDetailsViewModel),
      Some(email),
      isInReviewMode = isInReviewMode,
      service,
      journey
    )
  }.flatMap(identity)

  def submit(isInReviewMode: Boolean, service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      cdsFrontendDataCache.email flatMap { email =>
        contactDetailsCreateForm().bindFromRequest.fold(
          formWithErrors =>
            createContactDetails(journey).map { contactDetails =>
              BadRequest(
                contactDetailsView(
                  formWithErrors,
                  countries.all,
                  contactDetails,
                  Some(email),
                  isInReviewMode,
                  service,
                  journey
                )
              )
            },
          formData =>
            journey match {
              case Journey.Subscribe =>
                storeContactDetailsMigrate(formData, email, isInReviewMode, service, journey)
              case _ =>
                storeContactDetails(formData, email, isInReviewMode, service, journey)
            }
        )
      }
    }

  private def createContactDetails(
    journey: Journey.Value
  )(implicit request: Request[AnyContent]): Future[AddressViewModel] =
    cdsFrontendDataCache.subscriptionDetails flatMap { sd =>
      sd.contactDetails match {
        case Some(contactDetails) =>
          Future.successful(
            AddressViewModel(
              contactDetails.street.getOrElse(""),
              contactDetails.city.getOrElse(""),
              contactDetails.postcode,
              contactDetails.countryCode.getOrElse("")
            )
          )
        case _ =>
          journey match {
            case Journey.Register =>
              cdsFrontendDataCache.registrationDetails.map(rd => AddressViewModel(rd.address))
            case _ =>
              subscriptionDetailsService.cachedAddressDetails.map {
                case Some(addressViewModel) => addressViewModel
                case _ =>
                  throw new IllegalStateException("No addressViewModel details found in cache")
              }
          }
      }
    }

  private def clearFieldsIfNecessary(cdm: ContactDetailsViewModel, isInReviewMode: Boolean): ContactDetailsViewModel =
    if (!isInReviewMode && cdm.useAddressFromRegistrationDetails)
      cdm.copy(postcode = None, city = None, countryCode = None, street = None)
    else cdm

  private def populateOkView(
    contactDetailsModel: Option[ContactDetailsViewModel],
    email: Option[String],
    isInReviewMode: Boolean,
    service: Service,
    journey: Journey.Value
  )(implicit request: Request[AnyContent]): Future[Result] = {
    val form = contactDetailsModel
      .map(clearFieldsIfNecessary(_, isInReviewMode))
      .fold(contactDetailsCreateForm())(f => contactDetailsCreateForm().fill(f))

    createContactDetails(journey) map (
      contactDetails =>
        Ok(contactDetailsView(form, countries.all, contactDetails, email, isInReviewMode, service, journey))
    )
  }

  private def storeContactDetailsMigrate(
    formData: ContactDetailsViewModel,
    email: String,
    isInReviewMode: Boolean,
    service: Service,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {
    for {
      cachedAddressDetails <- subscriptionDetailsService.cachedAddressDetails
      _ <- registrationDetailsService.cacheAddress(
        regDetailsCreator
          .registrationAddressFromAddressViewModel(cachedAddressDetails.get)
      )
    } yield storeContactDetails(formData, email, isInReviewMode, service, journey)
  }.flatMap(identity)

  private def storeContactDetails(
    formData: ContactDetailsViewModel,
    email: String,
    inReviewMode: Boolean,
    service: Service,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    subscriptionDetailsService
      .cacheContactDetails(
        formData.copy(emailAddress = Some(email)).toContactDetailsModel,
        isInReviewMode = inReviewMode
      )
      .map(
        _ =>
          (inReviewMode, journey) match {
            case (true, _) =>
              Redirect(DetermineReviewPageController.determineRoute(service, journey))
            case (_, Journey.Register) =>
              Redirect(
                subscriptionFlowManager
                  .stepInformation(ContactDetailsSubscriptionFlowPageGetEori)
                  .nextPage
                  .url(service)
              )
            case (_, _) =>
              Redirect(
                subscriptionFlowManager
                  .stepInformation(ContactDetailsSubscriptionFlowPageMigrate)
                  .nextPage
                  .url(service)
              )
          }
      )
}
