/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EntitiesAndViewsDescriptorSpec extends AnyWordSpec with Matchers {

  "akka-javasdk-components.conf" should {
    "have correct configuration" in {
      val config = ConfigFactory.load("META-INF/akka-javasdk-components.conf")

      val keyValueComponents = config.getStringList("akka.javasdk.components.key-value-entity")
      keyValueComponents.size() shouldBe 1
      keyValueComponents should contain("com.example.SimpleKeyValueEntity")

      val eventSourcedComponents = config.getStringList("akka.javasdk.components.event-sourced-entity")
      eventSourcedComponents.size() shouldBe 1
      eventSourcedComponents should contain("com.example.SimpleEventSourcedEntity")

      val viewComponents = config.getStringList("akka.javasdk.components.view")
      viewComponents.size() shouldBe 3
      viewComponents should contain("com.example.SimpleView")
      viewComponents should contain("com.example.MultiView")
      viewComponents should contain("com.example.MultiView2")

      val kalixService = config.getString("akka.javasdk.service-setup")
      kalixService should be("com.example.Setup")
    }
  }
}
