/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.UtrSubscriptionMessages.{
  defaultHeadingMessage,
  defaultHintMessage
}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.AddressLookupPostcodeController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.HowCanWeIdentifyYouSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.subscriptionUtrForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.how_can_we_identify_you_utr

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HowCanWeIdentifyYouUtrController @Inject() (
  authAction: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  howCanWeIdentifyYouView: how_can_we_identify_you_utr,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) {
      implicit request => _: LoggedInUserWithEnrolments =>
        populateView(service, isInReviewMode = false)
    }

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) {
      implicit request => _: LoggedInUserWithEnrolments =>
        populateView(service, isInReviewMode = true)
    }

  private def populateView(service: Service, isInReviewMode: Boolean)(implicit request: Request[AnyContent]) =
    subscriptionBusinessService.getCachedCustomsId.map {
      case Some(Utr(id)) =>
        Ok(
          howCanWeIdentifyYouView(
            subscriptionUtrForm.fill(IdMatchModel(id)),
            getPageContent(),
            isInReviewMode,
            routes.HowCanWeIdentifyYouUtrController.submit(isInReviewMode, service),
            service
          )
        )
      case _ =>
        Ok(
          howCanWeIdentifyYouView(
            subscriptionUtrForm,
            getPageContent(),
            isInReviewMode,
            routes.HowCanWeIdentifyYouUtrController.submit(isInReviewMode, service),
            service
          )
        )
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionUtrForm
        .bindFromRequest()
        .fold(
          invalidForm =>
            Future.successful(
              BadRequest(
                howCanWeIdentifyYouView(
                  invalidForm,
                  getPageContent(),
                  isInReviewMode,
                  routes.HowCanWeIdentifyYouUtrController.submit(isInReviewMode, service),
                  service
                )
              )
            ),
          form => storeId(form, isInReviewMode, service)
        )
    }

  private def storeId(formData: IdMatchModel, inReviewMode: Boolean, service: Service)(implicit
    request: Request[AnyContent]
  ): Future[Result] =
    subscriptionDetailsHolderService
      .cacheCustomsId(Utr(formData.id))
      .map(
        _ =>
          if (inReviewMode)
            Redirect(DetermineReviewPageController.determineRoute(service))
          else if (requestSessionData.isUKJourney)
            Redirect(AddressLookupPostcodeController.displayPage(service))
          else
            Redirect(
              subscriptionFlowManager.stepInformation(HowCanWeIdentifyYouSubscriptionFlowPage).nextPage.url(service)
            )
      )

  private def getPageContent()(implicit request: Request[AnyContent]): Map[String, String] =
    requestSessionData.userSelectedOrganisationType match {
      case Some(CdsOrganisationType.Company) =>
        Map(
          "hintMessage"    -> "cds.matching.row-organisation.utr.hint",
          "headingMessage" -> "subscription-journey.how-confirm-identity.utr.third-org.heading",
          "subHeading"     -> "cds.matching.row-organisation.utr.subheading",
          "infoMessage"    -> "cds.navigation.corporation-utr-message",
          "findUtrText"    -> "cds.navigation.find-lost-utr"
        )
      case _ =>
        Map(
          "hintMessage"    -> defaultHintMessage,
          "headingMessage" -> defaultHeadingMessage,
          "subHeading"     -> "subscription-journey.how-confirm-identity.utr.subheading",
          "infoMessage"    -> "subscription-journey.navigation.self-utr-message",
          "findUtrText"    -> "subscription.navigation.find-lost-utr"
        )
    }

}
