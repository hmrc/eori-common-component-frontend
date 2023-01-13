/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.RegisterWithEoriAndIdRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.messaging.subscription.SubscriptionRequest
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.PayloadCache

import javax.inject.{Inject, Singleton}

@Singleton
class TestHelperController @Inject() (cc: ControllerComponents) extends AbstractController(cc) {

  def payloads(key: String) = Action {

    val payloadKey = key match {
      case "subscription_create" => PayloadCache.SubscriptionCreate
      case "biz_match"           => PayloadCache.BusinessMatch
    }

    val res = payloadKey match {
      case PayloadCache.SubscriptionCreate =>
        val someData = PayloadCache.payloads.getOrElse(
          PayloadCache.SubscriptionCreate,
          throw new RuntimeException("Ive lost my data!!! : (")
        )
        val payload = someData match {
          case SubscriptionRequest(scr) => SubscriptionRequest(scr)
        }
        Json.toJson(payload)
      case PayloadCache.BusinessMatch =>
        val someData = PayloadCache.payloads.getOrElse(
          PayloadCache.BusinessMatch,
          throw new RuntimeException("Ive lost my data!!! : (")
        )
        val payload = someData match {
          case RegisterWithEoriAndIdRequest(rc, rd) => RegisterWithEoriAndIdRequest(rc, rd)
        }
        Json.toJson(payload)
      case _ => throw new RuntimeException("Something bad has happened")
    }

    Ok(res)
  }

}
