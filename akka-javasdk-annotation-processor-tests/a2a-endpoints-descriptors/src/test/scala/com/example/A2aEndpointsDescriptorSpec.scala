/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class A2aEndpointsDescriptorSpec extends AnyWordSpec with Matchers {
  "akka-javasdk-components.conf" should {
    "list the A2A endpoints" in {
      val config = ConfigFactory.load("META-INF/akka-javasdk-components_com.example_test.conf")

      val a2aEndpoints = config.getStringList("akka.javasdk.components.a2a-endpoint")
      a2aEndpoints.size() shouldBe 1
      a2aEndpoints should contain("com.example.BasicA2aEndpoint")
    }
  }
}
