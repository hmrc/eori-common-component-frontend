package unit.util

import org.scalatest.{MustMatchers, WordSpec}
import uk.gov.hmrc.eoricommoncomponent.frontend.util.{InvalidUrlValueException, Require}

class RequireSpec extends WordSpec with MustMatchers {

  "Require requireThatUrlValue" should {

    "throw InvalidUrlValueException when requirement not met" in {

      val caught = intercept[InvalidUrlValueException] {
        Require.requireThatUrlValue(1 == 3, "Some Error")
      }
      caught.getMessage mustBe "invalid value: Some Error"

    }
  }
}
