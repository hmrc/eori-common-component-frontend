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

import play.api.data.Form
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriNumberViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.SubscriptionForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.what_is_your_eori_eu

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class WhatIsYourEoriEUController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  whatIsYourEoriEuView: what_is_your_eori_eu
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  val form: Form[EoriNumberViewModel] = SubscriptionForm.eoriNumberEuForm

  def form(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      subscriptionDetailsHolderService.cachedEoriNumber.map { eori =>
        val cachedEori = EoriNumberViewModel(eori.getOrElse(""))
        Ok(whatIsYourEoriEuView(form.fill(cachedEori), false, service))
      }
    }

  def submit(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      form.bindFromRequest().fold(
        formWithErrors =>
          Future.successful(BadRequest(whatIsYourEoriEuView(
            formWithErrors,
            false,
            service
          ))),
        formData =>
          subscriptionDetailsHolderService.cacheEoriNumber(formData.eoriNumber).map { _ =>
            Redirect("https://www.gov.uk/check-eori-number")
          }
      )
    }

}
