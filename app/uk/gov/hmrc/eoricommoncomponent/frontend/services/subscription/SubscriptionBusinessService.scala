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

package uk.gov.hmrc.eoricommoncomponent.frontend.services.subscription

import play.api.mvc.Request
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{AddressViewModel, ContactAddressModel}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{DataUnavailableException, SessionCache}

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionBusinessService @Inject() (sessionCache: SessionCache)(implicit ec: ExecutionContext) {

  def cachedContactDetailsModel(implicit request: Request[_]): Future[Option[ContactDetailsModel]] =
    sessionCache.subscriptionDetails map (_.contactDetails)

  def getCachedDateEstablished(implicit request: Request[_]): Future[LocalDate] =
    sessionCache.subscriptionDetails map {
      _.dateEstablished.getOrElse(throw DataUnavailableException("No Date Of Establishment Cached"))
    }

  def maybeCachedDateEstablished(implicit request: Request[_]): Future[Option[LocalDate]] =
    sessionCache.subscriptionDetails map (_.dateEstablished)

  def cachedEoriNumber(implicit request: Request[_]): Future[Option[String]] =
    sessionCache.subscriptionDetails map (_.eoriNumber)

  def addressOrException(implicit request: Request[_]): Future[AddressViewModel] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.addressDetails.getOrElse(throw DataUnavailableException("No Address Details Cached"))
    }

  def address(implicit request: Request[_]): Future[Option[AddressViewModel]] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.addressDetails
    }

  def contactAddress(implicit request: Request[_]): Future[Option[ContactAddressModel]] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.contactAddress
    }

  def getCachedNameIdViewModel(implicit request: Request[_]): Future[NameIdOrganisationMatchModel] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameIdOrganisationDetails.getOrElse(
        throw DataUnavailableException("No Name/Utr/Id Details Cached")
      )
    }

  def getCachedNameViewModel(implicit request: Request[_]): Future[NameOrganisationMatchModel] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameOrganisationDetails.getOrElse(throw DataUnavailableException("No Name Cached"))
    }

  def cachedNameIdOrganisationViewModel(implicit request: Request[_]): Future[Option[NameIdOrganisationMatchModel]] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameIdOrganisationDetails
    }

  def cachedNameOrganisationViewModel(implicit request: Request[_]): Future[Option[NameOrganisationMatchModel]] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameOrganisationDetails
    }

  def getCachedSubscriptionNameDobViewModel(implicit request: Request[_]): Future[NameDobMatchModel] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameDobDetails.getOrElse(throw DataUnavailableException("No Name/Dob Details Cached"))
    }

  def cachedSubscriptionNameDobViewModel(implicit request: Request[_]): Future[Option[NameDobMatchModel]] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.nameDobDetails
    }

  def getCachedCustomsId(implicit request: Request[_]): Future[Option[CustomsId]] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.customsId
    }

  def getCachedNinoOrUtrChoice(implicit request: Request[_]): Future[Option[String]] =
    sessionCache.subscriptionDetails map { subscriptionDetails =>
      subscriptionDetails.formData.ninoOrUtrChoice
    }

  def retrieveSubscriptionDetailsHolder(implicit request: Request[_]): Future[SubscriptionDetails] =
    sessionCache.subscriptionDetails

}
