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

package uk.gov.hmrc.eoricommoncomponent.frontend.config

import javax.inject.{Inject, Named, Singleton}
import play.api.Configuration
import play.api.i18n.Messages
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.Duration

@Singleton
class AppConfig @Inject() (
  config: Configuration,
  servicesConfig: ServicesConfig,
  @Named("appName") val appName: String
) {

  val messageFiles: Seq[String] = config.get[Seq[String]]("messages.file.names")

  val ttl: Duration = Duration.create(config.get[String]("cds-frontend-cache.ttl"))

  val allowlistReferrers: Seq[String] = {
    val configValue        = config.get[String]("allowlist-referrers")
    val substrings         = configValue.split(',').map(_.trim)
    val nonEmptySubstrings = substrings.filter(_.nonEmpty)
    nonEmptySubstrings.toIndexedSeq
  }

  val contactBaseUrl = servicesConfig.baseUrl("contact-frontend")

  private val serviceIdentifierRegister =
    config.get[String]("microservice.services.contact-frontend.serviceIdentifierRegister")

  val serviceIdentifierSubscribe =
    config.get[String]("microservice.services.contact-frontend.serviceIdentifierSubscribe")

  private val feedbackLinkSubscribe = config.get[String]("external-url.feedback-survey-subscribe")

  lazy val checkEoriNumberUrlPath: String = servicesConfig.getConfString("check-eori-number.context", "")
  lazy val checkEoriNumberUrl: String     = s"${servicesConfig.baseUrl("check-eori-number")}/${checkEoriNumberUrlPath}"

  def feedbackUrl(service: Service) = s"$feedbackLinkSubscribe-${service.code}"

  private val eoriCommonComponentRegistrationFrontendBaseUrl: String =
    config.get[String]("external-url.eori-common-component-registration-frontend.url")

  def eoriCommonComponentRegistrationFrontend(serviceName: String): String =
    eoriCommonComponentRegistrationFrontendBaseUrl + serviceName + "/register"

  private def languageKey(implicit messages: Messages) = messages.lang.language match {
    case "cy" => "cy"
    case _    => "en"
  }

  def findLostUtr()(implicit messages: Messages): String =
    config.get[String](s"external-url.find-lost-utr-$languageKey")

  val traderSupportService: String                       = config.get[String]("external-url.trader-support-service")
  val getCompanyInformation: String                      = config.get[String]("external-url.get-company-information")
  val contactEORITeam: String                            = config.get[String]("external-url.contact-eori-team")
  val checkEORINumber: String                            = config.get[String]("external-url.check-eori-number")
  val companyHouseRegister: String                       = config.get[String]("external-url.company-house-register")
  val askUtrCopy: String                                 = config.get[String]("external-url.ask-utr-copy")
  val addTeamMembers: String                             = config.get[String]("external-url.add-team-members")
  val changeToBusiness: String                           = config.get[String]("external-url.change-to-business")
  val changeOfDetails: String                            = config.get[String]("external-url.change-of-details")
  val userResearchBannerUrl: String                      = config.get[String]("external-url.user-research-bannerUrl")
  def callCharges()(implicit messages: Messages): String = config.get[String](s"external-url.call-charges-$languageKey")

  //get help link feedback for Subscribe journey
  def reportAProblemPartialUrlSubscribe(service: Service): String =
    s"$contactBaseUrl/contact/problem_reports_ajax?service=$serviceIdentifierSubscribe-${service.code}"

  def reportAProblemNonJSUrlSubscribe(service: Service): String =
    s"$contactBaseUrl/contact/problem_reports_nonjs?service=$serviceIdentifierSubscribe-${service.code}"

  //email verification service
  val emailVerificationEnabled: Boolean          = config.get[Boolean]("microservice.services.email-verification.enabled")
  val emailVerificationContinueUrlPrefix: String = config.get[String]("external-url.email-verification.continue-url")

  val emailVerificationBaseUrl: String = servicesConfig.baseUrl("email-verification")

  val emailVerificationFrontendBaseUrl: String =
    config.get[String]("microservice.services.email-verification-frontend.prefix")

  val emailVerificationServiceContext: String =
    config.get[String]("microservice.services.email-verification.context")

  val emailVerificationTemplateId: String =
    config.get[String]("microservice.services.email-verification.templateId")

  val emailVerificationLinkExpiryDuration: String =
    config.get[String]("microservice.services.email-verification.linkExpiryDuration")

  //handle subscription service
  val handleSubscriptionBaseUrl: String = servicesConfig.baseUrl("handle-subscription")

  val handleSubscriptionServiceContext: String =
    config.get[String]("microservice.services.handle-subscription.context")

  // tax enrolments
  val taxEnrolmentsBaseUrl: String = servicesConfig.baseUrl("tax-enrolments")

  val taxEnrolmentsServiceContext: String = config.get[String]("microservice.services.tax-enrolments.context")

  val enrolmentStoreProxyBaseUrl: String = servicesConfig.baseUrl("enrolment-store-proxy")

  val enrolmentStoreProxyServiceContext: String =
    config.get[String]("microservice.services.enrolment-store-proxy.context")

  def getServiceUrl(proxyServiceName: String): String = {
    val baseUrl = servicesConfig.baseUrl("eori-common-component-hods-proxy")
    val serviceContext =
      config.get[String](s"microservice.services.eori-common-component-hods-proxy.$proxyServiceName.context")
    s"$baseUrl/$serviceContext"
  }

  private val addressLookupBaseUrl: String = servicesConfig.baseUrl("address-lookup")
  private val addressLookupContext: String = config.get[String]("microservice.services.address-lookup.context")

  val addressLookup: String = addressLookupBaseUrl + addressLookupContext
  lazy val contactAddress   = config.getOptional[Boolean]("features.contact-address").getOrElse(false)

  val internalAuthToken: String = config.get[String]("internal-auth.token")
}
