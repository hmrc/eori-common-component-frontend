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

package uk.gov.hmrc.eoricommoncomponent.frontend.controllers.subscription

import play.api.Logger
import play.api.mvc.{AnyContent, Request, Session}
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{
  DataUnavailableException,
  RequestSessionData,
  SessionCache
}
import uk.gov.hmrc.eoricommoncomponent.frontend.util.Constants.ONE

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class SubscriptionFlowConfig(
  pageBeforeFirstFlowPage: SubscriptionPage,
  pagesInOrder: List[SubscriptionPage],
  pageAfterLastFlowPage: SubscriptionPage
) {

  private def lastPageInTheFlow(currentPos: Int): Boolean = currentPos == pagesInOrder.size - ONE

  def determinePageBeforeSubscriptionFlow(uriBeforeSubscriptionFlow: Option[String]): SubscriptionPage =
    uriBeforeSubscriptionFlow.fold(pageBeforeFirstFlowPage)(url => PreviousPage(url))

  def stepInformation(currentPage: SubscriptionPage): SubscriptionFlowInfo = {

    val currentPos = pagesInOrder.indexOf(currentPage)

    val nextPage = if (lastPageInTheFlow(currentPos)) pageAfterLastFlowPage else pagesInOrder(currentPos + ONE)

    SubscriptionFlowInfo(stepNumber = currentPos + ONE, totalSteps = pagesInOrder.size, nextPage = nextPage)
  }

}

@Singleton
class SubscriptionFlowManager @Inject() (requestSessionData: RequestSessionData, cdsFrontendDataCache: SessionCache)(
  implicit ec: ExecutionContext
) {

  private val logger = Logger(this.getClass)

  def currentSubscriptionFlow(implicit request: Request[AnyContent]): SubscriptionFlow =
    requestSessionData.userSubscriptionFlow

  def stepInformation(currentPage: SubscriptionPage)(implicit request: Request[AnyContent]): SubscriptionFlowInfo =
    SubscriptionFlows(currentSubscriptionFlow)
      .stepInformation(currentPage)

  def startSubscriptionFlow(
    previousPage: Option[SubscriptionPage] = None,
    cdsOrganisationType: CdsOrganisationType,
    service: Service
  )(implicit request: Request[AnyContent]): Future[(SubscriptionPage, Session)] = {
    val maybePreviousPageUrl = previousPage.map(page => page.url(service))
    cdsFrontendDataCache.registrationDetails map { registrationDetails =>
      val flow = selectFlow(registrationDetails, cdsOrganisationType)

      logger.info(s"select Subscription flow: ${flow.name}")
      (
        SubscriptionFlows(flow).pagesInOrder.head,
        requestSessionData.storeUserSubscriptionFlow(
          flow,
          SubscriptionFlows(flow).determinePageBeforeSubscriptionFlow(maybePreviousPageUrl).url(service)
        )
      )
    }
  }

  private def selectFlow(registrationDetails: RegistrationDetails, orgType: CdsOrganisationType)(implicit
    request: Request[AnyContent]
  ): SubscriptionFlow = {
    val isRow = UserLocation.isRow(requestSessionData)

    val subscribePrefix = (isRow, registrationDetails.customsId) match {
      case (true, None) => "migration-eori-row-utrNino-enabled-"
      case (true, _)    => "migration-eori-row-"
      case _            => "migration-eori-"
    }

    val selectedFlow: SubscriptionFlow =
      registrationDetails match {
        case _: RegistrationDetailsOrganisation =>
          SubscriptionFlow(subscribePrefix + "Organisation")
        case _: RegistrationDetailsIndividual =>
          SubscriptionFlow(subscribePrefix + "Individual")
        case _ => throw DataUnavailableException("RegistrationDetails is not available in cache")
      }

    SubscriptionFlows.flows.keys.find(_.name == (subscribePrefix + orgType.id)).getOrElse(selectedFlow)
  }

}
