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
import play.api.i18n.Messages
import play.api.mvc.{Action, _}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.Individual
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.subscriptionUtrForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.MatchingService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GYEHowCanWeIdentifyYouUtrController @Inject() (
  authAction: AuthAction,
  matchingService: MatchingService,
  mcc: MessagesControllerComponents,
  howCanWeIdentifyYouView: how_can_we_identify_you_utr,
  cdsFrontendDataCache: SessionCache
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      Future.successful(Ok(howCanWeIdentifyYouView(subscriptionUtrForm, isInReviewMode = false, service, journey)))
    }

  def submit(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => loggedInUser: LoggedInUserWithEnrolments =>
      subscriptionUtrForm.bindFromRequest.fold(
        formWithErrors =>
          Future.successful(
            BadRequest(howCanWeIdentifyYouView(formWithErrors, isInReviewMode = false, service, journey))
          ),
        formData =>
          matchOnId(formData, InternalId(loggedInUser.internalId)).map {
            case true =>
              Redirect(ConfirmContactDetailsController.form(service, journey))
            case false =>
              matchNotFoundBadRequest(formData, service, journey)
          }
      )
    }

  private def matchOnId(formData: IdMatchModel, internalId: InternalId)(implicit hc: HeaderCarrier): Future[Boolean] =
    retrieveNameDobFromCache().flatMap(ind => matchingService.matchIndividualWithId(Utr(formData.id), ind, internalId))

  private def matchNotFoundBadRequest(individualFormData: IdMatchModel, service: Service, journey: Journey.Value)(
    implicit request: Request[AnyContent]
  ): Result = {
    val errorForm = subscriptionUtrForm
      .withGlobalError(Messages("cds.matching-error.individual-not-found"))
      .fill(individualFormData)
    BadRequest(howCanWeIdentifyYouView(errorForm, isInReviewMode = false, service, journey))
  }

  // TODO Get rid of `.get`. Now if there is no information Exception will be thrown, understand what should happen if this is not provided
  private def retrieveNameDobFromCache()(implicit hc: HeaderCarrier): Future[Individual] =
    cdsFrontendDataCache.subscriptionDetails.map(_.nameDobDetails.get).map { nameDobDetails =>
      Individual.withLocalDate(
        firstName = nameDobDetails.firstName,
        middleName = None,
        lastName = nameDobDetails.lastName,
        dateOfBirth = nameDobDetails.dateOfBirth
      )
    }

}
