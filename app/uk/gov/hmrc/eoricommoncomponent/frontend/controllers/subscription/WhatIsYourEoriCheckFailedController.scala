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

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.DataUnavailableException
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.what_is_your_eori_check_failed

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class WhatIsYourEoriCheckFailedController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  subscriptionDetailsHolderService: SubscriptionDetailsService,
  whatIsYourEoriCheckFailedPage: what_is_your_eori_check_failed
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) with EnrolmentExtractor {

  def displayPage(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _ =>
      subscriptionDetailsHolderService.cachedEoriNumber.map { eori =>
        Ok(whatIsYourEoriCheckFailedPage(eori.getOrElse(throw DataUnavailableException("Eori is not cached")), service))
      }
    }

}
