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

package util.builders.matching

import uk.gov.hmrc.eoricommoncomponent.frontend.domain.Utr

object OrganisationUtrFormBuilder {

  val ValidUtrId: String = "2108834503"

  val ValidUtr: Utr                          = Utr(ValidUtrId)
  val ValidUtrRequest: Map[String, String]   = Map("have-utr" -> "true", "utr" -> ValidUtrId)
  val NoUtrRequest: Map[String, String]      = Map("have-utr" -> "false")
  val InvalidUtrRequest: Map[String, String] = Map("" -> "")
}
