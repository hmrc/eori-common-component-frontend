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
import play.api.mvc._
import play.api.Logger
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.CdsController
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.AuthAction
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowManager
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{Journey, Service}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{RequestSessionData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.organisation.OrgTypeLookup
import uk.gov.hmrc.eoricommoncomponent.frontend.services.registration.RegistrationConfirmService
import uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.registration.confirm_contact_details
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.{
  sub01_outcome_processing,
  sub01_outcome_rejected
}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmContactDetailsController @Inject() (
  authAction: AuthAction,
  registrationConfirmService: RegistrationConfirmService,
  requestSessionData: RequestSessionData,
  cdsFrontendDataCache: SessionCache,
  orgTypeLookup: OrgTypeLookup,
  subscriptionFlowManager: SubscriptionFlowManager,
  taxEnrolmentsService: TaxEnrolmentsService,
  mcc: MessagesControllerComponents,
  confirmContactDetailsView: confirm_contact_details,
  sub01OutcomeProcessingView: sub01_outcome_processing,
  sub01OutcomeRejected: sub01_outcome_rejected
)(implicit ec: ExecutionContext)
    extends CdsController(mcc) {

  private val logger = Logger(this.getClass)

  def form(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => _: LoggedInUserWithEnrolments =>
      cdsFrontendDataCache.registrationDetails flatMap {
        case individual: RegistrationDetailsIndividual =>
          Future.successful(
            Ok(
              confirmContactDetailsView(
                individual.name,
                concatenateAddress(individual),
                individual.customsId,
                None,
                YesNoWrongAddress.createForm(),
                service,
                journey
              )
            )
          )

        case org: RegistrationDetailsOrganisation =>
          orgTypeLookup.etmpOrgType map {
            case Some(ot) =>
              Ok(
                confirmContactDetailsView(
                  org.name,
                  concatenateAddress(org),
                  org.customsId,
                  Some(ot),
                  YesNoWrongAddress.createForm(),
                  service,
                  journey
                )
              )
            case None =>
              logger.warn("[ConfirmContactDetailsController.form] organisation type None")
              cdsFrontendDataCache.remove
              Redirect(OrganisationTypeController.form(service, journey))
          }
        case _ =>
          logger.warn("[ConfirmContactDetailsController.form] registrationDetails not found")
          cdsFrontendDataCache.remove
          Future.successful(Redirect(OrganisationTypeController.form(service, journey)))
      }
    }

  def submit(service: Service, journey: Journey.Value): Action[AnyContent] =
    authAction.ggAuthorisedUserWithEnrolmentsAction { implicit request => implicit loggedInUser =>
      YesNoWrongAddress
        .createForm()
        .bindFromRequest()
        .fold(
          formWithErrors =>
            cdsFrontendDataCache.registrationDetails flatMap {
              case individual: RegistrationDetailsIndividual =>
                Future.successful(
                  BadRequest(
                    confirmContactDetailsView(
                      individual.name,
                      concatenateAddress(individual),
                      individual.customsId,
                      None,
                      formWithErrors,
                      service,
                      journey
                    )
                  )
                )
              case org: RegistrationDetailsOrganisation =>
                orgTypeLookup.etmpOrgType map {
                  case Some(ot) =>
                    BadRequest(
                      confirmContactDetailsView(
                        org.name,
                        concatenateAddress(org),
                        org.customsId,
                        Some(ot),
                        formWithErrors,
                        service,
                        journey
                      )
                    )
                  case None =>
                    logger.warn("[ConfirmContactDetailsController.submit] organisation type None")
                    cdsFrontendDataCache.remove
                    Redirect(OrganisationTypeController.form(service, journey))
                }
              case _ =>
                logger.warn("[ConfirmContactDetailsController.submit] registrationDetails not found")
                cdsFrontendDataCache.remove
                Future.successful(Redirect(OrganisationTypeController.form(service, journey)))
            },
          areDetailsCorrectAnswer =>
            cdsFrontendDataCache.subscriptionDetails flatMap (
              subDetails =>
                subDetails.addressDetails match {
                  case Some(a) =>
                    cdsFrontendDataCache
                      .saveSubscriptionDetails(subDetails.copy(addressDetails = Some(a)))
                      .flatMap { _ =>
                        determineRoute(
                          registrationConfirmService,
                          areDetailsCorrectAnswer.areDetailsCorrect,
                          service,
                          journey
                        )
                      }

                  case None =>
                    cdsFrontendDataCache.registrationDetails flatMap (
                      d =>
                        cdsFrontendDataCache
                          .saveSubscriptionDetails(subDetails.copy(addressDetails = Some(concatenateAddress(d))))
                          .flatMap { _ =>
                            determineRoute(
                              registrationConfirmService,
                              areDetailsCorrectAnswer.areDetailsCorrect,
                              service,
                              journey
                            )
                          }
                    )
                }
            )
        )
    }

  def processing(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name          <- cdsFrontendDataCache.registrationDetails.map(_.name)
        processedDate <- cdsFrontendDataCache.sub01Outcome.map(_.processedDate)
      } yield Ok(sub01OutcomeProcessingView(Some(name), processedDate))
  }

  def rejected(service: Service): Action[AnyContent] = authAction.ggAuthorisedUserWithEnrolmentsAction {
    implicit request => _: LoggedInUserWithEnrolments =>
      for {
        name          <- cdsFrontendDataCache.registrationDetails.map(_.name)
        processedDate <- cdsFrontendDataCache.sub01Outcome.map(_.processedDate)
      } yield Ok(sub01OutcomeRejected(Some(name), processedDate, service))
  }

  private def determineRoute(
    registrationConfirmService: RegistrationConfirmService,
    detailsCorrect: YesNoWrong,
    service: Service,
    journey: Journey.Value
  )(implicit request: Request[AnyContent], loggedInUser: LoggedInUserWithEnrolments) =
    detailsCorrect match {
      case Yes =>
        registrationConfirmService.currentSubscriptionStatus flatMap {
          case NewSubscription | SubscriptionRejected =>
            onNewSubscription(service, journey)
          case SubscriptionProcessing =>
            Future.successful(Redirect(ConfirmContactDetailsController.processing(service)))
          case SubscriptionExists =>
            handleExistingSubscription(service, journey: Journey.Value)
          case status =>
            throw new IllegalStateException(s"Invalid subscription status : $status")
        }
      case No =>
        registrationConfirmService
          .clearRegistrationData(loggedInUser)
          .map(
            _ =>
              Redirect(
                uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.OrganisationTypeController
                  .form(service, journey)
              )
          )
      case WrongAddress =>
        Future.successful(
          Redirect(
            uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController
              .createForm(service, journey)
          )
        )
      case _ =>
        throw new IllegalStateException(
          "YesNoWrongAddressForm field somehow had a value that wasn't yes, no, wrong address, or empty"
        )
    }

  private def onNewSubscription(service: Service, journey: Journey.Value)(implicit
    request: Request[AnyContent]
  ): Future[Result] = {
    lazy val noSelectedOrganisationType =
      requestSessionData.userSelectedOrganisationType.isEmpty
    cdsFrontendDataCache.registrationDetails flatMap {
      case _: RegistrationDetailsIndividual if noSelectedOrganisationType =>
        Future.successful(
          Redirect(
            uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ConfirmIndividualTypeController
              .form(service, journey)
          )
        )

      case _ =>
        subscriptionFlowManager.startSubscriptionFlow(service, journey).map {
          case (page, newSession) => Redirect(page.url(service)).withSession(newSession)
        }
    }
  }

  private def handleExistingSubscription(service: Service, journey: Journey.Value)(implicit
    hc: HeaderCarrier
  ): Future[Result] =
    cdsFrontendDataCache.registrationDetails.flatMap(
      rd =>
        taxEnrolmentsService.doesEnrolmentExist(rd.safeId).map {
          case true =>
            Redirect(SignInWithDifferentDetailsController.form(service, journey))
          case false =>
            Redirect(
              SubscriptionRecoveryController
                .complete(service, journey)
            )
        }
    )

  private def concatenateAddress(registrationDetails: RegistrationDetails): AddressViewModel =
    AddressViewModel(registrationDetails.address)

}
