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
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.SicCodeViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm.sicCodeform
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.sic_code
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SicCodeController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  orgTypeLookup: OrgTypeLookup,
  mcc: MessagesControllerComponents,
  sicCodeView: sic_code,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def populateView(sicCode: Option[String], isInReviewMode: Boolean, service: Service, journey: Journey.Value)(
    implicit
    hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] = {
    lazy val form = sicCode.map(SicCodeViewModel).fold(sicCodeform)(sicCodeform.fill)
    orgTypeLookup.etmpOrgType map { _ =>
      Ok(
        sicCodeView(
          form,
          isInReviewMode,
          requestSessionData.userSelectedOrganisationType,
          service,
          journey,
          requestSessionData.selectedUserLocation
        )
      )
    }
  }

  def createForm(service: Service, journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.cachedSicCode.flatMap(populateView(_, isInReviewMode = false, service, journey))
  }

  def reviewForm(service: Service, journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedSicCode.flatMap(
        sic => populateView(Some(sic), isInReviewMode = true, service, journey)
      )
  }

  def submit(isInReviewMode: Boolean, service: Service, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      sicCodeform.bindFromRequest.fold(
        formWithErrors =>
          // TODO Check if this etmpOrgType call is necessary
          orgTypeLookup.etmpOrgType map { _ =>
            BadRequest(
              sicCodeView(
                formWithErrors,
                isInReviewMode,
                requestSessionData.userSelectedOrganisationType,
                service,
                journey,
                requestSessionData.selectedUserLocation
              )
            )
          },
        formData => submitNewDetails(formData, isInReviewMode, service, journey)
      )
    }

  private def stepInformation()(implicit hc: HeaderCarrier, request: Request[AnyContent]): SubscriptionFlowInfo =
    subscriptionFlowManager.stepInformation(SicCodeSubscriptionFlowPage)

  private def submitNewDetails(
    formData: SicCodeViewModel,
    isInReviewMode: Boolean,
    service: Service,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    subscriptionDetailsHolderService
      .cacheSicCode(formData.sicCode)
      .map(
        _ =>
          if (isInReviewMode)
            Redirect(
              uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController.determineRoute(
                service,
                journey
              )
            )
          else
            Redirect(stepInformation().nextPage.url(service))
      )

}
