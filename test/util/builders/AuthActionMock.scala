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

package util.builders

import base.Injector
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{AnyContentAsEmpty, DefaultActionBuilder}
import play.api.test.Helpers.stubBodyParser
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.auth.{AuthAction, CacheClearOnCompletionAction}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import play.api.mvc.BodyParsers
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers.any
import play.api.mvc.{AnyContent, Request}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.global

trait AuthActionMock extends AnyWordSpec with MockitoSugar with Injector {

  val configuration: Configuration            = instanceOf[Configuration]
  val environment: Environment                = Environment.simple()
  val mockedSessionCacheForAuth: SessionCache = mock[SessionCache]
  when(mockedSessionCacheForAuth.emailOpt(any[Request[AnyContent]]))
    .thenReturn(Future.successful(Some("some@email.com")))
  when(mockedSessionCacheForAuth.isJourneyComplete(any[Request[AnyContent]]))
    .thenReturn(Future.successful(false))

  val actionBuilder: DefaultActionBuilder = DefaultActionBuilder(stubBodyParser(AnyContentAsEmpty))(global)

  def authAction(authConnector: AuthConnector) =
    new AuthAction(
      configuration,
      environment,
      authConnector,
      actionBuilder,
      mockedSessionCacheForAuth,
      instanceOf[BodyParsers.Default],
      instanceOf[CacheClearOnCompletionAction]
    )(global)

}
