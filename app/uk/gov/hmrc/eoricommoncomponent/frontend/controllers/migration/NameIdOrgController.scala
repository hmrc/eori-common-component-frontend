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

import play.api.data.Form
import play.api.mvc._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.NameIdOrganisationDisplayMode._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.NameUtrDetailsSubscriptionFlowPage
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.MatchingForms.{
  nameUtrCompanyForm,
  nameUtrOrganisationForm,
  nameUtrPartnershipForm
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{
  DataUnavailableException,
  RequestSessionData,
  SessionCache
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.util.Require.requireThatUrlValue
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.nameId
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameIDOrgController @Inject() (
  authAction: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  requestSessionData: RequestSessionData,
  cdsFrontendDataCache: SessionCache,
  subscriptionFlowManager: SubscriptionFlowManager,
  mcc: MessagesControllerComponents,
  nameIdView: nameId,
  subscriptionDetailsHolderService: SubscriptionDetailsService
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private def form(implicit request: Request[AnyContent]) =
    if (requestSessionData.isPartnership) nameUtrPartnershipForm
    else if (requestSessionData.isCompany) nameUtrCompanyForm
    else nameUtrOrganisationForm

  def createForm(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.cachedNameIdOrganisationViewModel flatMap { cachedNameUtrViewModel =>
        val selectedOrganisationType =
          requestSessionData.userSelectedOrganisationType.map(_.id)
        selectedOrganisationType match {
          case Some(orgType) =>
            validateOrganisationType(orgType)
            populateOkView(
              cachedNameUtrViewModel,
              orgType,
              OrganisationTypeConfigurations(orgType),
              isInReviewMode = false,
              service
            )
          case None => throw DataUnavailableException("Organisation type is not available in cache")
        }

      }
    }

  private def validateOrganisationType(orgType: String) =
    requireThatUrlValue(OrganisationTypeConfigurations.contains(orgType), invalidOrganisationType(orgType))

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedNameIdViewModel flatMap { cdm =>
        val selectedOrganisationType =
          requestSessionData.userSelectedOrganisationType.map(_.id)
        selectedOrganisationType match {
          case Some(orgType) =>
            validateOrganisationType(orgType)
            populateOkView(Some(cdm), orgType, OrganisationTypeConfigurations(orgType), isInReviewMode = true, service)
          case None => throw DataUnavailableException("Organisation type is not available in cache")
        }

      }
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      form.bindFromRequest
        .fold(
          formWithErrors =>
            cdsFrontendDataCache.registrationDetails map { registrationDetails =>
              val selectedOrganisationType =
                requestSessionData.userSelectedOrganisationType.map(_.id)
              selectedOrganisationType match {
                case Some(orgType) =>
                  validateOrganisationType(orgType)
                  BadRequest(
                    nameIdView(
                      formWithErrors,
                      registrationDetails,
                      isInReviewMode,
                      OrganisationTypeConfigurations(orgType).displayMode,
                      service
                    )
                  )
                case None => throw DataUnavailableException("Organisation type is not available in cache")
              }
            },
          formData => storeNameUtrDetails(formData, isInReviewMode, service)
        )
    }

  private def populateOkView(
    nameUtrViewModel: Option[NameIdOrganisationMatchModel],
    organisationType: String,
    conf: Configuration,
    isInReviewMode: Boolean,
    service: Service
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] = {

    lazy val nameUtrForm = nameUtrViewModel.fold(form)(form.fill)
    cdsFrontendDataCache.registrationDetails map { registrationDetails =>
      Ok(nameIdView(nameUtrForm, registrationDetails, isInReviewMode, conf.displayMode, service))
    }
  }

  private def storeNameUtrDetails(
    formData: NameIdOrganisationMatchModel,
    inReviewMode: Boolean,
    service: Service
  )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Future[Result] =
    subscriptionDetailsHolderService
      .cacheNameIdDetails(formData)
      .map(
        _ =>
          if (inReviewMode)
            Redirect(
              uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
                .determineRoute(service)
            )
          else
            Redirect(
              subscriptionFlowManager
                .stepInformation(NameUtrDetailsSubscriptionFlowPage)
                .nextPage
                .url(service)
            )
      )

  trait Configuration {
    def matchingServiceType: String
    def displayMode: String
    def isNameAddressRegistrationAvailable: Boolean
    def form: Form[NameIdOrganisationMatchModel]
    def createCustomsId(id: String): CustomsId
  }

  case class UtrConfiguration(
    matchingServiceType: String,
    displayMode: String,
    isNameAddressRegistrationAvailable: Boolean = false
  ) extends Configuration {
    lazy val form: Form[NameIdOrganisationMatchModel] = nameUtrOrganisationForm
    def createCustomsId(utr: String): Utr             = Utr(utr)
  }

  def invalidOrganisationType(organisationType: String): Any =
    s"Invalid organisation type '$organisationType'."

  private val OrganisationTypeConfigurations: Map[String, Configuration] =
    Map(
      CdsOrganisationType.CompanyId                     -> UtrConfiguration("Corporate Body", displayMode = RegisteredCompanyDM),
      CdsOrganisationType.PartnershipId                 -> UtrConfiguration("Partnership", displayMode = PartnershipDM),
      CdsOrganisationType.LimitedLiabilityPartnershipId -> UtrConfiguration("LLP", displayMode = PartnershipDM),
      CdsOrganisationType.CharityPublicBodyNotForProfitId -> UtrConfiguration(
        "Unincorporated Body",
        displayMode = OrganisationModeDM,
        isNameAddressRegistrationAvailable = true
      )
    )

}

object NameIdOrganisationDisplayMode {
  val RegisteredCompanyDM = "registered-company"
  val CompanyDM           = "company"
  val PartnershipDM       = "partnership"
  val OrganisationModeDM  = "organisation"
}
