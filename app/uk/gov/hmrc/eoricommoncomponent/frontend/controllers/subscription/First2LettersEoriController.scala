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
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm.EoriRegion
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.EoriPrefixForm.EoriRegion.{EU, GB}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Service, SubscribeJourney}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.first_2_letters_eori_number

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class First2LettersEoriController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  sessionCache: SessionCache,
  first2LettersEoriView: first_2_letters_eori_number
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  val form: Form[EoriRegion] = EoriPrefixForm.eoriPrefixForm

  def form(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      sessionCache.getFirst2LettersEori.map { optEoriRegion =>
        Ok(first2LettersEoriView(form, optEoriRegion, false, service))
      }
    }

  def submit(service: Service, isInReviewMode: Boolean): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => (_: LoggedInUserWithEnrolments) =>
      form.bindFromRequest().fold(
        formWithErrors =>
          Future.successful(BadRequest(first2LettersEoriView(
            formWithErrors,
            None,
            isInReviewMode,
            service
          ))),
        region =>
          sessionCache.saveFirst2LettersEori(region).map {
            case GB => Redirect("https://www.gov.uk/eori")
            case EU => Redirect("https://www.gov.uk/check-eori-number")
          }
      )
    }

}
