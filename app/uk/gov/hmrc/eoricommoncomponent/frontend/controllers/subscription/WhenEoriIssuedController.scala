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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription

import play.api.mvc.*
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.*
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.*
import uk.gov.hmrc.eoricommoncomponent.frontend.domain. LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm.*
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionData
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.when_eori_issued

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WhenEoriIssuedController @Inject()(
  authAction: AuthAction,
  subscriptionFlowManager: SubscriptionFlowManager,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  requestSessionData: RequestSessionData,
  mcc: MessagesControllerComponents,
  whenEoriIssuedView: when_eori_issued,
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def createForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      for {
        maybeCachedDateModel <- subscriptionBusinessService.maybeCachedDateEstablished
      } yield populateView(maybeCachedDateModel, isInReviewMode = false, service)
    }

  private def populateView(
                            cachedDate: Option[LocalDate],
                            isInReviewMode: Boolean,
                            service: Service
                          )(implicit request: Request[AnyContent]): Result = {
    val form = cachedDate.fold(WhenEoriIssuedForm)(WhenEoriIssuedForm.fill)
    Ok(whenEoriIssuedView(form, isInReviewMode, service))
  }

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      for {
        cachedDateModel <- subscriptionBusinessService.getCachedDateEstablished
      } yield populateView(Some(cachedDateModel), isInReviewMode = true, service)
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      WhenEoriIssuedForm.bindFromRequest().fold(
        formWithErrors => Future.successful(
            BadRequest(
              whenEoriIssuedView(
                formWithErrors,
                isInReviewMode,
                service
              )
            )
        ),
        date =>
          saveDateEstablished(date).map { _ =>
            // TODO
            // Update the code below when we begin work on DDCYLS-8136
            // to integrate the new EU EORI journey into CDS navigation.
            if (isInReviewMode)
              Redirect(DetermineReviewPageController.determineRoute(service))
            else if (requestSessionData.isUKJourney)
              Redirect(routes.AddressLookupPostcodeController.displayPage(service))
            else {
              val page = subscriptionFlowManager
                .stepInformation(getSubscriptionPage(UserLocation.isRow(requestSessionData)))
                .nextPage
              Redirect(page.url(service))
            }
          }
      )
    }

  private def saveDateEstablished(date: LocalDate)(implicit request: Request[_]) =
    subscriptionDetailsHolderService.cacheDateEstablished(date)

  private def getSubscriptionPage(location: Boolean) =
    if (location) RowDateOfEstablishmentSubscriptionFlowPage else DateOfEstablishmentSubscriptionFlowPageMigrate

}
