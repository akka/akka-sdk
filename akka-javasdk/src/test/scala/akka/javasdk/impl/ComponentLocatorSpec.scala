/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import scala.jdk.CollectionConverters._

import akka.javasdk.impl.ComponentLocator._
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object ComponentLocatorSpec {
  private def componentPath(componentType: String): String = s"$DescriptorComponentBasePath.$componentType"
}

class ComponentLocatorSpec extends AnyWordSpec with Matchers {
  import ComponentLocatorSpec._

  "ComponentLocator.mergeDescriptorConfigs" should {

    "return empty config when given empty sequence" in {
      val result = ComponentLocator.mergeDescriptorConfigs(Seq.empty)
      result.isEmpty shouldBe true
    }

    "return single config as-is when given one config" in {
      val config = ConfigFactory.parseString("""
          |akka.javasdk {
          |  components {
          |    agent = ["com.example.MyAgent"]
          |    consumer = ["com.example.MyConsumer"]
          |  }
          |  service-setup = "com.example.Setup"
          |}
          |""".stripMargin)

      val result = ComponentLocator.mergeDescriptorConfigs(Seq(config))

      result.getStringList(componentPath(AgentKey)).asScala shouldBe Seq("com.example.MyAgent")
      result.getStringList(componentPath(ConsumerKey)).asScala shouldBe Seq("com.example.MyConsumer")
      result.getString(DescriptorServiceSetupEntryPath) shouldBe "com.example.Setup"
    }

    "merge component arrays from multiple configs" in {
      val config1 = ConfigFactory.parseString("""
          |akka.javasdk {
          |  components {
          |    agent = ["com.example.Agent1", "com.example.Agent2"]
          |    consumer = ["com.example.Consumer1"]
          |  }
          |}
          |""".stripMargin)

      val config2 = ConfigFactory.parseString("""
          |akka.javasdk {
          |  components {
          |    agent = ["com.library.LibAgent"]
          |    view = ["com.library.LibView"]
          |  }
          |}
          |""".stripMargin)

      val result = ComponentLocator.mergeDescriptorConfigs(Seq(config1, config2))

      result.getStringList(componentPath(AgentKey)).asScala shouldBe Seq(
        "com.example.Agent1",
        "com.example.Agent2",
        "com.library.LibAgent")
      result.getStringList(componentPath(ConsumerKey)).asScala shouldBe Seq("com.example.Consumer1")
      result.getStringList(componentPath(ViewKey)).asScala shouldBe Seq("com.library.LibView")
    }

    "take service-setup from config that has it" in {
      val config1 = ConfigFactory.parseString("""
          |akka.javasdk {
          |  components {
          |    agent = ["com.example.Agent1"]
          |  }
          |  service-setup = "com.example.MySetup"
          |}
          |""".stripMargin)

      val config2 = ConfigFactory.parseString("""
          |akka.javasdk {
          |  components {
          |    consumer = ["com.library.Consumer"]
          |  }
          |}
          |""".stripMargin)

      val result = ComponentLocator.mergeDescriptorConfigs(Seq(config1, config2))

      result.getString(DescriptorServiceSetupEntryPath) shouldBe "com.example.MySetup"
    }

    "merge all component types" in {
      val config1 = ConfigFactory.parseString("""
          |akka.javasdk.components {
          |  agent = ["Agent1"]
          |  consumer = ["Consumer1"]
          |  event-sourced-entity = ["ES1"]
          |  http-endpoint = ["Http1"]
          |  grpc-endpoint = ["Grpc1"]
          |}
          |""".stripMargin)

      val config2 = ConfigFactory.parseString("""
          |akka.javasdk.components {
          |  mcp-endpoint = ["Mcp1"]
          |  key-value-entity = ["KV1"]
          |  timed-action = ["TA1"]
          |  view = ["View1"]
          |  workflow = ["Workflow1"]
          |}
          |""".stripMargin)

      val result = ComponentLocator.mergeDescriptorConfigs(Seq(config1, config2))

      result.getStringList(componentPath(AgentKey)).asScala shouldBe Seq("Agent1")
      result.getStringList(componentPath(ConsumerKey)).asScala shouldBe Seq("Consumer1")
      result.getStringList(componentPath(EventSourcedEntityKey)).asScala shouldBe Seq("ES1")
      result.getStringList(componentPath(HttpEndpointKey)).asScala shouldBe Seq("Http1")
      result.getStringList(componentPath(GrpcEndpointKey)).asScala shouldBe Seq("Grpc1")
      result.getStringList(componentPath(McpEndpointKey)).asScala shouldBe Seq("Mcp1")
      result.getStringList(componentPath(KeyValueEntityKey)).asScala shouldBe Seq("KV1")
      result.getStringList(componentPath(TimedActionKey)).asScala shouldBe Seq("TA1")
      result.getStringList(componentPath(ViewKey)).asScala shouldBe Seq("View1")
      result.getStringList(componentPath(WorkflowKey)).asScala shouldBe Seq("Workflow1")
    }

    "handle configs with no components gracefully" in {
      val config1 = ConfigFactory.parseString("""
          |akka.javasdk {
          |  components {
          |    agent = ["com.example.Agent1"]
          |  }
          |}
          |""".stripMargin)

      val config2 = ConfigFactory.empty()

      val result = ComponentLocator.mergeDescriptorConfigs(Seq(config1, config2))

      result.getStringList(componentPath(AgentKey)).asScala shouldBe Seq("com.example.Agent1")
    }

    "throw exception when duplicate component is found across configs" in {
      val config1 = ConfigFactory.parseString("""
          |akka.javasdk.components {
          |  agent = ["com.example.DuplicateAgent", "com.example.Agent2"]
          |}
          |""".stripMargin)

      val config2 = ConfigFactory.parseString("""
          |akka.javasdk.components {
          |  agent = ["com.example.DuplicateAgent"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.mergeDescriptorConfigs(Seq(config1, config2))
      }

      exception.getMessage should include("Duplicate component(s) found")
      exception.getMessage should include("com.example.DuplicateAgent")
    }

    "throw exception listing all duplicates when multiple duplicates found" in {
      val config1 = ConfigFactory.parseString("""
          |akka.javasdk.components {
          |  consumer = ["com.example.Consumer1", "com.example.Consumer2"]
          |}
          |""".stripMargin)

      val config2 = ConfigFactory.parseString("""
          |akka.javasdk.components {
          |  consumer = ["com.example.Consumer1", "com.example.Consumer2", "com.example.Consumer3"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.mergeDescriptorConfigs(Seq(config1, config2))
      }

      exception.getMessage should include("Duplicate component(s) found")
      exception.getMessage should include("com.example.Consumer1")
      exception.getMessage should include("com.example.Consumer2")
    }

    "throw exception when multiple configs define service-setup" in {
      val config1 = ConfigFactory.parseString("""
          |akka.javasdk {
          |  components {
          |    agent = ["com.example.Agent1"]
          |  }
          |  service-setup = "com.example.Setup1"
          |}
          |""".stripMargin)

      val config2 = ConfigFactory.parseString("""
          |akka.javasdk {
          |  components {
          |    consumer = ["com.library.Consumer"]
          |  }
          |  service-setup = "com.library.Setup2"
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.mergeDescriptorConfigs(Seq(config1, config2))
      }

      exception.getMessage should include("Multiple service-setup classes found")
      exception.getMessage should include("com.example.Setup1")
      exception.getMessage should include("com.library.Setup2")
    }

    "allow configs without service-setup" in {
      val config1 = ConfigFactory.parseString("""
          |akka.javasdk.components {
          |  agent = ["com.example.Agent1"]
          |}
          |""".stripMargin)

      val config2 = ConfigFactory.parseString("""
          |akka.javasdk.components {
          |  consumer = ["com.library.Consumer"]
          |}
          |""".stripMargin)

      val result = ComponentLocator.mergeDescriptorConfigs(Seq(config1, config2))

      result.hasPath(DescriptorServiceSetupEntryPath) shouldBe false
    }
  }

  "ComponentLocator.isComponentDescriptor" should {

    "match artifact-specific descriptor filenames" in {
      ComponentLocator.isComponentDescriptor("akka-javasdk-components_com.example_my-service.conf") shouldBe true
      ComponentLocator.isComponentDescriptor("akka-javasdk-components_io.akka_api.conf") shouldBe true
      ComponentLocator.isComponentDescriptor("akka-javasdk-components_org.company_lib.conf") shouldBe true
    }

    "not match legacy descriptor filename" in {
      // Legacy format is handled separately, not by isComponentDescriptor
      ComponentLocator.isComponentDescriptor("akka-javasdk-components.conf") shouldBe false
    }

    "not match unrelated files" in {
      ComponentLocator.isComponentDescriptor("some-other-file.conf") shouldBe false
      ComponentLocator.isComponentDescriptor("akka-javasdk.conf") shouldBe false
      ComponentLocator.isComponentDescriptor("akka-javasdk-components_foo.json") shouldBe false
      ComponentLocator.isComponentDescriptor("MANIFEST.MF") shouldBe false
    }
  }
}
