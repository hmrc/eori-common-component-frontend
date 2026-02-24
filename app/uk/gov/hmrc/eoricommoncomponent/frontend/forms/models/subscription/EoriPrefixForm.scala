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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription

import play.api.data.*
import play.api.data.Forms.*
import play.api.data.format.Formatter
import play.api.libs.json.*

object EoriPrefixForm {

  enum EoriRegion:
    case GB, EU

  object EoriRegion {

    def fromString(value: String): Option[EoriRegion] =
      values.find(_.toString == value)

    given Format[EoriRegion] =
      Format(
        Reads {
          case JsString(value) =>
            fromString(value)
              .map(JsSuccess(_))
              .getOrElse(JsError(s"Unknown EoriRegion: $value"))

          case _ =>
            JsError("String value expected")
        },
        Writes(region => JsString(region.toString))
      )

  }

  implicit val eoriRegionFormatter: Formatter[EoriRegion] =
    new Formatter[EoriRegion] {

      private val errorKey = "cds.subscription.first-2-letters-eori.page-error"

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], EoriRegion] =
        data
          .get(key)
          .filter(_.nonEmpty)
          .flatMap(EoriRegion.fromString)
          .toRight(Seq(FormError(key, errorKey)))

      override def unbind(key: String, value: EoriRegion): Map[String, String] =
        Map(key -> value.toString)

    }

  def eoriPrefixForm: Form[EoriRegion] = Form(single("region" -> of[EoriRegion]))
}
