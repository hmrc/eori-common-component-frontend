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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription

import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{SubscriptionBusinessService, SubscriptionDetailsService}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.confirm_contact_address
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailForm.{confirmContactAddressYesNoAnswerForm, YesNo}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ConfirmContactAddressSubscriptionFlowPage

@Singleton
class ConfirmContactAddressController @Inject()(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionFlowManager: SubscriptionFlowManager,
  contactAddressView: confirm_contact_address
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayPage(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
        subscriptionBusinessService.contactAddress.flatMap { address =>
          address match {
            case Some(address) =>
              Future.successful(Ok(contactAddressView(confirmContactAddressYesNoAnswerForm, service, address.toContactAddressViewModel)))
            case None => ???
          }
        }

      }

  def submit(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.contactAddress.flatMap { address =>
        address match {
          case Some(address) =>
            confirmContactAddressYesNoAnswerForm.bindFromRequest
              .fold(
                formWithErrors => Future.successful(BadRequest(contactAddressView(formWithErrors, service, address.toContactAddressViewModel))),
                address =>
                  Future.successful(Redirect(
                    subscriptionFlowManager
                      .stepInformation(ConfirmContactAddressSubscriptionFlowPage)
                      .nextPage
                      .url(service)
                  )))
          case None => ???
        }
      }

    }

}
