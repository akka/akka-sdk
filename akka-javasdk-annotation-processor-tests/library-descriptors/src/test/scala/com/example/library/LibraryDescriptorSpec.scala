/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.library

import java.nio.charset.StandardCharsets.UTF_8

import scala.util.Using

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LibraryDescriptorSpec extends AnyWordSpec with Matchers {

  private def readResource(name: String): String = {
    val in = getClass.getClassLoader.getResourceAsStream(name)
    require(in != null, s"Resource not found: $name")
    Using.resource(in) { in => new String(in.readAllBytes(), UTF_8) }
  }

  "akka-javasdk-components.conf of a library" should {
    "list the components without the service marker" in {
      val config = ConfigFactory.load("META-INF/akka-javasdk-components_com.example_test.conf")

      config.getStringList(
        "akka.javasdk.components.timed-action") should contain only "com.example.library.LibraryAction"
      config.hasPath("akka.javasdk.service") shouldBe false
    }
  }

  "the annotation processors of a library" should {
    "write an inventory marked as a library" in {
      val json = readResource("META-INF/akka-javasdk-inventory_com.example_test.json")
      json should include("\"componentLibrary\": true,")
      json should include("\"class\": \"com.example.library.LibraryAction\"")
    }
  }
}
