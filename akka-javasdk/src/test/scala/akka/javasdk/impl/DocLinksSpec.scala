/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DocLinksSpec extends AnyWordSpec with Matchers {

  "DocLinks" should {

    "specific error codes should be mapped to sdk specific urls" in {
      DocLinks.forErrorCode("AK-00112") shouldBe defined
      DocLinks.forErrorCode("AK-00402") shouldBe defined
      DocLinks.forErrorCode("AK-00415") shouldBe defined
      DocLinks.forErrorCode("AK-00406") shouldBe defined
    }

    "fallback to general codes when no code matches" in {
      DocLinks.forErrorCode("AK-00100") shouldBe defined
      DocLinks.forErrorCode("AK-00200") shouldBe defined
    }

  }
}
