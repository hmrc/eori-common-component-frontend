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

package util.externalservices

import com.github.tomakehurst.wiremock.client.WireMock._
import common.User
import play.api.http.Status
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames.AUTHORIZATION
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CustomsId

trait AuthService {

  import uk.gov.hmrc.auth.core.AuthProvider._
  import uk.gov.hmrc.auth.core._
  import uk.gov.hmrc.auth.core.authorise._
  import uk.gov.hmrc.auth.core.retrieve._

  val authUrl                = "/auth/authorise"
  private val authUrlMatcher = urlEqualTo(authUrl)

  private def bearerTokenMatcher(user: User) = equalTo("Bearer " + user.bearerToken)

  def authRequestJson(predicate: Predicate)(retrievals: Retrieval[_]*): String = {
    val js =
      s"""
         |{
         |  "authorise": [${predicate.toJson}],
         |  "retrieve": [${retrievals.flatMap(_.propertyNames).map(Json.toJson(_)).mkString(",")}]
         |}
    """.stripMargin
    js
  }

  def authServiceAuthenticates(user: User): Unit =
    stubFor(
      post(authUrlMatcher)
        .withRequestBody(equalToJson(authRequestJson(AuthProviders(GovernmentGateway))()))
        //      .withHeader(AUTHORIZATION, bearerTokenMatcher(user))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody("{}")
        )
    )

  def authServiceReturnsIds(user: User): Unit = {
    stubFor(
      post(authUrlMatcher)
        .withRequestBody(
          equalToJson(
            authRequestJson(AuthProviders(GovernmentGateway))(
              email and credentialRole and affinityGroup and internalId and allEnrolments and groupIdentifier
            )
          )
        )
        //      .withHeader(AUTHORIZATION, bearerTokenMatcher(user))
        .willReturn(
          aResponse()
            .withStatus(Status.OK)
            .withBody(s"""
               |{
               |  "email": "${user.email.getOrElse("")}",
               |  "credentialRole": "${user.credRole}",
               |  "affinityGroup": ${Json.toJson(user.affinityGroup)(AffinityGroup.jsonFormat)},
               |  "groupIdentifier" : "${user.groupId.getOrElse("groupId-abcd-1234")}"
               |}
            """.stripMargin)
        )
    )

    val enrolmentsResponse = enrolmentsRetrievalJsonPortion(user)
    val response =
      aResponse()
        .withStatus(Status.OK)
        .withBody(s"""
             |{
             |  "internalId": "${user.internalId}",
             |  "email": "${user.email.getOrElse("")}",
             |  "credentialRole": "${user.credRole}",
             |  "affinityGroup": ${Json.toJson(user.affinityGroup)(AffinityGroup.jsonFormat)},
             |  "allEnrolments": [ $enrolmentsResponse ],
             |  "groupIdentifier" : "${user.groupId.getOrElse("groupId-abcd-1234")}"
             |}
            """.stripMargin)

    val authProviderId = Json.toJson(user.credentials.get)(Json.format[Credentials])
    val responseWithCredentials =
      aResponse()
        .withStatus(Status.OK)
        .withBody(Json.parse(s"""
             |{
             |  "internalId": "${user.internalId}",
             |  "credentialRole": "${user.credRole}",
             |  "email": "${user.email.getOrElse("")}",
             |  "affinityGroup": ${Json.toJson(user.affinityGroup)(AffinityGroup.jsonFormat)},
             |  "allEnrolments": [ $enrolmentsResponse ],
             |  "optionalCredentials": $authProviderId,
             |  "groupIdentifier" : "${user.groupId.getOrElse("groupId-abcd-1234")}"
             |}
            """.stripMargin).toString())

    stubFor(
      post(authUrlMatcher)
        .withRequestBody(
          equalToJson(
            authRequestJson(AuthProviders(GovernmentGateway))(
              email and credentialRole and affinityGroup and internalId and allEnrolments and groupIdentifier
            )
          )
        )
        .withHeader(AUTHORIZATION, bearerTokenMatcher(user))
        .willReturn(response)
    )

    stubFor(
      post(authUrlMatcher)
        .withRequestBody(
          equalToJson(
            authRequestJson(AuthProviders(GovernmentGateway))(
              email and credentialRole and affinityGroup and internalId and allEnrolments and groupIdentifier
            )
          )
        )
        .withHeader(AUTHORIZATION, bearerTokenMatcher(user))
        .willReturn(responseWithCredentials)
    )
  }

  private def enrolmentsRetrievalJsonPortion(user: User): String =
    List(
      enrolmentRetrievalJson("IR-CT", "UTR", user.ctUtr),
      enrolmentRetrievalJson("IR-SA", "UTR", user.saUtr),
      enrolmentRetrievalJson("HMRC-NI", "NINO", user.nino)
    ).flatten.mkString(",")

  private def enrolmentRetrievalJson(
    enrolmentKey: String,
    identifierName: String,
    customsId: Option[CustomsId]
  ): Option[String] =
    customsId.map { c =>
      s"""
         |{
         | "key": "$enrolmentKey",
         | "identifiers": [
         |   {
         |     "key": "$identifierName",
         |     "value": "${c.id}"
         |   }
         | ]
         |}
      """.stripMargin
    }

}
