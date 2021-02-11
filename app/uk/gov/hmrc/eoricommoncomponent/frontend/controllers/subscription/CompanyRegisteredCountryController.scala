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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription

import javax.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Request, Result}
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.DateOfEstablishmentController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.CompanyRegisteredCountry
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.countries.Countries
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.SubscriptionDetailsService
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.country

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompanyRegisteredCountryController @Inject() (
  authAction: AuthAction,
  subscriptionDetailsService: SubscriptionDetailsService,
  mcc: MessagesControllerComponents,
  countryPage: country
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  def displayPage(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user =>
      subscriptionDetailsService.cachedRegisteredCountry().map { countryOpt =>
        populateView(countryOpt, service, false)
      }
    }

  def reviewPage(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user =>
      subscriptionDetailsService.cachedRegisteredCountry().map { countryOpt =>
        populateView(countryOpt, service, true)
      }
    }

  private def populateView(country: Option[CompanyRegisteredCountry], service: Service, isInReviewMode: Boolean)(
    implicit request: Request[_]
  ): Result = {

    val form = country.fold(CompanyRegisteredCountry.form())(CompanyRegisteredCountry.form().fill(_))

    val (countries, picker) = Countries.getCountryParametersForAllCountries()

    Ok(countryPage(form, countries, picker, service, isInReviewMode))
  }

  def submit(service: Service, isInReviewMode: Boolean): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit user =>
      CompanyRegisteredCountry
        .form()
        .bindFromRequest()
        .fold(
          formWithErrors => {
            val (countries, picker) = Countries.getCountryParametersForAllCountries()
            Future.successful(BadRequest(countryPage(formWithErrors, countries, picker, service, isInReviewMode)))
          },
          country =>
            subscriptionDetailsService.cacheRegisteredCountry(country).map { _ =>
              if (isInReviewMode)
                Redirect(DetermineReviewPageController.determineRoute(service, Journey.Subscribe))
              else
                Redirect(DateOfEstablishmentController.createForm(service, Journey.Subscribe))
            }
        )
    }

}
