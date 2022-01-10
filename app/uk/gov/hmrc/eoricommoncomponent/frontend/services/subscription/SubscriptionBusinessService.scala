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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription

import javax.inject.{Inject, Singleton}
import java.time.LocalDate
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.AddressViewModel
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.SessionCache
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionBusinessService @Inject() (cdsFrontendDataCache: SessionCache)(implicit ec: ExecutionContext) {

  def cachedContactDetailsModel(implicit hc: HeaderCarrier): Future[Option[ContactDetailsModel]] =
    cdsFrontendDataCache.subscriptionDetails map (_.contactDetails)

  def getCachedDateEstablished(implicit hc: HeaderCarrier): Future[LocalDate] =
    cdsFrontendDataCache.subscriptionDetails map {
      _.dateEstablished.getOrElse(throw new IllegalStateException("No Date Of Establishment Cached"))
    }

  def maybeCachedDateEstablished(implicit hc: HeaderCarrier): Future[Option[LocalDate]] =
    cdsFrontendDataCache.subscriptionDetails map (_.dateEstablished)

  def cachedEoriNumber(implicit hc: HeaderCarrier): Future[Option[String]] =
    cdsFrontendDataCache.subscriptionDetails map (_.eoriNumber)

  def addressOrException(implicit hc: HeaderCarrier): Future[AddressViewModel] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.addressDetails.getOrElse(throw new IllegalStateException("No Address Details Cached"))
    }

  def address(implicit hc: HeaderCarrier): Future[Option[AddressViewModel]] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.addressDetails
    }

  def getCachedNameIdViewModel(implicit hc: HeaderCarrier): Future[NameIdOrganisationMatchModel] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameIdOrganisationDetails.getOrElse(
        throw new IllegalStateException("No Name/Utr/Id Details Cached")
      )
    }

  def getCachedNameViewModel(implicit hc: HeaderCarrier): Future[NameOrganisationMatchModel] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameOrganisationDetails.getOrElse(throw new IllegalStateException("No Name Cached"))
    }

  def cachedNameIdOrganisationViewModel(implicit hc: HeaderCarrier): Future[Option[NameIdOrganisationMatchModel]] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameIdOrganisationDetails
    }

  def cachedNameOrganisationViewModel(implicit hc: HeaderCarrier): Future[Option[NameOrganisationMatchModel]] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameOrganisationDetails
    }

  def getCachedSubscriptionNameDobViewModel(implicit hc: HeaderCarrier): Future[NameDobMatchModel] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameDobDetails.getOrElse(throw new IllegalStateException("No Name/Dob Details Cached"))
    }

  def cachedSubscriptionNameDobViewModel(implicit hc: HeaderCarrier): Future[Option[NameDobMatchModel]] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameDobDetails
    }

  def getCachedCustomsId(implicit hc: HeaderCarrier): Future[Option[CustomsId]] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.customsId
    }

  def getCachedNinoOrUtrChoice(implicit hc: HeaderCarrier): Future[Option[String]] =
    cdsFrontendDataCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.formData.ninoOrUtrChoice
    }

  def retrieveSubscriptionDetailsHolder(implicit hc: HeaderCarrier): Future[SubscriptionDetails] =
    cdsFrontendDataCache.subscriptionDetails

}
