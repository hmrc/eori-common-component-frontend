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

import common.Users._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.{
  RegistrationIdentification,
  RegistrationIdentificationOutcome,
  SafeId
}

object RegistrationIdentificationBuilder {

  val ACtOrgUserRegId =
    RegistrationIdentification(ACtOrgUser.internalId, safeId = "XE0000123456789", sapNumber = "0123456789")

  val SampleUserRegId = RegistrationIdentificationOutcome("int-0987654321", safeId = Some("XE0000123456789"))
}
