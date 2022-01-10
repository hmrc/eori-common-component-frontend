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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.SecuritySignOutController
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.ContactAddressModel
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.check_your_details

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

@Singleton
class CheckYourDetailsController @Inject() (
  authAction: AuthAction,
  cdsFrontendCache: SessionCache,
  mcc: MessagesControllerComponents,
  checkYourDetailsView: check_your_details,
  requestSessionData: RequestSessionData
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def reviewDetails(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction {
      implicit request => _: LoggedInUserWithEnrolments =>
        for {
          subscriptionDetailsHolder <- cdsFrontendCache.subscriptionDetails
          email                     <- cdsFrontendCache.email
          addressLookupParams       <- cdsFrontendCache.addressLookupParams
        } yield Ok(
          checkYourDetailsView(
            isThirdCountrySubscription = isThirdCountrySubscriptionFlow,
            isIndividualSubscriptionFlow = requestSessionData.userSubscriptionFlow.isIndividualFlow,
            organisationType = requestSessionData.userSelectedOrganisationType,
            addressDetails = subscriptionDetailsHolder.addressDetails,
            contactDetails = subscriptionDetailsHolder.contactDetails,
            eoriNumber = subscriptionDetailsHolder.eoriNumber,
            existingEori = subscriptionDetailsHolder.existingEoriNumber,
            email = Some(email),
            nameIdOrganisationDetails = subscriptionDetailsHolder.nameIdOrganisationDetails,
            nameOrganisationDetails = subscriptionDetailsHolder.nameOrganisationDetails,
            nameDobDetails = subscriptionDetailsHolder.nameDobDetails,
            dateEstablished = subscriptionDetailsHolder.dateEstablished,
            idDetails = subscriptionDetailsHolder.idDetails,
            customsId = subscriptionDetailsHolder.customsId,
            registeredCountry = subscriptionDetailsHolder.registeredCompany,
            addressLookupParams = addressLookupParams,
            contactAddress = subscriptionDetailsHolder.contactAddress,
            service = service
          )
        )
    }

  private def isThirdCountrySubscriptionFlow(implicit request: Request[AnyContent]): Boolean =
    requestSessionData.userSubscriptionFlow match {
      case RowOrganisationFlow | RowIndividualFlow => true
      case _                                       => false
    }

}
