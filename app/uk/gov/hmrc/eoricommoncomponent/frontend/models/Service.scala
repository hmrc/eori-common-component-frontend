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

package uk.gov.hmrc.eoricommoncomponent.frontend.models

import play.api.mvc.{PathBindable, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.util.Constants

sealed trait Service {
  val code: String
  val enrolmentKey: String

}

object Service {

  case object ATaR extends Service {
    override val code: String         = "atar"
    override val enrolmentKey: String = "HMRC-ATAR-ORG"
  }

  // This is for CDS enrolment checks, we're not supporting CDS enrolment now
  case object CDS extends Service {
    override val code: String         = "cds"
    override val enrolmentKey: String = "HMRC-CUS-ORG"
  }

  /**
    * Used to provide a 'service' parameter for controllers that are not currently in scope
    */
  case object NullService extends Service {
    override val code: String         = "null-service"
    override val enrolmentKey: String = ""
  }

  private val supportedServices = Set[Service](ATaR, CDS)

  def withName(str: String): Option[Service] =
    supportedServices.find(_.code == str)

  implicit def binder(implicit stringBinder: PathBindable[String]): PathBindable[Service] = new PathBindable[Service] {

    override def bind(key: String, value: String): Either[String, Service] =
      for {
        name    <- stringBinder.bind(key, value).right
        service <- Service.withName(name).toRight(Constants.INVALID_PATH_PARAM).right
      } yield service

    override def unbind(key: String, value: Service): String = stringBinder.unbind(key, value.code)
  }

  def serviceFromRequest(implicit request: Request[_]): Option[Service] = {
    val path = request.path
    supportedServices.find(service => path.contains(s"/${service.code}/"))
  }

}
