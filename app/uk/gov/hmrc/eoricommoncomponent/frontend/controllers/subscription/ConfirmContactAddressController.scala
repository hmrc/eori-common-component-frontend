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
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.ConfirmContactAddressSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.email.EmailForm.{
  confirmContactAddressYesNoAnswerForm,
  YesNo
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.confirm_contact_address

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmContactAddressController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  subscriptionBusinessService: SubscriptionBusinessService,
  subscriptionDetailsService: SubscriptionDetailsService,
  subscriptionFlowManager: SubscriptionFlowManager,
  contactAddressView: confirm_contact_address
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayPage(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.contactAddress.flatMap { address =>
        address match {
          case Some(address) =>
            Future.successful(
              Ok(contactAddressView(confirmContactAddressYesNoAnswerForm, service, address.toContactAddressViewModel))
            )
          case None => Future.successful(Redirect(ContactAddressController.displayPage(service)))
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
                formWithErrors =>
                  Future.successful(
                    BadRequest(contactAddressView(formWithErrors, service, address.toContactAddressViewModel))
                  ),
                answer => locationByAnswer(answer, service)
              )
          case None => Future.successful(Redirect(ContactAddressController.displayPage(service)))
        }
      }

    }

  private def locationByAnswer(answer: YesNo, service: Service)(implicit request: Request[AnyContent]): Future[Result] =
    answer match {
      case theAnswer if theAnswer.isYes =>
        Future.successful(
          Redirect(
            subscriptionFlowManager
              .stepInformation(ConfirmContactAddressSubscriptionFlowPage)
              .nextPage
              .url(service)
          )
        )
      case _ => Future.successful(Redirect(ContactAddressController.displayPage(service)))
    }

}
