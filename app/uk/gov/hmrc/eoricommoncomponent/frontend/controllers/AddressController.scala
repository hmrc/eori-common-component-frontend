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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.subscription.AddressDetailsForm.addressDetailsCreateForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.AddressService

import javax.inject.{Inject, Singleton}

@Singleton
class AddressController @Inject() (
  authorise: AuthAction,
  mcc: MessagesControllerComponents,
  addressService: AddressService
) extends CdsController(mcc) {

  def createForm(service: Service): Action[AnyContent] =
    authorise.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      addressService.populateFormViewCached(isInReviewMode = false, service)
    }

  def reviewForm(service: Service): Action[AnyContent] =
    authorise.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      addressService.populateReviewViewCached(isInReviewMode = true, service)
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authorise.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      addressService.handleFormDataAndRedirect(addressDetailsCreateForm(), isInReviewMode, service)
    }

}
