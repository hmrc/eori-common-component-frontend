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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.UtrSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.utrForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.match_utr_subscription
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HaveUtrSubscriptionController @Inject()(
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  requestSessionData: RequestSessionData,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  matchUtrSubscriptionView: match_utr_subscription,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      requestSessionData.userSelectedOrganisationType match {
        case Some(orgType) => Future.successful(Ok(matchUtrSubscriptionView(utrForm, orgType.id, journey)))
        case None          => noOrgTypeSelected
      }
  }

  def submit(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      requestSessionData.userSelectedOrganisationType match {
        case Some(orgType) =>
          utrForm.bindFromRequest.fold(
            formWithErrors =>
              Future.successful(BadRequest(matchUtrSubscriptionView(formWithErrors, orgType.id, journey))),
            formData => destinationsByAnswer(formData, journey, orgType)
          )
        case None => noOrgTypeSelected
      }
  }

  private def destinationsByAnswer(form: UtrMatchModel, journey: Journey.Value, orgType: CdsOrganisationType)(
    implicit hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    form.haveUtr match {
      case Some(true) if orgType == CdsOrganisationType.Company => cacheNameIdDetails(form, journey)
      case Some(true) =>
        subscriptionDetailsService.cacheCustomsId(Utr(form.id.getOrElse(noUtrException))).map { _ =>
          Redirect(AddressController.createForm(journey))
        }
      case Some(false) =>
        Future.successful(Redirect(subscriptionFlowManager.stepInformation(UtrSubscriptionFlowPage).nextPage.url))
      case _ => throw new IllegalStateException("No Data from the form")
    }

  // TODO Proposal for the method below. Right now we're redirecting to the new page without waiting for the cacheName method to be done
  // This change break the tests, so need to be investigated what is happening there
  /*
    subscriptionDetailsService.cachedNameDetails.flatMap { optionalName =>
      ((optionalName, form.id) match {
        case (Some(name), Some(id)) => subscriptionDetailsService.cacheNameIdAndCustomsId(name.name, id)
        case _                      => noBusinessNameOrId
      }).map( _ => Redirect(AddressController.createForm(journey)))
    }
   */
  private def cacheNameIdDetails(form: UtrMatchModel, journey: Journey.Value)(
    implicit hc: HeaderCarrier
  ): Future[Result] =
    for {
      optionalName <- subscriptionDetailsService.cachedNameDetails
    } yield {
      (optionalName, form.id) match {
        case (Some(name), Some(id)) => subscriptionDetailsService.cacheNameIdAndCustomsId(name.name, id)
        case _                      => noBusinessNameOrId
      }
      Redirect(AddressController.createForm(journey))
    }

  private lazy val noUtrException = throw new IllegalStateException("User selected 'Yes' for Utr but no Utr found")
  private lazy val noOrgTypeSelected = throw new IllegalStateException("No organisation type selected by user")
  private lazy val noBusinessNameOrId = throw new IllegalStateException("No business name or CustomsId cached")

}
