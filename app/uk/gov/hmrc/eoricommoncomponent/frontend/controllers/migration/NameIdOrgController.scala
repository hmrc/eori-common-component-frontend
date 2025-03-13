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
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.EtmpLegalStatus
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription.{
  SubscriptionBusinessService,
  SubscriptionDetailsService
}
import uk.gov.hmrc.eoricommoncomponent.frontend.util.Require.requireThatUrlValue
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.nameId

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NameIDOrgController @Inject() (
  authAction: AuthAction,
  subscriptionBusinessService: SubscriptionBusinessService,
  requestSessionData: RequestSessionData,
  sessionCache: SessionCache,
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
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.cachedNameIdOrganisationViewModel flatMap { cachedNameUtrViewModel =>
        val selectedOrganisationType =
          requestSessionData.userSelectedOrganisationType.map(_.id)
        selectedOrganisationType match {
          case Some(orgType) =>
            validateOrganisationType(orgType)
            populateOkView(
              cachedNameUtrViewModel,
              OrganisationTypeConfigurations(orgType),
              isInReviewMode = false,
              service
            )
          case None => throw DataUnavailableException("Organisation type is not available in cache")
        }

      }
    }

  private def validateOrganisationType(orgType: String): Unit =
    requireThatUrlValue(OrganisationTypeConfigurations.contains(orgType), invalidOrganisationType(orgType))

  def reviewForm(service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      subscriptionBusinessService.getCachedNameIdViewModel flatMap { cdm =>
        val selectedOrganisationType =
          requestSessionData.userSelectedOrganisationType.map(_.id)
        selectedOrganisationType match {
          case Some(orgType) =>
            validateOrganisationType(orgType)
            populateOkView(Some(cdm), OrganisationTypeConfigurations(orgType), isInReviewMode = true, service)
          case None => throw DataUnavailableException("Organisation type is not available in cache")
        }

      }
    }

  def submit(isInReviewMode: Boolean, service: Service): Action[AnyContent] =
    authAction.enrolledUserWithSessionAction(service) { implicit request => _: LoggedInUserWithEnrolments =>
      form.bindFromRequest()
        .fold(
          formWithErrors =>
            sessionCache.registrationDetails map { registrationDetails =>
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
    nameIdOrgViewMode: NameIdOrgViewModel,
    isInReviewMode: Boolean,
    service: Service
  )(implicit request: Request[AnyContent]): Future[Result] = {

    lazy val nameUtrForm = nameUtrViewModel.fold(form)(form.fill)
    sessionCache.registrationDetails map { registrationDetails =>
      Ok(nameIdView(nameUtrForm, registrationDetails, isInReviewMode, nameIdOrgViewMode.displayMode, service))
    }
  }

  private def storeNameUtrDetails(formData: NameIdOrganisationMatchModel, inReviewMode: Boolean, service: Service)(
    implicit request: Request[AnyContent]
  ): Future[Result] =
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

  case class NameIdOrgViewModel(
    matchingServiceType: String,
    displayMode: String,
    isNameAddressRegistrationAvailable: Boolean = false
  ) {
    lazy val form: Form[NameIdOrganisationMatchModel] = nameUtrOrganisationForm

  }

  def invalidOrganisationType(organisationType: String): Any =
    s"Invalid organisation type '$organisationType'."

  private val OrganisationTypeConfigurations: Map[String, NameIdOrgViewModel] =
    Map(
      CdsOrganisationType.CompanyId -> NameIdOrgViewModel(
        EtmpLegalStatus.CorporateBody,
        displayMode = RegisteredCompanyDM
      ),
      CdsOrganisationType.PartnershipId -> NameIdOrgViewModel(EtmpLegalStatus.Partnership, displayMode = PartnershipDM),
      CdsOrganisationType.LimitedLiabilityPartnershipId -> NameIdOrgViewModel(
        EtmpLegalStatus.Llp,
        displayMode = PartnershipLLP
      ),
      CdsOrganisationType.CharityPublicBodyNotForProfitId -> NameIdOrgViewModel(
        EtmpLegalStatus.UnincorporatedBody,
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
  val PartnershipLLP      = "Llp-partnership"
}
