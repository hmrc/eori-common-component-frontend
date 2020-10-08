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
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.NinoSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.rowIndividualsNinoForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.match_nino_subscription
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HaveNinoSubscriptionController @Inject() (
  authAction: AuthAction,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  matchNinoSubscriptionView: match_nino_subscription,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        populateView(service, journey)
    }

  private def populateView(service: Service, journey: Journey.Value)(implicit
    hc: HeaderCarrier,
    request: Request[AnyContent]
  ) =
    subscriptionDetailsHolderService.cachedNinoMatch.map {
      case Some(formData) =>
        Ok(matchNinoSubscriptionView(rowIndividualsNinoForm.fill(formData), service, journey))

      case _ => Ok(matchNinoSubscriptionView(rowIndividualsNinoForm, service, journey))
    }

  def submit(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        rowIndividualsNinoForm.bindFromRequest.fold(
          formWithErrors => Future.successful(BadRequest(matchNinoSubscriptionView(formWithErrors, service, journey))),
          formData => destinationsByAnswer(formData, service, journey)
        )
    }

  private def destinationsByAnswer(form: NinoMatchModel, service: Service, journey: Journey.Value)(implicit
    hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    form.haveNino match {
      case Some(true) =>
        subscriptionDetailsHolderService
          .cacheCustomsIdAndNinoMatch(Some(Nino(form.nino.getOrElse(noNinoException))), Some(form))
          .map(_ => redirectToNextPage(service))
      case Some(false) =>
        subscriptionDetailsHolderService.cacheCustomsIdAndNinoMatch(None, Some(form)) map (
          _ => redirectToNextPage(service)
        )
      case _ => throw new IllegalStateException("No Data from the form")
    }

  private def redirectToNextPage(service: Service)(implicit request: Request[AnyContent]): Result =
    Redirect(subscriptionFlowManager.stepInformation(NinoSubscriptionFlowPage).nextPage.url(service))

  private lazy val noNinoException = throw new IllegalStateException("User selected 'Yes' for Nino but no Nino found")
}
