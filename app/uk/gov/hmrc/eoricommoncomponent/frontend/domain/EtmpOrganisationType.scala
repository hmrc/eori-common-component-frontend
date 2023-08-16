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

package uk.gov.hmrc.eoricommoncomponent.frontend.domain

import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.CdsOrganisationType._
import uk.gov.hmrc.eoricommoncomponent.frontend.services.mapping.EtmpLegalStatus

sealed trait EtmpOrganisationType {
  def etmpOrgTypeCode: String
}

case object Partnership extends EtmpOrganisationType {
  override def etmpOrgTypeCode: String = "0001"

  override def toString: String = "Partnership"
}

case object LLP extends EtmpOrganisationType {
  override def etmpOrgTypeCode: String = "0002"

  override def toString: String = "LLP"
}

case object CorporateBody extends EtmpOrganisationType {
  override def etmpOrgTypeCode: String = "0003"

  override def toString: String = "Corporate Body"
}

case object UnincorporatedBody extends EtmpOrganisationType {
  override def etmpOrgTypeCode: String = "0004"

  override def toString: String = "Unincorporated Body"
}

case object NA extends EtmpOrganisationType {
  override def etmpOrgTypeCode: String = "N/A"

  override def toString: String = "N/A"
}

object EtmpOrganisationType extends Logging {

  private val cdsToEtmpOrgType = Map(
    CompanyId                       -> CorporateBody,
    PartnershipId                   -> Partnership,
    LimitedLiabilityPartnershipId   -> LLP,
    CharityPublicBodyNotForProfitId -> UnincorporatedBody,
    EUOrganisationId                -> CorporateBody,
    ThirdCountryOrganisationId      -> CorporateBody
  )

  def apply(cdsOrgType: CdsOrganisationType): EtmpOrganisationType = cdsToEtmpOrgType.getOrElse(cdsOrgType.id, NA)

  def apply(id: String): EtmpOrganisationType = id match {
    case EtmpLegalStatus.Partnership        => Partnership
    case EtmpLegalStatus.Llp                => LLP
    case EtmpLegalStatus.CorporateBody      => CorporateBody
    case EtmpLegalStatus.UnincorporatedBody => UnincorporatedBody
    case invalidId =>
      val error =
        s"""I got an $invalidId as an ETMP Organisation Type but I wanted one of "Partnership", "LLP", "Corporate Body", "Unincorporated Body""""
      // $COVERAGE-OFF$Loggers
      logger.warn(error)
      // $COVERAGE-ON
      throw new IllegalArgumentException(error)
  }

  private def unapply(id: EtmpOrganisationType): String = id match {
    case Partnership        => EtmpLegalStatus.Partnership
    case LLP                => EtmpLegalStatus.Llp
    case CorporateBody      => EtmpLegalStatus.CorporateBody
    case UnincorporatedBody => EtmpLegalStatus.UnincorporatedBody
    case _                  => "N/A"
  }

  def orgTypeToEtmpOrgCode(id: String): String = apply(id).etmpOrgTypeCode

  implicit val etmpOrgReads: Reads[EtmpOrganisationType] = new Reads[EtmpOrganisationType] {
    def reads(value: JsValue): JsResult[EtmpOrganisationType] = JsSuccess(apply((value \ "id").as[String]))
  }

  implicit val etmpOrgWrites: Writes[EtmpOrganisationType] = new Writes[EtmpOrganisationType] {
    def writes(org: EtmpOrganisationType): JsValue = Json.toJson(CdsOrganisationType(unapply(org)))
  }

}
