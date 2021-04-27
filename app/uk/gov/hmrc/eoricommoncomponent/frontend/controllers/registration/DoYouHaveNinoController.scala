/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.{
  GetNinoController,
  SixLineAddressController
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.haveRowIndividualsNinoForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.match_nino_row_individual

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DoYouHaveNinoController @Inject() (
  authAction: AuthAction,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  matchNinoRowIndividualView: match_nino_row_individual,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayForm(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(Ok(matchNinoRowIndividualView(haveRowIndividualsNinoForm, service, journey)))
    }

  def submit(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        haveRowIndividualsNinoForm.bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(matchNinoRowIndividualView(formWithErrors, service, journey))),
          formData =>
            formData.haveNino match {
              case Some(true) =>
                subscriptionDetailsService
                  .cacheNinoMatch(Some(formData))
                  .map(_ => Redirect(GetNinoController.displayForm(service, journey)))
              case Some(false) =>
                subscriptionDetailsService.updateSubscriptionDetails.map { _ =>
                  noNinoRedirect(service, journey)
                }
              case _ => throw new IllegalArgumentException("Have NINO should be Some(true) or Some(false) but was None")
            }
        )
    }

  private def noNinoRedirect(service: Service, journey: Journey.Value)(implicit request: Request[AnyContent]): Result =
    requestSessionData.userSelectedOrganisationType match {
      case Some(cdsOrgType) =>
        Redirect(SixLineAddressController.showForm(false, cdsOrgType.id, service, journey))
      case _ => throw new IllegalStateException("No userSelectedOrganisationType details in session.")
    }

}
