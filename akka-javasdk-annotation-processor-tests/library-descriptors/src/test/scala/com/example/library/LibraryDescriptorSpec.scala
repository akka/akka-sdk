/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.library

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LibraryDescriptorSpec extends AnyWordSpec with Matchers {

  "akka-javasdk-components.conf of a library" should {
    "list the components without the service marker" in {
      val config = ConfigFactory.load("META-INF/akka-javasdk-components_com.example_test.conf")

      config.getStringList(
        "akka.javasdk.components.timed-action") should contain only "com.example.library.LibraryAction"
      config.hasPath("akka.javasdk.service") shouldBe false
    }
  }
}
