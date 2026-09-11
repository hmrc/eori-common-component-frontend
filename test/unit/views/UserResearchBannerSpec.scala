/*
 * Copyright 2026 HM Revenue & Customs
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

package unit.views

import com.google.inject.name.Names
import org.apache.pekko.util.Timeout
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.i18n.Lang.defaultLang
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.Helpers.contentAsString
import play.twirl.api.HtmlFormat
import uk.gov.hmrc.auth.core.AffinityGroup.Organisation
import uk.gov.hmrc.eoricommoncomponent.frontend.config.{InternalAuthTokenInitialiser, NoOpInternalAuthTokenInitialiser}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.migration.{migration_success, we_need_to_make_checks}
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.subscription.*
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.{eori_enrol_success, you_cant_use_service}
import util.{CSRFTest, TestData}

import scala.concurrent.duration.*

/** Covers every page that opts in to the user research banner, so that removing the feature means deleting this one
  * spec rather than unpicking assertions from each journey's spec.
  */
class UserResearchBannerSpec extends PlaySpec with CSRFTest with TestData {

  private def appWith(bannerEnabled: Boolean): Application =
    new GuiceApplicationBuilder()
      .configure(
        "create-internal-auth-token-on-start" -> false,
        "features.user-research-banner"       -> bannerEnabled
      )
      .overrides {
        bind[InternalAuthTokenInitialiser].to[NoOpInternalAuthTokenInitialiser]
        bind[String].qualifiedWith(Names.named("appName")).toInstance("eori-common-component-frontend")
      }
      .build()

  private lazy val bannerOn: Application  = appWith(bannerEnabled = true)
  private lazy val bannerOff: Application = appWith(bannerEnabled = false)

  implicit val request: Request[AnyContentAsEmpty.type] = withFakeCSRF(fakeAtarSubscribeRequest)
  implicit val timeout: Timeout                         = 30.seconds

  private def messagesIn(app: Application, lang: Lang): Messages =
    MessagesImpl(lang, app.injector.instanceOf[MessagesApi])

  /** Every page that passes displayUserResearchBanner = true to the layout. */
  private def optedInPages(app: Application)(implicit messages: Messages): Seq[(String, HtmlFormat.Appendable)] = {
    val injector = app.injector
    Seq(
      "we_need_to_make_checks" -> injector.instanceOf[we_need_to_make_checks].apply("test@mail.com", atarService),
      "migration_success"      -> injector.instanceOf[migration_success].apply("", "", atarService, false),
      "eori_enrol_success"     -> injector.instanceOf[eori_enrol_success].apply(atarService),
      "subscription_outcome_fail_company" -> injector.instanceOf[subscription_outcome_fail_company].apply(atarService),
      "subscription_outcome_fail_partnership" ->
        injector.instanceOf[subscription_outcome_fail_partnership].apply(atarService),
      "subscription_outcome_fail_llp" -> injector.instanceOf[subscription_outcome_fail_llp].apply(atarService),
      "subscription_outcome_fail_organisation" ->
        injector.instanceOf[subscription_outcome_fail_organisation].apply(atarService),
      "subscription_outcome_fail_solo_and_individual" ->
        injector.instanceOf[subscription_outcome_fail_solo_and_individual].apply(atarService),
      "subscription_outcome_fail_row_utr_organisation" ->
        injector.instanceOf[subscription_outcome_fail_row_utr_organisation].apply(atarService),
      "subscription_outcome_fail_row" ->
        injector.instanceOf[subscription_outcome_fail_row].apply(atarService, isOrganisation = true),
      "subscription_outcome_fail_eu_eori" -> injector.instanceOf[subscription_outcome_fail_eu_eori].apply(cdsService)
    )
  }

  private def banner(page: HtmlFormat.Appendable): org.jsoup.select.Elements =
    Jsoup.parse(contentAsString(page)).body.getElementsByClass("hmrc-user-research-banner")

  "The user research banner" should {

    "be displayed on every page that opts in" in {
      implicit val messages: Messages = messagesIn(bannerOn, defaultLang)

      optedInPages(bannerOn).foreach { case (name, page) =>
        withClue(s"$name: ") {
          val urb = banner(page)

          urb.size mustBe 1
          urb.select(".hmrc-user-research-banner__link").attr("href") mustBe "https://banner-en"
          urb.select(".hmrc-user-research-banner__close").isEmpty mustBe true
        }
      }
    }

    "be displayed in Welsh when the language is Welsh" in {
      implicit val messages: Messages = messagesIn(bannerOn, Lang("cy"))

      optedInPages(bannerOn).foreach { case (name, page) =>
        withClue(s"$name: ") {
          val link = banner(page).select(".hmrc-user-research-banner__link")

          link.attr("href") mustBe "https://banner-cy"
          link.text mustBe "Ymunwch â’n panel ymchwil (yn agor tab newydd)"
        }
      }
    }

    "not be displayed on any opted-in page when the feature is turned off" in {
      implicit val messages: Messages = messagesIn(bannerOff, defaultLang)

      optedInPages(bannerOff).foreach { case (name, page) =>
        withClue(s"$name: ")(banner(page) mustBe empty)
      }
    }

    "not be displayed on a page that does not opt in" in {
      implicit val messages: Messages = messagesIn(bannerOn, defaultLang)

      val page: Document =
        Jsoup.parse(contentAsString(bannerOn.injector.instanceOf[you_cant_use_service].apply(
          Some(Organisation),
          atarService
        )))

      page.body.getElementsByClass("hmrc-user-research-banner") mustBe empty
    }
  }

}
