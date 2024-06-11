/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ActionDescriptorSpec extends AnyWordSpec with Matchers {

  "kalix-components.conf" should {
    "have correct configuration" in {
      val config = ConfigFactory.load("META-INF/kalix-components.conf")

      val actionComponents = config.getStringList("kalix.jvm.sdk.components.action")
      actionComponents.size() shouldBe 2
      actionComponents should contain("com.example.HelloAction")
      actionComponents should contain("com.example.ActionSubscriber")

      val kalixService = config.getString("kalix.jvm.sdk.kalix-service")
      kalixService should be("com.example.Main")
    }
  }
}
