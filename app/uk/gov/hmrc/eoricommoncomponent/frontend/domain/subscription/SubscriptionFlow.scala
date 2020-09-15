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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription

import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Journey

object SubscriptionFlows {

  private val individualFlowConfig =
    createFlowConfig(Journey.Register, List(ContactDetailsSubscriptionFlowPageGetEori, EoriConsentSubscriptionFlowPage))

  private val soleTraderFlowConfig = createFlowConfig(
    Journey.Register,
    List(
      ContactDetailsSubscriptionFlowPageGetEori,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val corporateFlowConfig = createFlowConfig(
    Journey.Register,
    List(
      DateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageGetEori,
      BusinessShortNameSubscriptionFlowPage,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      //VatGroupFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val partnershipFlowConfig = createFlowConfig(
    Journey.Register,
    List(
      DateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageGetEori,
      BusinessShortNameSubscriptionFlowPage,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val thirdCountryIndividualFlowConfig =
    createFlowConfig(Journey.Register, List(ContactDetailsSubscriptionFlowPageGetEori, EoriConsentSubscriptionFlowPage))

  private val thirdCountrySoleTraderFlowConfig = createFlowConfig(
    Journey.Register,
    List(
      ContactDetailsSubscriptionFlowPageGetEori,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val thirdCountryCorporateFlowConfig = createFlowConfig(
    Journey.Register,
    List(
      DateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageGetEori,
      BusinessShortNameSubscriptionFlowPage,
      SicCodeSubscriptionFlowPage,
      VatRegisteredUkSubscriptionFlowPage,
      //VatGroupFlowPage,
      VatDetailsSubscriptionFlowPage,
      VatRegisteredEuSubscriptionFlowPage,
      VatEUIdsSubscriptionFlowPage,
      VatEUConfirmSubscriptionFlowPage,
      EoriConsentSubscriptionFlowPage
    )
  )

  private val soleTraderRegExistingEoriFlowConfig = createFlowConfig(
    Journey.Subscribe,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDobDetailsSubscriptionFlowPage,
      HowCanWeIdentifyYouSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage
    )
  )

  private val corporateRegExistingEoriFlowConfig = createFlowConfig(
    Journey.Subscribe,
    List(
      EoriNumberSubscriptionFlowPage,
      NameUtrDetailsSubscriptionFlowPage,
      DateOfEstablishmentSubscriptionFlowPageMigrate,
      AddressDetailsSubscriptionFlowPage
    )
  )

  private val migrationEoriRowSoleTraderAndIndividualFlowConfig = createFlowConfig(
    Journey.Subscribe,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDobDetailsSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate
    )
  )

  private val migrationEoriRowSoleTraderAndIndividualFlowConfigUtrNinoEnabled = createFlowConfig(
    Journey.Subscribe,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDobDetailsSubscriptionFlowPage,
      UtrSubscriptionFlowPage,
      NinoSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate
    )
  )

  private val migrationEoriRowCorporateFlowConfig = createFlowConfig(
    Journey.Subscribe,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDetailsSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      RowDateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate
    )
  )

  private val migrationEoriRowCorporateFlowConfigUtrNinoEnabled = createFlowConfig(
    Journey.Subscribe,
    List(
      EoriNumberSubscriptionFlowPage,
      NameDetailsSubscriptionFlowPage,
      UtrSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      RowDateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate
    )
  )

  val flows: Map[SubscriptionFlow, SubscriptionFlowConfig] = Map(
    OrganisationSubscriptionFlow                               -> corporateFlowConfig,
    PartnershipSubscriptionFlow                                -> partnershipFlowConfig,
    SoleTraderSubscriptionFlow                                 -> soleTraderFlowConfig,
    IndividualSubscriptionFlow                                 -> individualFlowConfig,
    ThirdCountryOrganisationSubscriptionFlow                   -> thirdCountryCorporateFlowConfig,
    ThirdCountrySoleTraderSubscriptionFlow                     -> thirdCountrySoleTraderFlowConfig,
    ThirdCountryIndividualSubscriptionFlow                     -> thirdCountryIndividualFlowConfig,
    MigrationEoriOrganisationSubscriptionFlow                  -> corporateRegExistingEoriFlowConfig,
    MigrationEoriSoleTraderSubscriptionFlow                    -> soleTraderRegExistingEoriFlowConfig,
    MigrationEoriIndividualSubscriptionFlow                    -> soleTraderRegExistingEoriFlowConfig,
    MigrationEoriRowOrganisationSubscriptionFlow               -> migrationEoriRowCorporateFlowConfig,
    MigrationEoriRowSoleTraderSubscriptionFlow                 -> migrationEoriRowSoleTraderAndIndividualFlowConfig,
    MigrationEoriRowIndividualSubscriptionFlow                 -> migrationEoriRowSoleTraderAndIndividualFlowConfig,
    MigrationEoriRowOrganisationSubscriptionUtrNinoEnabledFlow -> migrationEoriRowCorporateFlowConfigUtrNinoEnabled,
    MigrationEoriRowIndividualsSubscriptionUtrNinoEnabledFlow  -> migrationEoriRowSoleTraderAndIndividualFlowConfigUtrNinoEnabled
  )

  private def createFlowConfig(journey: Journey.Value, flowStepList: List[SubscriptionPage]): SubscriptionFlowConfig =
    journey match {
      case Journey.Subscribe =>
        SubscriptionFlowConfig(
          pageBeforeFirstFlowPage = RegistrationConfirmPage,
          flowStepList,
          pageAfterLastFlowPage = ReviewDetailsPageSubscription
        )
      case _ =>
        SubscriptionFlowConfig(
          pageBeforeFirstFlowPage = RegistrationConfirmPage,
          flowStepList,
          pageAfterLastFlowPage = ReviewDetailsPageGetYourEORI
        )
    }

  def apply(subscriptionFlow: SubscriptionFlow): SubscriptionFlowConfig = flows(subscriptionFlow)
}

case class SubscriptionFlowInfo(stepNumber: Int, totalSteps: Int, nextPage: SubscriptionPage)

sealed abstract class SubscriptionFlow(val name: String, val isIndividualFlow: Boolean)

case object OrganisationSubscriptionFlow extends SubscriptionFlow("Organisation", isIndividualFlow = false)

case object PartnershipSubscriptionFlow extends SubscriptionFlow("Partnership", isIndividualFlow = false)

case object IndividualSubscriptionFlow extends SubscriptionFlow("Individual", isIndividualFlow = true)

case object ThirdCountryOrganisationSubscriptionFlow
    extends SubscriptionFlow(ThirdCountryOrganisation.id, isIndividualFlow = false)

case object ThirdCountrySoleTraderSubscriptionFlow
    extends SubscriptionFlow(ThirdCountrySoleTrader.id, isIndividualFlow = true)

case object ThirdCountryIndividualSubscriptionFlow
    extends SubscriptionFlow(ThirdCountryIndividual.id, isIndividualFlow = true)

case object SoleTraderSubscriptionFlow extends SubscriptionFlow(SoleTrader.id, isIndividualFlow = true)

case object MigrationEoriOrganisationSubscriptionFlow
    extends SubscriptionFlow("migration-eori-Organisation", isIndividualFlow = false)

case object MigrationEoriIndividualSubscriptionFlow
    extends SubscriptionFlow("migration-eori-Individual", isIndividualFlow = true)

case object MigrationEoriSoleTraderSubscriptionFlow
    extends SubscriptionFlow("migration-eori-sole-trader", isIndividualFlow = true)

case object MigrationEoriRowOrganisationSubscriptionFlow
    extends SubscriptionFlow("migration-eori-row-Organisation", isIndividualFlow = false)

case object MigrationEoriRowSoleTraderSubscriptionFlow
    extends SubscriptionFlow("migration-eori-row-sole-trader", isIndividualFlow = true)

case object MigrationEoriRowIndividualSubscriptionFlow
    extends SubscriptionFlow("migration-eori-row-Individual", isIndividualFlow = true)

case object MigrationEoriRowOrganisationSubscriptionUtrNinoEnabledFlow
    extends SubscriptionFlow("migration-eori-row-utrNino-enabled-Organisation", isIndividualFlow = false)

case object MigrationEoriRowIndividualsSubscriptionUtrNinoEnabledFlow
    extends SubscriptionFlow("migration-eori-row-utrNino-enabled-Individual", isIndividualFlow = true)

object SubscriptionFlow {

  def apply(flowName: String): SubscriptionFlow =
    SubscriptionFlows.flows.keys
      .find(_.name == flowName)
      .fold(throw new IllegalStateException(s"Incorrect Subscription flowname $flowName"))(identity)

}

sealed abstract class SubscriptionPage(val url: String)

case object ContactDetailsSubscriptionFlowPageGetEori
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ContactDetailsController
        .createForm(journey = Journey.Register)
        .url
    )

case object ContactDetailsSubscriptionFlowPageMigrate
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ContactDetailsController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object UtrSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.HaveUtrSubscriptionController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object NinoSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.HaveNinoSubscriptionController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object AddressDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController.createForm(journey =
        Journey.Subscribe
      ).url
    )

case object NameUtrDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.NameIDOrgController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object NameDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.NameOrgController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object NameDobDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.NameDobSoleTraderController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object HowCanWeIdentifyYouSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.HowCanWeIdentifyYouController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object RowDateOfEstablishmentSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.DateOfEstablishmentController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object DateOfEstablishmentSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.DateOfEstablishmentController
        .createForm(journey = Journey.Register)
        .url
    )

