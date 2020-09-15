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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.HowCanWeIdentifyYouSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.ninoOrUtrForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.how_can_we_identify_you
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HowCanWeIdentifyYouController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  howCanWeIdentifyYouView: how_can_we_identify_you,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(Ok(howCanWeIdentifyYouView(ninoOrUtrForm, isInReviewMode = false, journey)))
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedCustomsId.map { customsId =>
        val ninoOrUtr = customsId match {
          case Nino(id) => NinoOrUtr(Some(id), None, Some("nino"))
          case Utr(id)  => NinoOrUtr(None, Some(id), Some("utr"))
          case unexpected =>
            throw new IllegalStateException("Expected a Nino or UTR from the cached customs Id but got: " + unexpected)
        }
        Ok(howCanWeIdentifyYouView(ninoOrUtrForm.fill(ninoOrUtr), isInReviewMode = true, journey))
      }
  }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      ninoOrUtrForm
        .bindFromRequest()
        .fold(
          invalidForm => Future.successful(BadRequest(howCanWeIdentifyYouView(invalidForm, isInReviewMode, journey))),
          form => storeId(form, isInReviewMode, journey)
        )
    }

  private def storeId(formData: NinoOrUtr, inReviewMode: Boolean, journey: Journey.Value)(implicit
    hc: HeaderCarrier,
    request: Request[AnyContent]
  ): Future[Result] =
    subscriptionDetailsHolderService
      .cacheCustomsId(customsId(formData))
      .map(
        _ =>
          if (inReviewMode)
            Redirect(DetermineReviewPageController.determineRoute(journey))
          else
            Redirect(subscriptionFlowManager.stepInformation(HowCanWeIdentifyYouSubscriptionFlowPage).nextPage.url)
      )

  private def customsId(ninoOrUtr: NinoOrUtr): CustomsId = ninoOrUtr match {
    case NinoOrUtr(Some(nino), _, ninoOrUtrRadio) if ninoOrUtrRadio.contains("nino") => Nino(nino)
    case NinoOrUtr(_, Some(utr), ninoOrUtrRadio) if ninoOrUtrRadio.contains("utr")   => Utr(utr)
    case unexpected =>
      throw new IllegalArgumentException("Expected only nino or utr to be populated but got: " + unexpected)
  }

}
