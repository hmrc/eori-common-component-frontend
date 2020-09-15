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

package util.builders

import java.util.UUID

import play.api.Application
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.{CSRFTokenHelper, FakeRequest}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.RequestSessionDataKeys
import uk.gov.hmrc.http.SessionKeys

object SessionBuilder {

  def sessionMap(userId: String): List[(String, String)] = {
    val sessionId = s"session-${UUID.randomUUID}"
    List(SessionKeys.sessionId -> sessionId, SessionKeys.userId -> userId)
  }

  def addToken[T](fakeRequest: FakeRequest[T])(implicit app: Application): FakeRequest[T] =
    new FakeRequest(CSRFTokenHelper.addCSRFToken(fakeRequest))

  def buildRequestWithSession(userId: String)(implicit app: Application) =
    addToken(FakeRequest().withSession(sessionMap(userId): _*))

  def buildRequestWithSessionAndFormValues(userId: String, form: Map[String, String])(implicit
    app: Application
  ): FakeRequest[AnyContentAsFormUrlEncoded] =
    buildRequestWithSession(userId).withFormUrlEncodedBody(form.toList: _*)

  def buildRequestWithFormValues(
    form: Map[String, String]
  )(implicit app: Application): FakeRequest[AnyContentAsFormUrlEncoded] =
    buildRequestWithSessionNoUserAndToken.withFormUrlEncodedBody(form.toList: _*)

  def buildRequestWithSessionNoUser = {
    val sessionId = s"session-${UUID.randomUUID}"
    FakeRequest().withSession(SessionKeys.sessionId -> sessionId)
  }

  def buildRequestWithSessionNoUserAndToken(implicit app: Application) = {
    val sessionId = s"session-${UUID.randomUUID}"
    addToken(FakeRequest().withSession(SessionKeys.sessionId -> sessionId))
  }

  def buildRequestWithSessionAndPathNoUserAndBasedInUkNotSelected(method: String, path: String) = {
    val sessionId = s"session-${UUID.randomUUID}"
    FakeRequest(method, path).withSession(SessionKeys.sessionId -> sessionId)
  }

  def buildRequestWithSessionAndPathNoUser(method: String, path: String) = {
    val sessionId = s"session-${UUID.randomUUID}"
    FakeRequest(method, path).withSession(SessionKeys.sessionId -> sessionId, "visited-uk-page" -> "true")
  }

  def buildRequestWithSessionAndPath(path: String, userId: String, method: String = "GET")(implicit app: Application) =
    FakeRequest(method, path).withSession(sessionMap(userId): _*)

  def buildRequestWithSessionAndPathAndFormValues(
    method: String,
    path: String,
    userId: String,
    form: Map[String, String]
  )(implicit app: Application): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest(method, path).withSession(sessionMap(userId): _*).withFormUrlEncodedBody(form.toList: _*)

  def buildRequestWithSessionAndOrgType(userId: String, orgTypeId: String)(implicit app: Application) = {
    val list    = (RequestSessionDataKeys.selectedOrganisationType -> orgTypeId) :: sessionMap(userId)
    val request = FakeRequest().withSession(list: _*)
    addToken(request)
  }

}
