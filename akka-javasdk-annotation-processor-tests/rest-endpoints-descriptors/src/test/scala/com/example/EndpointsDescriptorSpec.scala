/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EndpointsDescriptorSpec extends AnyWordSpec with Matchers {

  "akka-javasdk-components.conf" should {
    val config = ConfigFactory.load("META-INF/akka-javasdk-components.conf")

    "contain http endpoint components" in {
      val endpointComponents = config.getStringList("akka.javasdk.components.http-endpoint")
      endpointComponents.size() shouldBe 2
      endpointComponents should contain("com.example.HelloController")
      endpointComponents should contain("com.example.UserRegistryController")
    }

    "contain grpc endpoint components" in {
      val endpointComponents = config.getStringList("akka.javasdk.components.grpc-endpoint")
      endpointComponents.size() shouldBe 1
      endpointComponents should contain("com.example.UserGrpcServiceImpl")
    }
  }
}
