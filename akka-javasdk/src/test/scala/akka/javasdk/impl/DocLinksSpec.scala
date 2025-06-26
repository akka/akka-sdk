/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DocLinksSpec extends AnyWordSpec with Matchers {

  "DocLinks" should {
    "specific error codes should be mapped to sdk specific urls" in {

      def shouldBeExplicitlyDefined(code: String) = {
        withClue(s"checking $code :") {
          DocLinks.errorCodes.get(code) shouldBe defined
        }
      }

      shouldBeExplicitlyDefined("AK-00112")
      shouldBeExplicitlyDefined("AK-00406")
      shouldBeExplicitlyDefined("AK-00415")
      shouldBeExplicitlyDefined("AK-00416")
      shouldBeExplicitlyDefined("AK-01206")
    }

    "fallback to general codes when no code matches" in {
      DocLinks.forErrorCode("AK-00100") shouldBe defined
      DocLinks.forErrorCode("AK-00200") shouldBe defined
      DocLinks.forErrorCode("AK-00300") shouldBe defined
      DocLinks.forErrorCode("AK-00400") shouldBe defined
      DocLinks.forErrorCode("AK-00700") shouldBe defined
      DocLinks.forErrorCode("AK-00800") shouldBe defined
      DocLinks.forErrorCode("AK-00900") shouldBe defined
      DocLinks.forErrorCode("AK-01000") shouldBe defined
      DocLinks.forErrorCode("AK-01200") shouldBe defined
    }

  }
}
