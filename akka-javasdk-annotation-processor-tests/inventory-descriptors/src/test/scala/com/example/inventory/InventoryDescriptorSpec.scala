/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.inventory

import java.nio.charset.StandardCharsets.UTF_8

import scala.util.Using

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InventoryDescriptorSpec extends AnyWordSpec with Matchers {

  private def readResource(name: String): String = {
    val in = getClass.getClassLoader.getResourceAsStream(name)
    require(in != null, s"Resource not found: $name")
    Using.resource(in) { in => new String(in.readAllBytes(), UTF_8) }
  }

  "the inventory of the fixture project" should {
    "list every component with its fields" in {
      readResource("META-INF/akka-javasdk-inventory_com.example_test.json") shouldBe readResource(
        "expected-inventory.json")
    }
  }
}
