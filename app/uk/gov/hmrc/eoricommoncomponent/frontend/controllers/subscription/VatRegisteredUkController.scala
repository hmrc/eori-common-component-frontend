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
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.VatDetailsController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{
  VatDetailsSubscriptionFlowPage,
  VatRegisteredUkSubscriptionFlowPage
}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{LoggedInUserWithEnrolments, YesNo}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.vat_registered_uk

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatRegisteredUkController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionDetailsService: SubscriptionDetailsService,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  vatRegisteredUkView: vat_registered_uk
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(
        Ok(
          vatRegisteredUkView(
            isInReviewMode = false,
            vatRegisteredUkYesNoAnswerForm(requestSessionData.isPartnership),
            isIndividualFlow,
            requestSessionData.isPartnership,
            journey
          )
        )
      )
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] = ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        isVatRegisteredUk <- subscriptionBusinessService.getCachedVatRegisteredUk
        yesNo: YesNo = YesNo(isVatRegisteredUk)
      } yield Ok(
        vatRegisteredUkView(
          isInReviewMode = true,
          vatRegisteredUkYesNoAnswerForm(requestSessionData.isPartnership).fill(yesNo),
          isIndividualFlow,
          requestSessionData.isPartnership,
          journey
        )
      )
  }

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      vatRegisteredUkYesNoAnswerForm(requestSessionData.isPartnership)
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Future.successful(
              BadRequest(
                vatRegisteredUkView(
                  isInReviewMode,
                  formWithErrors,
                  isIndividualFlow,
                  requestSessionData.isPartnership,
                  journey
                )
              )
            ),
          yesNoAnswer =>
            subscriptionDetailsService.cacheVatRegisteredUk(yesNoAnswer).flatMap {
              _ =>
                if (isInReviewMode)
                  if (yesNoAnswer.isYes)
                    Future.successful(Redirect(VatDetailsController.reviewForm(journey = Journey.Register).url))
                  else {
                    subscriptionDetailsService.clearCachedUkVatDetails
                    Future.successful(Redirect(DetermineReviewPageController.determineRoute(journey).url))
                  }
                else if (yesNoAnswer.isYes)
                  Future.successful(
                    Redirect(subscriptionFlowManager.stepInformation(VatRegisteredUkSubscriptionFlowPage).nextPage.url)
                  )
                else
                  Future.successful(
                    Redirect(subscriptionFlowManager.stepInformation(VatDetailsSubscriptionFlowPage).nextPage.url)
                  )
            }
        )
    }

  private def isIndividualFlow(implicit rq: Request[AnyContent]) =
    subscriptionFlowManager.currentSubscriptionFlow.isIndividualFlow

}
