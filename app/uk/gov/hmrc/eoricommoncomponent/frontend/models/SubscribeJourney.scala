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

package uk.gov.hmrc.eoricommoncomponent.frontend.models

import play.api.mvc.PathBindable
import uk.gov.hmrc.eoricommoncomponent.frontend.util.Constants

case class SubscribeJourney(journeyType: JourneyType)

sealed trait JourneyType
case object AutoEnrolment extends JourneyType
case object LongJourney   extends JourneyType

object SubscribeJourney {

  implicit def binder(implicit stringBinder: PathBindable[String]): PathBindable[SubscribeJourney] =
    new PathBindable[SubscribeJourney] {

      override def bind(key: String, value: String): Either[String, SubscribeJourney] =
        for {
          str <- stringBinder.bind(key, value).right
          journey <- str.toLowerCase match {
            case "autoenrolment" => Right(SubscribeJourney(AutoEnrolment: JourneyType))
            case "longjourney"   => Right(SubscribeJourney(LongJourney: JourneyType))
            case _               => Left(Constants.INVALID_PATH_PARAM)
          }
        } yield journey

      override def unbind(key: String, value: SubscribeJourney): String =
        stringBinder.unbind(key, value.journeyType.toString.toLowerCase)

    }

}
