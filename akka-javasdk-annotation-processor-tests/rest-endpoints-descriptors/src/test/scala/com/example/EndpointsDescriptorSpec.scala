/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EndpointsDescriptorSpec extends AnyWordSpec with Matchers {

  "akka-javasdk-components.conf" should {
    "contain http endpoints components" in {
      val config = ConfigFactory.load("META-INF/akka-javasdk-components.conf")

      val endpointComponents = config.getStringList("akka.javasdk.components.http-endpoint")
      endpointComponents.size() shouldBe 2
      endpointComponents should contain("com.example.HelloController")
      endpointComponents should contain("com.example.UserRegistryController")
    }
  }
}
