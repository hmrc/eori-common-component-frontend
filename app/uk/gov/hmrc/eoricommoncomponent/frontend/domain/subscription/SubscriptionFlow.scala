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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription

import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.SubscriptionFlowConfig
import uk.gov.hmrc.eoricommoncomponent.frontend.models.{
  AutoEnrolment,
  JourneyType,
  LongJourney,
  Service,
  SubscribeJourney
}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.DataUnavailableException

object SubscriptionFlows {

  private val soleTraderRegExistingEoriFlowConfig = createFlowConfig(
    List(
      NameDobDetailsSubscriptionFlowPage,
      HowCanWeIdentifyYouSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage
    )
  )

  private val corporateRegExistingEoriFlowConfig = createFlowConfig(
    List(
      NameUtrDetailsSubscriptionFlowPage,
      DateOfEstablishmentSubscriptionFlowPageMigrate,
      AddressDetailsSubscriptionFlowPage
    )
  )

  private val rowIndividualFlowConfig = createFlowConfig(
    List(
      NameDobDetailsSubscriptionFlowPage,
      UtrSubscriptionFlowPage,
      NinoSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate,
      ContactAddressSubscriptionFlowPage,
      ConfirmContactAddressSubscriptionFlowPage
    )
  )

  private val rowOrganisationFlowConfig = createFlowConfig(
    List(
      NameDetailsSubscriptionFlowPage,
      UtrSubscriptionFlowPage,
      AddressDetailsSubscriptionFlowPage,
      RowDateOfEstablishmentSubscriptionFlowPage,
      ContactDetailsSubscriptionFlowPageMigrate,
      ContactAddressSubscriptionFlowPage,
      ConfirmContactAddressSubscriptionFlowPage
    )
  )

  val flows: Map[SubscriptionFlow, SubscriptionFlowConfig] = Map(
    OrganisationFlow    -> corporateRegExistingEoriFlowConfig,
    SoleTraderFlow      -> soleTraderRegExistingEoriFlowConfig,
    IndividualFlow      -> soleTraderRegExistingEoriFlowConfig,
    RowOrganisationFlow -> rowOrganisationFlowConfig,
    RowIndividualFlow   -> rowIndividualFlowConfig
  )

  private def createFlowConfig(flowStepList: List[SubscriptionPage]): SubscriptionFlowConfig =
    SubscriptionFlowConfig(
      pageBeforeFirstFlowPage = UserLocationPage,
      flowStepList,
      pageAfterLastFlowPage = ReviewDetailsPageSubscription
    )

  def apply(subscriptionFlow: SubscriptionFlow): SubscriptionFlowConfig = flows(subscriptionFlow)
}

case class SubscriptionFlowInfo(stepNumber: Int, totalSteps: Int, nextPage: SubscriptionPage)

sealed abstract class SubscriptionFlow(val name: String, val isIndividualFlow: Boolean)

case object OrganisationFlow extends SubscriptionFlow("migration-eori-Organisation", isIndividualFlow = false)

case object IndividualFlow extends SubscriptionFlow("migration-eori-Individual", isIndividualFlow = true)

case object SoleTraderFlow extends SubscriptionFlow("migration-eori-sole-trader", isIndividualFlow = true)

case object RowOrganisationFlow
    extends SubscriptionFlow("migration-eori-row-utrNino-enabled-Organisation", isIndividualFlow = false)

case object RowIndividualFlow
    extends SubscriptionFlow("migration-eori-row-utrNino-enabled-Individual", isIndividualFlow = true)

object SubscriptionFlow {

  def apply(flowName: String): SubscriptionFlow =
    SubscriptionFlows.flows.keys
      .find(_.name == flowName)
      .fold(throw DataUnavailableException(s"Unknown Subscription flowname $flowName"))(identity)

}

sealed abstract class SubscriptionPage() {
  def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String
}

case object ContactDetailsSubscriptionFlowPageMigrate extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ContactDetailsController
      .createForm(service)
      .url

}

case object ConfirmContactAddressSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ConfirmContactAddressController
      .displayPage(service)
      .url

}

case object ContactAddressSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.ContactAddressController
      .displayPage(service)
      .url

}

case object UtrSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.HaveUtrSubscriptionController
      .createForm(service)
      .url

}

case object NinoSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.HaveNinoSubscriptionController
      .createForm(service)
      .url

}

case object AddressDetailsSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.AddressController.createForm(service).url

}

case object NameUtrDetailsSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.NameIDOrgController
      .createForm(service)
      .url

}

case object NameDetailsSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.NameOrgController
      .createForm(service)
      .url

}

case object NameDobDetailsSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.migration.routes.NameDobSoleTraderController
      .createForm(service)
      .url

}

case object HowCanWeIdentifyYouSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.HowCanWeIdentifyYouController
      .createForm(service)
      .url

}

case object RowDateOfEstablishmentSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.DateOfEstablishmentController
      .createForm(service)
      .url

}

case object DateOfEstablishmentSubscriptionFlowPageMigrate extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription.routes.DateOfEstablishmentController
      .createForm(service)
      .url

}

case object EmailSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes.WhatIsYourEmailController
      .createForm(service, subscribeJourney)
      .url

}

case object CheckYourEmailSubscriptionFlowPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.email.routes.CheckYourEmailController
      .createForm(service, subscribeJourney)
      .url

}

case object ReviewDetailsPageSubscription extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.routes.DetermineReviewPageController
      .determineRoute(service)
      .url

}

case object UserLocationPage extends SubscriptionPage {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    uk.gov.hmrc.eoricommoncomponent.frontend.controllers.registration.routes.UserLocationController
      .form(service)
      .url

}

case class PreviousPage(someUrl: String) extends SubscriptionPage() {

  override def url(service: Service, subscribeJourney: SubscribeJourney = SubscribeJourney(LongJourney)): String =
    someUrl

}
