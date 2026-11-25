/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.jdk.CollectionConverters._

import akka.runtime.sdk.spi.RegionInfo
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ContextsSpec extends AnyWordSpec with Matchers {

  "AbstractContext" should {

    "read the downed regions on each call" in {
      var downed = Set("b")
      val context = new AbstractContext(new RegionInfo("a", () => downed)) {}

      context.selfRegion() shouldBe "a"
      context.downedRegions().asScala shouldBe Set("b")

      downed = Set("b", "c")
      context.downedRegions().asScala shouldBe Set("b", "c")

      downed = Set.empty
      context.downedRegions() shouldBe empty
    }

    "give a set that cannot be modified" in {
      val context = new AbstractContext(new RegionInfo("a", () => Set("b"))) {}

      an[UnsupportedOperationException] should be thrownBy context.downedRegions().add("x")
    }
  }
}
