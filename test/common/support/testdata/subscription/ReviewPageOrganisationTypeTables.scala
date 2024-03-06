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

package common.support.testdata.subscription

import org.scalatest.prop.TableFor1
import org.scalatest.prop.Tables.Table
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType._

trait ReviewPageOrganisationTypeTables {

  val organisationTypes: TableFor1[CdsOrganisationType] = Table(
    "organisationType",
    Company,
    Partnership,
    LimitedLiabilityPartnership,
    CharityPublicBodyNotForProfit,
    EUOrganisation,
    ThirdCountryOrganisation
  )

  val individualsOnlyOrganisationTypes: TableFor1[CdsOrganisationType] = Table("organisationType", ThirdCountryIndividual, EUIndividual, Individual)

  val soleTradersOnlyOrganisationTypes: TableFor1[CdsOrganisationType] = Table("organisationType", SoleTrader, ThirdCountrySoleTrader)

  val businessDetailsOrganisationTypes: TableFor1[CdsOrganisationType] = organisationTypes

  val contactDetailsOrganisationTypes: TableFor1[CdsOrganisationType] = individualsOnlyOrganisationTypes ++ soleTradersOnlyOrganisationTypes

  val shortenedNameOrganisationTypes: TableFor1[CdsOrganisationType] = organisationTypes ++ soleTradersOnlyOrganisationTypes

}
