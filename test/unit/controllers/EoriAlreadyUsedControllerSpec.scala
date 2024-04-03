package unit.controllers

import play.api.http.Status.OK
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.eoricommoncomponent.frontend.controllers.EoriAlreadyUsedController
import uk.gov.hmrc.eoricommoncomponent.frontend.views.html.eori_already_used
import util.ControllerSpec
import util.builders.AuthBuilder.withAuthorisedUser
import util.builders.{AuthActionMock, SessionBuilder}

class EoriAlreadyUsedControllerSpec extends ControllerSpec with AuthActionMock {
  private val mockAuthConnector = mock[AuthConnector]

  private val eoriAlreadyUsedView = instanceOf[eori_already_used]

  private val mockAuthAction   = authAction(mockAuthConnector)

  private val controller =
    new EoriAlreadyUsedController(
      mockAuthAction,
      mcc,
      eoriAlreadyUsedView
    )


  "EoriAlreadyUsedController" should {

    "return OK (200) and display eori already used page" in {
      withAuthorisedUser(defaultUserId, mockAuthConnector)

      val result =
        controller.displayPage(atarService).apply(SessionBuilder.buildRequestWithSession(defaultUserId))

      status(result) shouldBe OK

    }

  }

}