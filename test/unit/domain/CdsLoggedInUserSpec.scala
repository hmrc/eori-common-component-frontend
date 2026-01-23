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

package unit.domain

import base.UnitSpec
import uk.gov.hmrc.auth.core.AffinityGroup.{Individual, Organisation}
import uk.gov.hmrc.auth.core.{Enrolment, Enrolments}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.LoggedInUserWithEnrolments

class CdsLoggedInUserSpec extends UnitSpec {

  "isOrganisation" should {
    "return true when affinity group is organisation" in {
      LoggedInUserWithEnrolments(Some(Organisation), None, Enrolments(Set.empty[Enrolment]), None, None, "credId")
        .isOrganisation shouldEqual true
    }

    "return false when affinity group is not organisation" in {
      LoggedInUserWithEnrolments(Some(Individual), None, Enrolments(Set.empty[Enrolment]), None, None, "credId")
        .isOrganisation shouldEqual false
    }
  }
}
