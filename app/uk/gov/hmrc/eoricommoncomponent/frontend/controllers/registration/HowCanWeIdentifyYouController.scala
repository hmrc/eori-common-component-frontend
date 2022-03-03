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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration

import javax.inject.{Inject, Singleton}
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.ninoOrUtrChoiceForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.how_can_we_identify_you
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HowCanWeIdentifyYouController @Inject() (
  authAction: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  mcc: MessagesControllerComponents,
  howCanWeIdentifyYouView: how_can_we_identify_you,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      populateView(service, isInReviewMode = false)
    }

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      populateView(service, isInReviewMode = true)
    }

  private def populateView(service: Service, isInReviewMode: Boolean)(implicit hc: HeaderCarrier, request: Request[_]) =
    subscriptionBusinessService.getCachedNinoOrUtrChoice.map { choice =>
      Ok(howCanWeIdentifyYouView(ninoOrUtrChoiceForm.fill(NinoOrUtrChoice(choice)), isInReviewMode, service))
    }

  def submit(service: Service, isInReviewMode: Boolean): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      ninoOrUtrChoiceForm
        .bindFromRequest()
        .fold(
          invalidForm => Future.successful(BadRequest(howCanWeIdentifyYouView(invalidForm, isInReviewMode, service))),
          form => storeChoice(form, isInReviewMode, service)
        )
    }

  private def storeChoice(formData: NinoOrUtrChoice, inReviewMode: Boolean, service: Service)(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    subscriptionDetailsHolderService
      .cacheNinoOrUtrChoice(formData)
      .map { _ =>
        formData.ninoOrUtrRadio match {
          case Some("nino") =>
            Redirect(continueNino(inReviewMode, service))
          case _ =>
            Redirect(continueUtr(inReviewMode, service))
        }
      }

  private def continueNino(inReviewMode: Boolean, service: Service) =
    if (inReviewMode) HowCanWeIdentifyYouNinoController.reviewForm(service)
    else HowCanWeIdentifyYouNinoController.createForm(service)

  private def continueUtr(inReviewMode: Boolean, service: Service) =
    if (inReviewMode) HowCanWeIdentifyYouUtrController.reviewForm(service)
    else HowCanWeIdentifyYouUtrController.createForm(service)

}
