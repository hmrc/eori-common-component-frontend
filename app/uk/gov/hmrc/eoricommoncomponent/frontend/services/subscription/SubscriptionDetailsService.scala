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
import uk.gov.hmrc.eoricommoncomponent.frontend.connector.Save4LaterConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.subscription.SubscriptionDetails
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.registration.ContactDetailsModel
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription.{
  AddressViewModel,
  CompanyRegisteredCountry,
  ContactAddressModel
}
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.eoricommoncomponent.frontend.services.cache.{CachedData, SessionCache}
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.ContactDetailsAdaptor
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionDetailsService @Inject() (
  sessionCache: SessionCache,
  contactDetailsAdaptor: ContactDetailsAdaptor,
  save4LaterConnector: Save4LaterConnector
)(implicit ec: ExecutionContext) {

  def saveKeyIdentifiers(groupId: GroupId, internalId: InternalId, service: Service)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[Unit] = {
    val key = CachedData.groupIdKey
    sessionCache.safeId.flatMap { safeId =>
      val cacheIds = CacheIds(internalId, safeId, Some(service.code))
      save4LaterConnector.put[CacheIds](groupId.id, key, cacheIds)
    }
  }

  def saveSubscriptionDetails(
    insertNewDetails: SubscriptionDetails => SubscriptionDetails
  )(implicit request: Request[_]): Future[Unit] = sessionCache.subscriptionDetails flatMap { subDetails =>
    sessionCache.saveSubscriptionDetails(insertNewDetails(subDetails)).map(_ => ())

  }

  def cacheContactDetails(contactDetailsModel: ContactDetailsModel, isInReviewMode: Boolean = false)(implicit
    request: Request[_]
  ): Future[Unit] =
    contactDetails(contactDetailsModel, isInReviewMode) flatMap { contactDetails =>
      saveSubscriptionDetails(sd => sd.copy(contactDetails = Some(contactDetails)))
    }

  def cacheAddressDetails(address: AddressViewModel)(implicit request: Request[_]): Future[Unit] = {
    def noneForEmptyPostcode(a: AddressViewModel) = a.copy(postcode = a.postcode.filter(_.nonEmpty))
    saveSubscriptionDetails(sd => sd.copy(addressDetails = Some(noneForEmptyPostcode(address))))
  }

  def cacheContactAddressDetails(address: ContactAddressModel)(implicit request: Request[_]): Future[Unit] = {
    def noneForEmptyPostcode(a: ContactAddressModel) = a.copy(postcode = a.postcode.filter(_.nonEmpty))

    saveSubscriptionDetails(sd => sd.copy(contactAddress = Some(noneForEmptyPostcode(address))))
  }

  def cacheNameIdDetails(
    nameIdOrganisationMatchModel: NameIdOrganisationMatchModel
  )(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(nameIdOrganisationDetails = Some(nameIdOrganisationMatchModel)))

  def cacheNameAndCustomsId(name: String, customsId: CustomsId)(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(
      sd =>
        sd.copy(
          nameIdOrganisationDetails = Some(NameIdOrganisationMatchModel(name, customsId.id)),
          customsId = Some(customsId)
        )
    )

  def cachedNameIdDetails(implicit request: Request[_]): Future[Option[NameIdOrganisationMatchModel]] =
    sessionCache.subscriptionDetails map (_.nameIdOrganisationDetails)

  def cacheNameDetails(
    nameOrganisationMatchModel: NameOrganisationMatchModel
  )(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(nameOrganisationDetails = Some(nameOrganisationMatchModel)))

  def cachedNameDetails(implicit request: Request[_]): Future[Option[NameOrganisationMatchModel]] =
    sessionCache.subscriptionDetails map (_.nameOrganisationDetails)

  def cachedUtrMatch(implicit request: Request[_]): Future[Option[UtrMatchModel]] =
    sessionCache.subscriptionDetails map (_.formData.utrMatch)

  def cachedNinoMatch(implicit request: Request[_]): Future[Option[NinoMatchModel]] =
    sessionCache.subscriptionDetails map (_.formData.ninoMatch)

  def cachedOrganisationType(implicit request: Request[_]): Future[Option[CdsOrganisationType]] =
    sessionCache.subscriptionDetails map (_.formData.organisationType)

  def cacheEoriNumber(eoriNumber: String)(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(eoriNumber = Some(eoriNumber)))

  def cachedEoriNumber(implicit request: Request[_]): Future[Option[String]] =
    sessionCache.subscriptionDetails map (_.eoriNumber)

  def cacheDateEstablished(date: LocalDate)(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(dateEstablished = Some(date)))

  def cacheNameDobDetails(nameDob: NameDobMatchModel)(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(nameDobDetails = Some(nameDob)))

  def cacheCustomsId(subscriptionCustomsId: CustomsId)(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(customsId = Some(subscriptionCustomsId)))

  def cacheNinoOrUtrChoice(ninoOrUtrChoice: NinoOrUtrChoice)(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(
      sd => sd.copy(formData = sd.formData.copy(ninoOrUtrChoice = ninoOrUtrChoice.ninoOrUtrRadio))
    )

  def cacheNinoMatchForNoAnswer(ninoMatch: Option[NinoMatchModel])(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(formData = sd.formData.copy(ninoMatch = ninoMatch), customsId = None))

  def cacheUtrMatchForNoAnswer(utrMatch: Option[UtrMatchModel])(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(
      sd =>
        sd.copy(formData = sd.formData.copy(utrMatch = utrMatch), customsId = None, nameIdOrganisationDetails = None)
    )

  def cacheUtrMatch(utrMatch: Option[UtrMatchModel])(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(formData = sd.formData.copy(utrMatch = utrMatch)))

  def cacheNinoMatch(ninoMatch: Option[NinoMatchModel])(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(formData = sd.formData.copy(ninoMatch = ninoMatch)))

  private def contactDetails(view: ContactDetailsModel, isInReviewMode: Boolean)(implicit
    request: Request[_]
  ): Future[ContactDetailsModel] =
    if (!isInReviewMode && view.useAddressFromRegistrationDetails)
      sessionCache.registrationDetails map { registrationDetails =>
        contactDetailsAdaptor.toContactDetailsModelWithRegistrationAddress(view, registrationDetails.address)
      }
    else Future.successful(view)

  def cachedCustomsId(implicit request: Request[_]): Future[Option[CustomsId]] =
    sessionCache.subscriptionDetails map (_.customsId)

  def cacheExistingEoriNumber(eori: ExistingEori)(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(existingEoriNumber = Some(eori)))

  def cachedExistingEoriNumber(implicit request: Request[_]): Future[Option[ExistingEori]] =
    sessionCache.subscriptionDetails map (_.existingEoriNumber)

  def cacheRegisteredCountry(country: CompanyRegisteredCountry)(implicit request: Request[_]): Future[Unit] =
    saveSubscriptionDetails(sd => sd.copy(registeredCompany = Some(country)))

  def cachedRegisteredCountry()(implicit request: Request[_]): Future[Option[CompanyRegisteredCountry]] =
    sessionCache.subscriptionDetails.map(_.registeredCompany)

}
