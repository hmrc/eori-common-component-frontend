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

package uk.gov.hmrc.eoricommoncomponent.frontend.forms

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation._
import play.api.i18n.Messages
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.eoricommoncomponent.frontend.DateConverter
import uk.gov.hmrc.eoricommoncomponent.frontend.domain._
import uk.gov.hmrc.eoricommoncomponent.frontend.domain.registration.UserLocation
import uk.gov.hmrc.eoricommoncomponent.frontend.forms.FormUtils.{mandatoryDateTodayOrBefore, _}

object MatchingForms {

  val Length35          = 35
  val Length34          = 34
  private val nameRegex = "[a-zA-Z0-9-' ]*"

  private def validUtrFormat(utr: Option[String]): Boolean = {

    val ZERO  = 0
    val ONE   = 1
    val TWO   = 2
    val THREE = 3
    val FOUR  = 4
    val FIVE  = 5
    val SIX   = 6
    val SEVEN = 7
    val EIGHT = 8
    val NINE  = 9
    val TEN   = 10

    def isValidUtr(remainder: Int, checkDigit: Int): Boolean = {
      val mapOfRemainders = Map(
        ZERO  -> TWO,
        ONE   -> ONE,
        TWO   -> NINE,
        THREE -> EIGHT,
        FOUR  -> SEVEN,
        FIVE  -> SIX,
        SIX   -> FIVE,
        SEVEN -> FOUR,
        EIGHT -> THREE,
        NINE  -> TWO,
        TEN   -> ONE
      )
      mapOfRemainders.get(remainder).contains(checkDigit)
    }

    utr match {
      case Some(u) =>
        val utrWithoutK = u.trim.stripSuffix("K").stripSuffix("k")
        utrWithoutK.length == TEN && utrWithoutK.forall(_.isDigit) && {
          val actualUtr   = utrWithoutK.toList
          val checkDigit  = actualUtr.head.asDigit
          val restOfUtr   = actualUtr.tail
          val weights     = List(SIX, SEVEN, EIGHT, NINE, TEN, FIVE, FOUR, THREE, TWO)
          val weightedUtr = for ((w1, u1) <- weights zip restOfUtr) yield w1 * u1.asDigit
          val total       = weightedUtr.sum
          val remainder   = total % 11
          isValidUtr(remainder, checkDigit)
        }
      case None => false
    }
  }

  val organisationTypeDetailsForm: Form[CdsOrganisationType] = Form(
    "organisation-type" -> optional(text)
      .verifying(
        "cds.matching.organisation-type.page-error.organisation-type-field.error.required",
        x => x.fold(false)(oneOf(CdsOrganisationType.validOrganisationTypes.keySet).apply(_))
      )
      .transform[CdsOrganisationType](
        o =>
          CdsOrganisationType(
            CdsOrganisationType
              .forId(
                o.getOrElse(throw new IllegalArgumentException("Could not create CdsOrganisationType for empty ID."))
              )
              .id
          ),
        x => Some(x.id)
      )
  )

  val userLocationForm: Form[UserLocationDetails] = Form(
    "location" -> optional(text)
      .verifying(
        "cds.registration.user-location.error.location",
        x => x.fold(false)(oneOf(UserLocation.validLocations).apply(_))
      )
      .transform[UserLocationDetails](o => UserLocationDetails(o), x => x.location)
  )

  private val validYesNoAnswerOptions = Set("true", "false")

  def eoriSignoutYesNoForm()(implicit messages: Messages): Form[YesNo] =
    Form(
      mapping(
        "yes-no-answer" -> optional(
          text.verifying(messages("ecc.unable-to-use.signout.empty"), oneOf(validYesNoAnswerOptions))
        )
          .verifying(messages("ecc.unable-to-use.signout.empty"), _.isDefined)
          .transform[Boolean](str => str.get.toBoolean, bool => Option(String.valueOf(bool)))
      )(YesNo.apply)(YesNo.unapply)
    )

