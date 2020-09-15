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
import org.joda.time.LocalDate
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.OrgTypeNotFoundException
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{EtmpOrganisationType, LoggedInUserWithEnrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.date_of_establishment
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DateOfEstablishmentController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  dateOfEstablishmentView: date_of_establishment,
  orgTypeLookup: OrgTypeLookup
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        maybeCachedDateModel <- subscriptionBusinessService.maybeCachedDateEstablished
        orgType              <- orgTypeLookup.etmpOrgType
      } yield populateView(
        maybeCachedDateModel,
        isInReviewMode = false,
        orgType.getOrElse(throw new OrgTypeNotFoundException()),
        journey
      )
    }

  private def populateView(
    cachedDate: Option[LocalDate],
    isInReviewMode: Boolean,
    orgType: EtmpOrganisationType,
    journey: Journey.Value
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Result = {
    val form = cachedDate.fold(subscriptionDateOfEstablishmentForm)(subscriptionDateOfEstablishmentForm.fill)
    Ok(dateOfEstablishmentView(form, isInReviewMode, orgType, UserLocation.isRow(requestSessionData), journey))
  }

  def reviewForm(journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      for {
        cachedDateModel <- fetchDate
        orgType         <- orgTypeLookup.etmpOrgType
      } yield populateView(
        Some(cachedDateModel),
        isInReviewMode = true,
        orgType.getOrElse(throw new OrgTypeNotFoundException()),
        journey
      )
    }

  private def fetchDate(implicit hc: HeaderCarrier): Future[LocalDate] =
    subscriptionBusinessService.getCachedDateEstablished

  def submit(isInReviewMode: Boolean, journey: Journey.Value): Action[AnyContent] =
    ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionDateOfEstablishmentForm.bindFromRequest.fold(
        formWithErrors =>
          orgTypeLookup.etmpOrgType map {
            case Some(orgType) =>
              BadRequest(
                dateOfEstablishmentView(
                  formWithErrors,
                  isInReviewMode,
                  orgType,
                  UserLocation.isRow(requestSessionData),
                  journey
                )
              )
            case None => throw new OrgTypeNotFoundException()
          },
        date =>
          saveDateEstablished(date).map { _ =>
            if (isInReviewMode)
              Redirect(DetermineReviewPageController.determineRoute(journey))
            else
              Redirect(
                subscriptionFlowManager
                  .stepInformation(getSubscriptionPage(journey, UserLocation.isRow(requestSessionData)))
                  .nextPage
                  .url
              )
          }
      )
    }

  private def saveDateEstablished(date: LocalDate)(implicit hc: HeaderCarrier) =
    subscriptionDetailsHolderService.cacheDateEstablished(date)

  private def getSubscriptionPage(journey: Journey.Value, location: Boolean) =
    (journey, location) match {
      case (Journey.Subscribe, true) => RowDateOfEstablishmentSubscriptionFlowPage
      case (Journey.Subscribe, false) =>
        DateOfEstablishmentSubscriptionFlowPageMigrate
      case _ => DateOfEstablishmentSubscriptionFlowPage
    }

}
