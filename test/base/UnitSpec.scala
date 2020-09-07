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

package base

import java.nio.charset.Charset

import akka.stream.Materializer
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}
import play.api.mvc.Result
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}

trait UnitSpec extends WordSpec with Matchers {

  // From github.com.hmrc/hmrctest to have a possibility to remove deprecated hmrctest library
  // TODO Add deprecated, use play.test.Helpers method contentAsString
  def bodyOf(result: Result)(implicit mat: Materializer): String = {
    val bodyBytes: ByteString = await(result.body.consumeData)
    // We use the default charset to preserve the behaviour of a previous
    // version of this code, which used new String(Array[Byte]).
    // If the fact that the previous version used the default charset was an
    // accident then it may be better to decode in UTF-8 or the charset
    // specified by the result's headers.
    bodyBytes.decodeString(Charset.defaultCharset().name)
  }

  // From github.com.hmrc/hmrctest to have a possibility to remove deprecated hmrctest library
  // TODO Add deprecated, use play.test.Helpers method contentAsString
  def bodyOf(resultF: Future[Result])(implicit mat: Materializer): Future[String] = {
    resultF.map(bodyOf)(global)
  }

  // Convenience to avoid having to wrap andThen() parameters in Future.successful
  // From github.com.hmrc/hmrctest to have a possibility to remove deprecated hmrctest library
  // TODO Not sure if we want to keep this, for tests it's probably okay, but I prefer to explicit show the type
  // With this approach we need to manually check what specific method return
  implicit def liftFuture[A](v: A): Future[A] = Future.successful(v)

  // From github.com.hmrc/hmrctest to have a possibility to remove deprecated hmrctest library
  implicit val defaultTimeout: FiniteDuration = 5 seconds

  // From github.com.hmrc/hmrctest to have a possibility to remove deprecated hmrctest library
  // TODO Similar like above, prefer explicit extraction, most of the play.api.test.Helpers._ methods works with Future and are more efficient
  implicit def extractAwait[A](future: Future[A]): A = await[A](future)

  // From github.com.hmrc/hmrctest to have a possibility to remove deprecated hmrctest library
  // TODO use await from play.api.test.Helpers or futureValue from ScalaFutures trait
  def await[A](future: Future[A])(implicit timeout: Duration): A = Await.result(future, timeout)

  // From github.com.hmrc/hmrctest to have a possibility to remove deprecated hmrctest library
  // TODO Use status from play.api.test.Helpers on the Future
  def status(of: Result): Int = of.header.status

  // From github.com.hmrc/hmrctest to have a possibility to remove deprecated hmrctest library
  // TODO Use status from play.api.test.Helpers
  def status(of: Future[Result])(implicit timeout: Duration): Int = play.api.test.Helpers.status(of)
}
