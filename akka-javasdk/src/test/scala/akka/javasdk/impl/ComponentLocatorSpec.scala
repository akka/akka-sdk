/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.net.JarURLConnection

import scala.jdk.CollectionConverters._

import akka.actor.ReflectiveDynamicAccess
import akka.javasdk.impl.ComponentLocator._
import akka.javasdk.testmodels.EndpointsTestModels.GetHelloEndpoint
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EmployeeEntity
import akka.javasdk.testmodels.keyvalueentity.Counter
import akka.javasdk.testmodels.view.ViewTestModels.UserByEmailWithGet
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

  "ComponentLocator.findAllDescriptorFiles" should {

    // Regression test for a shared-JarFile-cache bug: getJarFile() on a JarURLConnection
    // returns the JVM-wide cached instance (JarFileFactory) when useCaches is true (the
    // default), so it's shared by every reader of that jar in the process - including
    // another caller that already holds a reference to it, e.g. akka.util.ManifestInfo,
    // which reads every classpath jar's manifest on each ActorSystem start. A naive
    // `Using(jarConnection.getJarFile) { ... }` closes that shared instance, breaking
    // anyone else still holding it - reproduced deterministically below without relying
    // on thread-scheduling luck: grab a JarFile handle up front (standing in for a
    // concurrent holder), run the scan, then confirm the handle obtained beforehand is
    // still usable afterwards.
    "not break a JarFile handle obtained by another caller before the scan runs" in {
      val classLoader = getClass.getClassLoader
      val jarMetaInfUrl = classLoader.getResources("META-INF/").asScala.find(_.getProtocol == "jar").getOrElse {
        fail("test classpath has no jar-protocol META-INF resource to exercise the cache with")
      }

      // Stand-in for a concurrent reader (e.g. ManifestInfo) that already obtained the
      // cached JarFile before ComponentLocator's scan runs.
      val priorHandle = jarMetaInfUrl.openConnection().asInstanceOf[JarURLConnection].getJarFile

      ComponentLocator.findAllDescriptorFiles(classLoader)

      // Must not throw java.lang.IllegalStateException: zip file closed.
      priorHandle.entries().asScala.size should be > 0
    }
  }

  "ComponentLocator.loadLibraryComponents" should {
    val dynamicAccess = new ReflectiveDynamicAccess(getClass.getClassLoader)

    "return nothing when config has no library components" in {
      ComponentLocator.loadLibraryComponents(ConfigFactory.empty(), dynamicAccess, Set.empty) shouldBe empty
      val emptyBlock = ConfigFactory.parseString("akka.javasdk.library.components {}")
      ComponentLocator.loadLibraryComponents(emptyBlock, dynamicAccess, Set.empty) shouldBe empty
      val referenceDefaults = ConfigFactory.defaultReference()
      ComponentLocator.loadLibraryComponents(referenceDefaults, dynamicAccess, Set.empty) shouldBe empty
    }

    "load listed classes" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  key-value-entity = ["${classOf[Counter].getName}"]
          |  event-sourced-entity = ["${classOf[EmployeeEntity].getName}"]
          |  view = ["${classOf[UserByEmailWithGet].getName}"]
          |  http-endpoint = ["${classOf[GetHelloEndpoint].getName}"]
          |}
          |""".stripMargin)

      val result = ComponentLocator.loadLibraryComponents(config, dynamicAccess, Set.empty)

      result should contain theSameElementsAs Seq(
        classOf[EmployeeEntity],
        classOf[Counter],
        classOf[UserByEmailWithGet],
        classOf[GetHelloEndpoint])
    }

    "skip classes already located through descriptors" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  key-value-entity = ["${classOf[Counter].getName}"]
          |  event-sourced-entity = ["${classOf[EmployeeEntity].getName}"]
          |}
          |""".stripMargin)

      val result = ComponentLocator.loadLibraryComponents(config, dynamicAccess, Set(classOf[EmployeeEntity]))

      result shouldBe Seq(classOf[Counter])
    }

    "fail on class listed more than once" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  key-value-entity = ["${classOf[Counter].getName}"]
          |  view = ["${classOf[Counter].getName}"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(config, dynamicAccess, Set.empty)
      }
      exception.getMessage should include(s"Duplicate library component(s) [${classOf[Counter].getName}]")
    }

    "fail on unknown component type" in {
      val config = ConfigFactory.parseString("""
          |akka.javasdk.library.components {
          |  entity = ["com.example.SomeEntity"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(config, dynamicAccess, Set.empty)
      }
      exception.getMessage should include("Unknown component type(s) [entity]")
      exception.getMessage should include(LibraryComponentsConfigPath)
    }

    "fail on class that cannot be loaded" in {
      val config = ConfigFactory.parseString("""
          |akka.javasdk.library.components {
          |  view = ["com.example.MissingView"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(config, dynamicAccess, Set.empty)
      }
      exception.getMessage should include("Could not load library component class [com.example.MissingView]")
      exception.getMessage should include(s"$LibraryComponentsConfigPath.view")
    }

    "fail on class listed under the wrong component type" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  event-sourced-entity = ["${classOf[Counter].getName}"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(config, dynamicAccess, Set.empty)
      }
      exception.getMessage should include(s"Library component class [${classOf[Counter].getName}]")
      exception.getMessage should include(
        "is not a event-sourced-entity component, expected a class extending akka.javasdk.eventsourcedentity.EventSourcedEntity")
    }

    "fail on class listed as view that does not extend View" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  view = ["${classOf[Counter].getName}"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(config, dynamicAccess, Set.empty)
      }
      exception.getMessage should include("is not a view component, expected a class extending akka.javasdk.view.View")
    }

    "fail on class listed as endpoint without the endpoint annotation" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  grpc-endpoint = ["${classOf[GetHelloEndpoint].getName}"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(config, dynamicAccess, Set.empty)
      }
      exception.getMessage should include(
        "is not a grpc-endpoint component, expected a class annotated with @GrpcEndpoint")
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
