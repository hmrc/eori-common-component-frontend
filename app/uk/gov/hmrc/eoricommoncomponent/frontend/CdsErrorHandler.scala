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

package uk.gov.hmrc.eoricommoncomponent.frontend

import scala.concurrent.Future

class CdsErrorHandler @Inject() (
  val messagesApi: MessagesApi,
  val configuration: Configuration,
  errorTemplateView: error_template,
  clientErrorTemplateView: client_error_template,
  notFoundView: notFound
) extends FrontendErrorHandler {

  private val logger = Logger(this.getClass)

  override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(implicit
    request: Request[_]
  ): Html = throw new IllegalStateException("This method should not be used any more.")

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {

    // $COVERAGE-OFF$Loggers
    logger.error(s"Error with status code: $statusCode and message: $message")
    // $COVERAGE-ON
    implicit val req: Request[_] = Request(request, "")

    statusCode match {
      case NOT_FOUND                                              => Future.successful(Results.NotFound(notFoundView()))
      case BAD_REQUEST if message == Constants.INVALID_PATH_PARAM => Future.successful(Results.NotFound(notFoundView()))
      case FORBIDDEN if message == Constants.NO_CSRF_FOUND_IN_BODY =>
        Future.successful(Redirect(SecuritySignOutController.displayPage(service)))
      case _ => Future.successful(Results.InternalServerError(clientErrorTemplateView(message)))
    }
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {

    implicit val req: Request[_] = Request(request, "")

    exception match {
      case sessionTimeOut: SessionTimeOutException =>
        // $COVERAGE-OFF$Loggers
        logger.warn("SessionTimeout with message - " + sessionTimeOut.errorMessage)
        // $COVERAGE-ON
        Future.successful(Redirect(SecuritySignOutController.displayPage(service)).withNewSession)
      case invalidRequirement: InvalidUrlValueException =>
        // $COVERAGE-OFF$Loggers
        logger.warn(invalidRequirement.message)
        // $COVERAGE-ON
        Future.successful(Results.NotFound(notFoundView()))
      case dataUnavailableException: DataUnavailableException =>
        // $COVERAGE-OFF$Loggers
        logger.warn("DataUnavailableException with message - " + dataUnavailableException.message)
        // $COVERAGE-ON
        Future.successful(Results.InternalServerError(errorTemplateView()))
      case _ =>
        // $COVERAGE-OFF$Loggers
        logger.error("Internal server error: " + exception.getMessage, exception)
        // $COVERAGE-ON
        Future.successful(Results.InternalServerError(errorTemplateView()))
    }
  }

}
