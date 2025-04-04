/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms.models.subscription

import play.api.data.Form
import play.api.data.Forms._
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.Mappings.mandatoryString

case class AddressResultsForm(address: String)

object AddressResultsForm {

  def form(allowedAddresses: Seq[String]): Form[AddressResultsForm] = Form(
    mapping("address" -> mandatoryString("ecc.address-lookup.postcode.address.error")(allowedAddresses.contains(_)))(
      AddressResultsForm.apply
    )(AddressResultsForm.unapply)
  )

}