  private def validBusinessName: Constraint[String] =
    Constraint({
      case s if s.isEmpty      => Invalid(ValidationError("cds.matching-error.business-details.business-name.isEmpty"))
      case s if s.length > 105 => Invalid(ValidationError("cds.matching-error.business-details.business-name.too-long"))
      case _                   => Valid
    })

  private def validPartnershipName: Constraint[String] =
    Constraint({
      case s if s.isEmpty => Invalid(ValidationError("cds.matching-error.business-details.partnership-name.isEmpty"))
      case s if s.length > 105 =>
        Invalid(ValidationError("cds.matching-error.business-details.partnership-name.too-long"))
      case _ => Valid
    })

  private def validCompanyName: Constraint[String] =
    Constraint({
      case s if s.isEmpty      => Invalid(ValidationError("cds.matching-error.business-details.company-name.isEmpty"))
      case s if s.length > 105 => Invalid(ValidationError("cds.matching-error.business-details.company-name.too-long"))
      case _                   => Valid
    })

  private def validUtr: Constraint[String] = {

    def validLength: String => Boolean = s => s.length == 10 || (s.endsWith("k") || s.endsWith("K") && s.length == 11)

    Constraint({
      case s if formatInput(s).isEmpty                => Invalid(ValidationError("cds.matching-error.business-details.utr.isEmpty"))
      case s if !validLength(formatInput(s))          => Invalid(ValidationError("cds.matching-error.utr.length"))
      case s if !validUtrFormat(Some(formatInput(s))) => Invalid(ValidationError("cds.matching-error.utr.invalid"))
      case _                                          => Valid
    })
  }

  val nameUtrOrganisationForm: Form[NameIdOrganisationMatchModel] = Form(
    mapping("name" -> text.verifying(validBusinessName), "utr" -> text.verifying(validUtr))(
      NameIdOrganisationMatchModel.apply
    )(NameIdOrganisationMatchModel.unapply)
  )

  val nameUtrPartnershipForm: Form[NameIdOrganisationMatchModel] = Form(
    mapping("name" -> text.verifying(validPartnershipName), "utr" -> text.verifying(validUtr))(
      NameIdOrganisationMatchModel.apply
    )(NameIdOrganisationMatchModel.unapply)
  )

  val nameUtrCompanyForm: Form[NameIdOrganisationMatchModel] = Form(
    mapping("name" -> text.verifying(validCompanyName), "utr" -> text.verifying(validUtr))(
      NameIdOrganisationMatchModel.apply
    )(NameIdOrganisationMatchModel.unapply)
  )

  val nameOrganisationForm: Form[NameOrganisationMatchModel] = Form(
    mapping("name" -> text.verifying(validBusinessName))(NameOrganisationMatchModel.apply)(
      NameOrganisationMatchModel.unapply
    )
  )

  val enterNameDobForm: Form[NameDobMatchModel] = Form(
    mapping(
      "first-name"  -> text.verifying(validFirstName),
      "middle-name" -> optional(text.verifying(validMiddleName)),
      "last-name"   -> text.verifying(validLastName),
      "date-of-birth" -> mandatoryDateTodayOrBefore(
        onEmptyError = "dob.error.empty-date",
        onInvalidDateError = "dob.error.invalid-date",
        onDateInFutureError = "dob.error.future-date",
        minYear = DateConverter.earliestYearDateOfBirth
      )
    )(NameDobMatchModel.apply)(NameDobMatchModel.unapply)
  )

  val enterNameDobFormRow: Form[NameDobMatchModel] = Form(
    mapping(
      "first-name"  -> text.verifying(validGivenName),
      "middle-name" -> optional(text.verifying(validMiddleName)),
      "last-name"   -> text.verifying(validFamilyName),
      "date-of-birth" -> mandatoryDateTodayOrBefore(
        onEmptyError = "dob.error.empty-date",
        onInvalidDateError = "dob.error.invalid-date",
        onDateInFutureError = "dob.error.future-date",
        minYear = DateConverter.earliestYearDateOfBirth
      )
    )(NameDobMatchModel.apply)(NameDobMatchModel.unapply)
  )

