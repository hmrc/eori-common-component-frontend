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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration

import javax.inject.{Inject, Singleton}
import play.api.Application
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.VatRegisteredUkController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.VatGroupController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.YesNo
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.isleOfManYesNoAnswerForm
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.isle_of_man

import scala.concurrent.ExecutionContext

@Singleton
class IsleOfManController @Inject() (
  override val currentApp: Application,
  override val authConnector: AuthConnector,
  view: isle_of_man,
  mcc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def form(): Action[AnyContent] = Action { implicit request =>
    Ok(view(isleOfManYesNoAnswerForm()))
  }

  def submit(): Action[AnyContent] = Action { implicit request =>
    isleOfManYesNoAnswerForm()
      .bindFromRequest()
      .fold(
        formWithErrors => BadRequest(view(formWithErrors)),
        isleOfManYesNoAnswerForm => destinationsByAnswer(isleOfManYesNoAnswerForm)
      )
  }

  def destinationsByAnswer(yesNoAnswer: YesNo)(implicit request: Request[AnyContent]): Result = yesNoAnswer match {
    case theAnswer if theAnswer.isYes => Redirect(VatRegisteredUkController.form())
    case _                            => Redirect(VatGroupController.createForm(Journey.Register))
  }

}
