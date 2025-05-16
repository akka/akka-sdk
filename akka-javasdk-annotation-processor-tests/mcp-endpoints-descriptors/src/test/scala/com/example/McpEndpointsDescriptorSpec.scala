/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class McpEndpointsDescriptorSpec extends AnyWordSpec with Matchers {
  "akka-javasdk-components.conf" should {
    "have correct configuration" in {
      val config = ConfigFactory.load("META-INF/akka-javasdk-components.conf")

      val keyValueComponents = config.getStringList("akka.javasdk.components.mcp-endpoint")
      keyValueComponents.size() shouldBe 1
      keyValueComponents should contain("com.example.BasicMcpEndpoint")
    }
  }
}
