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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription

import uk.gov.hmrc.eoricommoncomponent.frontend.domain.Eori

import java.time.LocalDateTime

sealed trait SubscriptionResult {
  val processingDate: String
}

case class SubscriptionSuccessful(
  eori: Eori,
  formBundleId: String,
  processingDate: String,
  emailVerificationTimestamp: Option[LocalDateTime]
) extends SubscriptionResult

case class SubscriptionPending(
  formBundleId: String,
  processingDate: String,
  emailVerificationTimestamp: Option[LocalDateTime]
) extends SubscriptionResult

case class SubscriptionFailed(failureReason: String, processingDate: String) extends SubscriptionResult
