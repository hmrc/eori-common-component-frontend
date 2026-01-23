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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration

import play.api.Logging
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.{AddressController, DetermineReviewPageController}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.{RowIndividualFlow, RowOrganisationFlow}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.subscriptionUtrForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, RequestSessionData}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.how_can_we_identify_you_utr

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GetUtrSubscriptionController @Inject() (
  authAction: AuthAction,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  getUtrSubscriptionView: how_can_we_identify_you_utr,
  subscriptionDetailsService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with Logging {

  def createForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) {
      implicit request => (_: LoggedInUserWithEnrolments) =>
        populateView(isInReviewMode = false, service)
    }

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) {
      implicit request => (_: LoggedInUserWithEnrolments) =>
        populateView(isInReviewMode = true, service)
    }

  private def populateView(isInReviewMode: Boolean, service: Service)(implicit request: Request[AnyContent]) =
    requestSessionData.userSelectedOrganisationType match {
      case Some(_) =>
        subscriptionDetailsService.cachedCustomsId.map {
          case Some(Utr(id)) =>
            Ok(
              getUtrSubscriptionView(
                subscriptionUtrForm.fill(IdMatchModel(id)),
                getPageContent(),
                isInReviewMode,
                routes.GetUtrSubscriptionController.submit(isInReviewMode, service),
                service
              )
            )

          case _ =>
            Ok(
              getUtrSubscriptionView(
                subscriptionUtrForm,
                getPageContent(),
                isInReviewMode,
                routes.GetUtrSubscriptionController.submit(isInReviewMode, service),
                service
              )
            )
        }
      case None =>
        // $COVERAGE-OFF$
        logger.error("GetUtrSubscriptionController: populateView - No organisation type selected by user")
        // $COVERAGE-ON$
        noOrgTypeSelected
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) {
      implicit request => (_: LoggedInUserWithEnrolments) =>
        requestSessionData.userSelectedOrganisationType match {
          case Some(orgType) =>
            subscriptionUtrForm.bindFromRequest().fold(
              formWithErrors =>
                Future.successful(
                  BadRequest(
                    getUtrSubscriptionView(
                      formWithErrors,
                      getPageContent(),
                      isInReviewMode,
                      routes.GetUtrSubscriptionController.submit(isInReviewMode, service),
                      service
                    )
                  )
                ),
              formData => cacheAndContinue(isInReviewMode, formData, service, orgType)
            )
          case None =>
            // $COVERAGE-OFF$
            logger.error("GetUtrSubscriptionController: submit - No organisation type selected by user")
            // $COVERAGE-ON$
            noOrgTypeSelected
        }
    }

  private def cacheAndContinue(
    isInReviewMode: Boolean,
    form: IdMatchModel,
    service: Service,
    orgType: CdsOrganisationType
  )(implicit request: Request[AnyContent]): Future[Result] =
    cacheUtr(form, orgType).map(_ =>
      if (isInReviewMode && !isItRowJourney())
        Redirect(DetermineReviewPageController.determineRoute(service))
      else
        Redirect(AddressController.createForm(service))
    )

  private def isItRowJourney()(implicit request: Request[AnyContent]): Boolean =
    requestSessionData.userSubscriptionFlow == RowOrganisationFlow ||
      requestSessionData.userSubscriptionFlow == RowIndividualFlow

  private def cacheUtr(form: IdMatchModel, orgType: CdsOrganisationType)(implicit request: Request[_]): Future[Unit] =
    if (orgType == CdsOrganisationType.Company)
      subscriptionDetailsService.cachedNameDetails.flatMap {
        case Some(nameDetails) =>
          subscriptionDetailsService.cacheNameAndCustomsId(nameDetails.name, Utr(form.id))
        case _ =>
          // $COVERAGE-OFF$
          logger.error("GetUtrSubscriptionController: cacheUtr - No business name cached")
          // $COVERAGE-ON$
          noBusinessName

      }
    else subscriptionDetailsService.cacheCustomsId(Utr(form.id))

  private lazy val noOrgTypeSelected: Nothing = throw DataUnavailableException("No organisation type selected by user")
  private lazy val noBusinessName: Nothing    = throw DataUnavailableException("No business name cached")

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
          "hintMessage"    -> UtrSubscriptionMessages.defaultHintMessage,
          "headingMessage" -> UtrSubscriptionMessages.defaultHeadingMessage,
          "subHeading"     -> "subscription-journey.how-confirm-identity.utr.subheading",
          "infoMessage"    -> "subscription-journey.navigation.self-utr-message",
          "findUtrText"    -> "subscription.navigation.find-lost-utr"
        )
    }

}

object UtrSubscriptionMessages {
  val defaultHeadingMessage = "subscription-journey.how-confirm-identity.utr.heading"
  val defaultHintMessage    = "cds.matching.row-organisation.utr.hint"
}
