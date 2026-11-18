/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor

import java.nio.file.Files
import java.nio.file.Path

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InventoryClasspathSpec extends AnyWordSpec with Matchers {

  private val OptionName = "akka.javasdk.inventory.classpath"

  "InventoryClasspath" should {

    "read the entries in order" in {
      val jar = Files.createTempFile("inventory-classpath", ".jar")

      InventoryClasspath.parse(OptionName, s"[/project/target/classes, $jar]") shouldBe java.util.List
        .of(Path.of("/project/target/classes"), jar)
    }

    "read a path with a space" in {
      InventoryClasspath.parse(OptionName, "[/my project/target/classes]") shouldBe java.util.List
        .of(Path.of("/my project/target/classes"))
    }

    "read an empty list" in {
      InventoryClasspath.parse(OptionName, "[]") shouldBe java.util.List.of()
    }

    "reject a value that is not a list" in {
      val exception = intercept[InventoryException](InventoryClasspath.parse(OptionName, "/project/target/classes"))

      exception.getMessage should include(OptionName)
    }

    "reject a jar that does not exist" in {
      val exception = intercept[InventoryException](InventoryClasspath.parse(OptionName, "[/missing/library.jar]"))

      exception.getMessage should include("/missing/library.jar")
    }
  }
}
