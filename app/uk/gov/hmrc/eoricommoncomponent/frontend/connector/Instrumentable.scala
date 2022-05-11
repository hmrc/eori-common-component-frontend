/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.eoricommoncomponent.frontend.connector

import play.api.Logger
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.PayloadCache
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.PayloadCache.Payload

trait Instrumentable {

  private val logger = Logger(this.getClass)
  val cache          = PayloadCache.payloads

  def sampleData[A](key: Payload, value: A): Unit = {
    logger.info(s"SAMPLING: payload ${key}, data ${value}")
    cache += (key -> value)
  }

}