case object DateOfEstablishmentSubscriptionFlowPageMigrate
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.DateOfEstablishmentController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object VatRegisteredUkSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.VatRegisteredUkController
        .createForm(journey = Journey.Register)
        .url
    )

//case object VatGroupFlowPage extends SubscriptionPage(uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.VatGroupController.createForm(journey = Journey.Register).url)

case object BusinessShortNameSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.BusinessShortNameController
        .createForm(journey = Journey.Register)
        .url
    )

case object VatDetailsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.VatDetailsController
        .createForm(journey = Journey.Register)
        .url
    )

case object VatRegisteredEuSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.VatRegisteredEuController
        .createForm(journey = Journey.Register)
        .url
    )

case object VatEUIdsSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.VatDetailsEuController
        .createForm(journey = Journey.Register)
        .url
    )

case object VatEUConfirmSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.VatDetailsEuConfirmController
        .createForm(journey = Journey.Register)
        .url
    )

case object EoriConsentSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.DisclosePersonalDetailsConsentController
        .createForm(journey = Journey.Register)
        .url
    )

case object SicCodeSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.SicCodeController
        .createForm(journey = Journey.Register)
        .url
    )

case object EoriNumberSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.WhatIsYourEoriController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object EmailSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes.WhatIsYourEmailController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object CheckYourEmailSubscriptionFlowPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes.CheckYourEmailController
        .createForm(journey = Journey.Subscribe)
        .url
    )

case object ReviewDetailsPageGetYourEORI
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
        .determineRoute(journey = Journey.Register)
        .url
    )

case object ReviewDetailsPageSubscription
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
        .determineRoute(journey = Journey.Subscribe)
        .url
    )

case object RegistrationConfirmPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.ConfirmContactDetailsController
        .form(journey = Journey.Register)
        .url
    )

case object ConfirmIndividualTypePage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ConfirmIndividualTypeController
        .form(journey = Journey.Register)
        .url
    )

case object UserLocationPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.UserLocationController
        .form(journey = Journey.Subscribe)
        .url
    )

case object BusinessDetailsRecoveryPage
    extends SubscriptionPage(
      uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.BusinessDetailsRecoveryController
        .form(journey = Journey.Register)
        .url
    )

case class PreviousPage(override val url: String) extends SubscriptionPage(url)
