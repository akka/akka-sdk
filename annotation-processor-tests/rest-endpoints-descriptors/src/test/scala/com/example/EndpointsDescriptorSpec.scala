/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EndpointsDescriptorSpec extends AnyWordSpec with Matchers {

  "kalix-components.conf" should {
    "contain endpoints components" in {
      val config = ConfigFactory.load("META-INF/kalix-components.conf")

      val actionComponents = config.getStringList("kalix.jvm.sdk.components.endpoint")
      actionComponents.size() shouldBe 2
      actionComponents should contain("com.example.HelloController")
      actionComponents should contain("com.example.UserRegistryController")
    }
  }
}
