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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers

import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{affinityGroup, allEnrolments, internalId, email => ggEmail, _}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AccessController, AuthRedirectSupport, EnrolmentExtractor}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

abstract class CdsController(mcc: MessagesControllerComponents)(implicit ec: ExecutionContext)
  extends FrontendController(mcc) with I18nSupport with AuthorisedFunctions with AuthRedirectSupport
    with EnrolmentExtractor with AccessController {

  def authConnector: AuthConnector

  // TODO Get rid of this application
  // Injecting the whole application to have access to injector or in different places to config is a bad practice
  // https://github.com/google/guice/wiki/InjectOnlyDirectDependencies#inject-only-direct-dependencies
  def currentApp: Application

  override def messagesApi: MessagesApi =
    currentApp.injector.instanceOf[MessagesApi]

  private type RequestProcessorSimple =
    Request[AnyContent] => LoggedInUserWithEnrolments => Future[Result]
  private type RequestProcessorExtended =
    Request[AnyContent] => Option[String] => LoggedInUserWithEnrolments => Future[Result]

  private val baseRetrievals = ggEmail and credentialRole and affinityGroup
  private val extendedRetrievals = baseRetrievals and internalId and allEnrolments and groupIdentifier

  def ggAuthorisedUserAction(requestProcessor: Request[AnyContent] => Future[Result]): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway))
        .retrieve(baseRetrievals) {
          case currentUserEmail ~ userCredentialRole ~ userAffinityGroup =>
            permitUserOrRedirect(userAffinityGroup, userCredentialRole, currentUserEmail)(requestProcessor(request))
        } recover withAuthRecovery(request)
    }

  def ggAuthorisedUserWithEnrolmentsAction(requestProcessor: RequestProcessorSimple): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(GovernmentGateway))
        .retrieve(extendedRetrievals) {
          case currentUserEmail ~ userCredentialRole ~ userAffinityGroup ~ userInternalId ~ userAllEnrolments ~ groupId =>
            transformRequest(
              Right(requestProcessor),
              userAffinityGroup,
              userInternalId,
              userAllEnrolments,
              currentUserEmail,
              userCredentialRole,
              groupId
            )
        } recover withAuthRecovery(request)
    }

  private def transformRequest(
                                requestProcessor: Either[RequestProcessorExtended, RequestProcessorSimple],
                                userAffinityGroup: Option[AffinityGroup],
                                userInternalId: Option[String],
                                userAllEnrolments: Enrolments,
                                currentUserEmail: Option[String],
                                userCredentialRole: Option[CredentialRole],
                                groupId: Option[String]
                              )(implicit request: Request[AnyContent]) = {
    val loggedInUser =
      LoggedInUserWithEnrolments(userAffinityGroup, userInternalId, userAllEnrolments, currentUserEmail, groupId)

    permitUserOrRedirect(userAffinityGroup, userCredentialRole, currentUserEmail) {
      requestProcessor fold(_ (request)(userInternalId)(loggedInUser), _ (request)(loggedInUser))
    }
  }
}