  private def validFirstName: Constraint[String] =
    Constraint("constraints.first-name")({
      case s if s.isEmpty => Invalid(ValidationError("cds.subscription.first-name.error.empty"))
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.first-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.first-name.error.too-long"))
      case _                  => Valid
    })

  private def validGivenName: Constraint[String] =
    Constraint("constraints.first-name")({
      case s if s.isEmpty => Invalid(ValidationError("cds.subscription.given-name.error.empty"))
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.given-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.given-name.error.too-long"))
      case _                  => Valid
    })

  private def validMiddleName: Constraint[String] =
    Constraint("constraints.first-name")({
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.middle-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.middle-name.error.too-long"))
      case _                  => Valid
    })

  private def validLastName: Constraint[String] =
    Constraint("constraints.last-name")({
      case s if s.isEmpty => Invalid(ValidationError("cds.subscription.last-name.error.empty"))
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.last-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.last-name.error.too-long"))
      case _                  => Valid
    })

  private def validFamilyName: Constraint[String] =
    Constraint("constraints.last-name")({
      case s if s.isEmpty => Invalid(ValidationError("cds.subscription.family-name.error.empty"))
      case s if !s.matches(nameRegex) =>
        Invalid(ValidationError("cds.subscription.family-name.error.wrong-format"))
      case s if s.length > 35 => Invalid(ValidationError("cds.subscription.family-name.error.too-long"))
      case _                  => Valid
    })

  private def validNino: Constraint[String] =
    Constraint({
      case s if formatInput(s).isEmpty                  => Invalid(ValidationError("cds.subscription.nino.error.empty"))
      case s if formatInput(s).length != 9              => Invalid(ValidationError("cds.subscription.nino.error.wrong-length"))
      case s if !formatInput(s).matches("[a-zA-Z0-9]*") => Invalid(ValidationError("cds.matching.nino.invalidFormat"))
      case s if !Nino.isValid(formatInput(s))           => Invalid(ValidationError("cds.matching.nino.invalidNino"))
      case _                                            => Valid
    })

  val subscriptionNinoForm: Form[IdMatchModel] = Form(
    mapping("nino" -> text.verifying(validNino))(IdMatchModel.apply)(IdMatchModel.unapply)
  )

  val subscriptionUtrForm: Form[IdMatchModel] = Form(
    mapping("utr" -> text.verifying(validUtr))(IdMatchModel.apply)(IdMatchModel.unapply)
  )

  val ninoOrUtrChoiceForm: Form[NinoOrUtrChoice] = Form(
    mapping(
      "ninoOrUtrRadio" -> optional(text)
        .verifying("cds.subscription.nino.utr.invalid", _.fold(false)(x => x.trim.nonEmpty))
    )(NinoOrUtrChoice.apply)(NinoOrUtrChoice.unapply)
  )

  def validHaveUtr: Constraint[Option[Boolean]] =
    Constraint({
      case None => Invalid(ValidationError("cds.matching.organisation-utr.field-error.have-utr"))
      case _    => Valid
    })

  val haveUtrForm: Form[UtrMatchModel] = Form(
    mapping("have-utr" -> optional(boolean).verifying(validHaveUtr))(UtrMatchModel.apply)(model => Some(model.haveUtr))
  )

  def validHaveNino: Constraint[Option[Boolean]] =
    Constraint({
      case None => Invalid(ValidationError("cds.matching.nino.row.yes-no.error"))
      case _    => Valid
    })

  val haveRowIndividualsNinoForm: Form[NinoMatchModel] = Form(
    mapping("have-nino" -> optional(boolean).verifying(validHaveNino))(NinoMatchModel.apply)(
      model => Some(model.haveNino)
    )
  )

}
