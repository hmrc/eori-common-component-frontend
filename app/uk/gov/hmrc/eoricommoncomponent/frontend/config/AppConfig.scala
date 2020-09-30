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

package uk.gov.hmrc.eoricommoncomponent.frontend.config

import javax.inject.{Inject, Named, Singleton}
import play.api.Configuration
import uk.gov.hmrc.eoricommoncomponent.frontend.models.Service
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.duration.Duration
import scala.util.matching.Regex

@Singleton
class AppConfig @Inject() (
  config: Configuration,
  servicesConfig: ServicesConfig,
  runMode: RunMode,
  @Named("appName") val appName: String
) {

  lazy val env: String = runMode.env

  lazy val messageFiles: Seq[String] = config.get[Seq[String]]("messages.file.names")

  lazy val ttl: Duration = Duration.create(config.get[String]("cds-frontend-cache.ttl"))

  lazy val allowlistReferrers: Seq[String] =
    config.get[String]("allowlist-referrers").split(',').map(_.trim).filter(_.nonEmpty)

  private lazy val contactBaseUrl = servicesConfig.baseUrl("contact-frontend")

  private lazy val serviceIdentifierRegister =
    config.get[String]("microservice.services.contact-frontend.serviceIdentifierRegister")

  private lazy val serviceIdentifierSubscribe =
    config.get[String]("microservice.services.contact-frontend.serviceIdentifierSubscribe")

  def serviceReturnUrl(service: Service) = config.get[String](s"external-url.service-return.${service.code}")

  lazy val feedbackLink          = config.get[String]("external-url.feedback-survey")
  lazy val feedbackLinkSubscribe = config.get[String]("external-url.feedback-survey-subscribe")

  lazy val externalGetEORILink = config.get[String]("external-url.get-cds-eori")

  lazy val blockedRoutesRegex: Seq[Regex] = config.get[String]("routes-to-block").split(',').map(_.r).toSeq

  //get help link feedback for Register journey
  def reportAProblemPartialUrlRegister(service: Service): String =
    s"$contactBaseUrl/contact/problem_reports_ajax?service=$serviceIdentifierRegister-${service.code}"

  def reportAProblemNonJSUrlRegister(service: Service): String =
    s"$contactBaseUrl/contact/problem_reports_nonjs?service=$serviceIdentifierRegister-${service.code}"

  //get help link feedback for Subscribe journey
  def reportAProblemPartialUrlSubscribe(service: Service): String =
    s"$contactBaseUrl/contact/problem_reports_ajax?service=$serviceIdentifierSubscribe-${service.code}"

  def reportAProblemNonJSUrlSubscribe(service: Service): String =
    s"$contactBaseUrl/contact/problem_reports_nonjs?service=$serviceIdentifierSubscribe-${service.code}"

  private lazy val betafeedbackBaseUrl = s"${contactBaseUrl}/contact/beta-feedback"

  def betaFeedBackRegister(service: Service) =
    s"${betafeedbackBaseUrl}?service=${serviceIdentifierRegister}-${service.code}"

  def betaFeedBackSubscribe(service: Service) =
    s"${betafeedbackBaseUrl}?service=${serviceIdentifierSubscribe}-${service.code}"

  //email verification service
  lazy val emailVerificationBaseUrl: String = servicesConfig.baseUrl("email-verification")

  lazy val emailVerificationServiceContext: String =
    config.get[String]("microservice.services.email-verification.context")

  lazy val emailVerificationTemplateId: String =
    config.get[String]("microservice.services.email-verification.templateId")

  lazy val emailVerificationLinkExpiryDuration: String =
    config.get[String]("microservice.services.email-verification.linkExpiryDuration")

  //handle subscription service
  lazy val handleSubscriptionBaseUrl: String = servicesConfig.baseUrl("handle-subscription")

  lazy val handleSubscriptionServiceContext: String =
    config.get[String]("microservice.services.handle-subscription.context")

  //pdf generation
  lazy val pdfGeneratorBaseUrl: String = servicesConfig.baseUrl("pdf-generator")
  // tax enrolments
  lazy val taxEnrolmentsBaseUrl: String = servicesConfig.baseUrl("tax-enrolments")

  lazy val taxEnrolmentsServiceContext: String = config.get[String]("microservice.services.tax-enrolments.context")

  lazy val enrolmentStoreProxyBaseUrl: String = servicesConfig.baseUrl("enrolment-store-proxy")

  lazy val enrolmentStoreProxyServiceContext: String =
    config.get[String]("microservice.services.enrolment-store-proxy.context")

  def getServiceUrl(proxyServiceName: String): String = {
    val baseUrl = servicesConfig.baseUrl("eori-common-component-hods-proxy")
    val serviceContext =
      config.get[String](s"microservice.services.eori-common-component-hods-proxy.$proxyServiceName.context")
    s"$baseUrl/$serviceContext"
  }

}
