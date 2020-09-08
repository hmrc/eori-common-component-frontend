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

package uk.gov.hmrc.customs.rosmfrontend.controllers.auth

import play.api.mvc.{AnyContent, Request, Result}
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.auth.core.NoActiveSession
import uk.gov.hmrc.customs.rosmfrontend.controllers.JourneyTypeFromUrl
import uk.gov.hmrc.customs.rosmfrontend.models.Journey.{Register, Subscribe}
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects

trait AuthRedirectSupport extends AuthRedirects with JourneyTypeFromUrl {

  // TODO Get rid of the whole application here, this can be a class with injected config and env
  // Injecting the whole application to have access to injector or in different places to config is a bad practice
  // https://github.com/google/guice/wiki/InjectOnlyDirectDependencies#inject-only-direct-dependencies
  def currentApp: Application

  override lazy val config: Configuration = currentApp.configuration
  override lazy val env: Environment = Environment(currentApp.path, currentApp.classloader, currentApp.mode)

  private def continueUrlKey(implicit request: Request[AnyContent]) = {
    val visitedUkPage: Boolean = request.session.get("visited-uk-page").getOrElse("false").toBoolean
    journeyFromUrl match {
      case Subscribe if visitedUkPage =>
        "external-url.company-auth-frontend.continue-url-subscribe-from-are-you-based-in-uk"
      case Subscribe     => "external-url.company-auth-frontend.continue-url-subscribe"
      case Register => "external-url.company-auth-frontend.continue-url"
      case _           => throw new IllegalArgumentException("No valid journey found in URL: " + request.path)
    }
  }

  private def getContinueUrl(implicit request: Request[AnyContent]) = config.get[String](continueUrlKey)

  def withAuthRecovery(implicit request: Request[AnyContent]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession => toGGLogin(continueUrl = getContinueUrl)
  }
}
