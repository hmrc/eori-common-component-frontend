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

package uk.gov.hmrc.eoricommoncomponent.frontend.filters

import org.apache.pekko.stream.Materializer
import play.api.mvc._
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.eoricommoncomponent.frontend.config.AppConfig

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AllowlistFilter @Inject() (
  appConfig: AppConfig,
  sessionCookieBaker: SessionCookieBaker,
  cookieHeaderEncoding: CookieHeaderEncoding
)(implicit val mat: Materializer, ec: ExecutionContext)
    extends Filter {

  override def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] = {
    val permittedReferer = rh.headers
      .get(HeaderNames.REFERER)
      .exists(referer => appConfig.allowlistReferrers.exists(allowed => referer.contains(allowed)))

    if (permittedReferer) {
      val allowlistedSession: Session = rh.session + ("allowlisted" -> "true")
      val cookies: Seq[Cookie]        = (rh.cookies ++ Seq(sessionCookieBaker.encodeAsCookie(allowlistedSession))).toSeq
      val headers                     = rh.headers.add(HeaderNames.COOKIE -> cookieHeaderEncoding.encodeCookieHeader(cookies))
      next(rh.withHeaders(headers)) // Ensures the allowlisted param is added to the remainder of THIS request
        .map(
          _.addingToSession("allowlisted" -> "true")(rh)
        ) // Ensures the allowlisted param is added to FUTURE requests (via the Set-Cookie header)
    } else
      next(rh)
  }

}
