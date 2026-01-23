/*
 * Copyright 2026 HM Revenue & Customs
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
import play.api.libs.json.{JsValue, Json}
import play.mvc.Http.HeaderNames.CONTENT_TYPE
import play.mvc.Http.MimeTypes.JSON
import play.mvc.Http.Status.{NOT_FOUND, OK, SERVICE_UNAVAILABLE}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}

object CheckEoriNumberService {

  val processingDate: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2000, 1, 1, 1, 1), ZoneId.of("Europe/London"))

  private val responseWithValidEori: JsValue = Json.arr(
    Json.obj(
      "eori"           -> "GB89898989898989",
      "valid"          -> true,
      "processingDate" -> processingDate,
      "unknown"        -> "this is not expected but should be ignored"
    )
  )

  private def endpoint(eori: String) = s"/check-eori-number/check-eori/$eori"

  def returnEoriValidCheck(eori: String): Unit =
    stubTheCheckEoriNumberResponse(endpoint(eori), responseWithValidEori.toString(), OK)

  def returnEoriInvalidCheck(eori: String): Unit =
    stubTheCheckEoriNumberResponse(endpoint(eori), "", NOT_FOUND)

  def returnEoriUndeterminedCheck(eori: String): Unit =
    stubTheCheckEoriNumberResponse(endpoint(eori), "", SERVICE_UNAVAILABLE)

  def stubTheCheckEoriNumberResponse(url: String, response: String, status: Int): Unit =
    stubFor(
      get(urlEqualTo(url))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withBody(response)
            .withHeader(CONTENT_TYPE, JSON)
        )
    )

}
