/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Paths

import com.example.inventory.InventoryCompiler.DefaultOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InventoryProcessorSpec extends AnyWordSpec with Matchers {

  private def expected(name: String): String =
    new String(Files.readAllBytes(Paths.get("src/test/resources/expected").resolve(name)), UTF_8)

  "InventoryProcessor" should {

    "write the inventory of a service" in {
      val result = InventoryCompiler.compile("basic")
      withClue(result.diagnostics) { result.success shouldBe true }
      new String(result.inventory("com.example", "shop").get, UTF_8) shouldBe expected("basic.json")
    }

    "mark a component library" in {
      val result = InventoryCompiler.compile("basic", DefaultOptions :+ "-Aakka.javasdk.componentLibrary=true")
      new String(result.inventory("com.example", "shop").get, UTF_8) should include("\"componentLibrary\": true,")
    }

    "write the same bytes for the same sources" in {
      val first = InventoryCompiler.compile("basic").inventory("com.example", "shop").get
      val second = InventoryCompiler.compile("basic").inventory("com.example", "shop").get
      second shouldBe first
    }

    "replace an earlier inventory" in {
      val first = InventoryCompiler.compile("basic")
      val second = InventoryCompiler.compile("basic-without-agent", outputDir = Some(first.outputDir))
      val json = new String(second.inventory("com.example", "shop").get, UTF_8)
      json shouldNot include("com.example.shop.SupportAgent")
      json should include("com.example.shop.OrderEntity")
    }

    "write no inventory when validation fails" in {
      val result = InventoryCompiler.compile("invalid-component")
      result.success shouldBe false
      result.inventory("com.example", "shop") shouldBe None
    }

    "write no inventory when the descriptor processor reports an error" in {
      val result = InventoryCompiler.compile("two-setups")
      result.success shouldBe false
      result.diagnostics.map(_.getMessage(null)).mkString should include("More than one class annotated with @Setup")
      result.inventory("com.example", "shop") shouldBe None
    }

    "report an error when the artifact id is missing" in {
      val result = InventoryCompiler.compile(
        "basic",
        options = Seq("-Aakka.javasdk.groupId=com.example"),
        processors = "akka.javasdk.tooling.processor.InventoryProcessor")
      result.success shouldBe false
      result.diagnostics.map(_.getMessage(null)).mkString should include("akka.javasdk.artifactId")
    }

    "claim the annotations" in {
      val result =
        InventoryCompiler.compile("supported-annotations-only", DefaultOptions ++ Seq("-Xlint:processing", "-Werror"))
      withClue(result.diagnostics) { result.success shouldBe true }
      result.inventory("com.example", "shop") shouldBe defined
      new String(result.inventory("com.example", "shop").get, UTF_8) shouldNot include("com.example.shop.ShopSetup")
    }
  }
}
