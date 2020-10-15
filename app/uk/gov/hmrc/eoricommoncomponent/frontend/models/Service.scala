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

import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.mvc.{PathBindable, Request}
import uk.gov.hmrc.eoricommoncomponent.frontend.util.Constants

case class Service(
  code: String,
  enrolmentKey: String,
  shortName: String,
  callBack: String,
  friendlyName: String,
  friendlyNameWelsh: String
)

object Service {

  val cds = Service("cds", "HMRC-CUS-ORG", "", "", "", "")
  // TODO Get rid of empty Service, throw exception in places where it's used
  val empty = Service("", "", "", "", "", "")

  private val configuration = Configuration(ConfigFactory.load())

  private val supportedServices: Seq[Service] = {
    val listOfTheServices = configuration.get[String]("services-config.list").split(",").map(_.trim).toList

    listOfTheServices.map { service =>
      Service(
        code = configuration.get[String](s"services-config.$service.name"),
        enrolmentKey = configuration.get[String](s"services-config.$service.enrolment"),
        shortName = configuration.get[String](s"services-config.$service.shortName"),
        callBack = configuration.get[String](s"services-config.$service.callBack"),
        friendlyName = configuration.get[String](s"services-config.$service.friendlyName").replace("_", " "),
        friendlyNameWelsh = configuration.get[String](s"services-config.$service.friendlyNameWelsh").replace("_", " ")
      )
    }
  }

  private val supportedServicesMap: Map[String, Service] = {
    if (isServicesConfigCorrect(supportedServices))
      supportedServices.map(service => service.code -> service).toMap
    else throw new Exception("Services config contains duplicate service")
  }

  private def isServicesConfigCorrect(services: Seq[Service]): Boolean =
    services.map(_.code).distinct.size == services.size

  def withName(str: String): Option[Service] = supportedServicesMap.get(str)

  implicit def binder(implicit stringBinder: PathBindable[String]): PathBindable[Service] = new PathBindable[Service] {

    override def bind(key: String, value: String): Either[String, Service] =
      for {
        name    <- stringBinder.bind(key, value).right
        service <- Service.withName(name).toRight(Constants.INVALID_PATH_PARAM).right
      } yield service

    override def unbind(key: String, value: Service): String = stringBinder.unbind(key, value.code)
  }

  def serviceFromRequest(implicit request: Request[_]): Option[Service] = {
    val path       = request.path
    val serviceKey = supportedServicesMap.keys.find(serviceKey => path.contains(s"/$serviceKey"))

    serviceKey.map(supportedServicesMap(_))
  }

}
