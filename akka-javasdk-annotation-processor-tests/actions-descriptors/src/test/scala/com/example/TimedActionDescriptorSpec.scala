/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TimedActionDescriptorSpec extends AnyWordSpec with Matchers {

  "akka-javasdk-components.conf" should {
    "have correct configuration" in {
      val config = ConfigFactory.load("META-INF/akka-javasdk-components.conf")

      val actionComponents = config.getStringList("akka.javasdk.components.timed-action")
      actionComponents.size() shouldBe 2
      actionComponents should contain("com.example.HelloAction")
      actionComponents should contain("com.example.SomeTimedAction")

      val kalixService = config.getString("akka.javasdk.service-setup")
      kalixService should be("com.example.Setup")
    }
  }
}
