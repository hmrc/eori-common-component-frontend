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

package util

import uk.gov.hmrc.eoricommoncomponent.frontend.models.{AutoEnrolment, LongJourney, Service, SubscribeJourney}

trait TestData {

  val subscribeJourneyLong: SubscribeJourney  = SubscribeJourney(LongJourney)
  val subscribeJourneyShort: SubscribeJourney = SubscribeJourney(AutoEnrolment)

  val atarService: Service =
    Service(
      "atar",
      "HMRC-ATAR-ORG",
      "ATaR",
      Some("/test-atar/callback"),
      "ATaR Service",
      "",
      Some("/test-atar/feedback")
    )

  val gvmsService: Service =
    Service(
      "gagmr",
      "HMRC-GVMS-ORG",
      "GaGMR",
      Some("/test-gagmr/callback"),
      "GVMS Service",
      "",
      Some("/test-gvms/feedback")
    )

  val otherService: Service =
    Service(
      "other",
      "HMRC-OTHER-ORG",
      "Other",
      Some("/other-service/callback"),
      "Other Service",
      "",
      Some("/other-service/feedback")
    )

  val cdsService: Service =
    Service(
      "cds",
      "HMRC-CUS-ORG",
      "CDS",
      Some("/test-cds/callback"),
      "the Customs Declaration Service (CDS)",
      "",
      Some("/test-cds/feedback")
    )

}
