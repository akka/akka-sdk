/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ActionDescriptorSpec extends AnyWordSpec with Matchers {

  "akka-platform-components.conf" should {
    "have correct configuration" in {
      val config = ConfigFactory.load("META-INF/akka-platform-components.conf")

      val actionComponents = config.getStringList("akka.platform.jvm.sdk.components.action")
      actionComponents.size() shouldBe 2
      actionComponents should contain("com.example.HelloAction")
      actionComponents should contain("com.example.ActionSubscriber")

      val kalixService = config.getString("akka.platform.jvm.sdk.service-setup")
      kalixService should be("com.example.Setup")
    }
  }
}
