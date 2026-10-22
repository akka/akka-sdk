/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

import scala.jdk.CollectionConverters._

import akka.actor.DynamicAccess
import akka.actor.ReflectiveDynamicAccess
import akka.javasdk.agent.SessionMemoryEntity
import akka.javasdk.impl.ComponentLocator._
import akka.javasdk.keyvalueentity.KeyValueEntity
import akka.javasdk.testmodels.EndpointsTestModels.GetHelloEndpoint
import akka.javasdk.testmodels.EndpointsTestModels.PostHelloEndpoint
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EmployeeEntity
import akka.javasdk.testmodels.keyvalueentity.Counter
import akka.javasdk.testmodels.view.ViewTestModels.UserByEmailWithGet
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UnannotatedCounter extends KeyValueEntity[Integer]

object ComponentLocatorSpec {
  private def componentPath(componentType: String): String = s"$DescriptorComponentBasePath.$componentType"

  private def descriptor(location: String, fileName: String, content: String): Descriptor =
    Descriptor(new URI(s"$location/META-INF/$fileName").toURL, ConfigFactory.parseString(content))

  private val serviceDescriptor =
    descriptor(
      "file:/service/target/classes",
      "akka-javasdk-components_com.example_service.conf",
      """akka.javasdk.service = true
        |akka.javasdk.components.agent = ["com.example.ServiceAgent"]""".stripMargin)

  private val testkitDescriptor =
    descriptor(
      "jar:file:/libs/akka-javasdk-testkit.jar!",
      "akka-javasdk-components_akka-javasdk-testkit.conf",
      """akka.javasdk.components.agent = ["akka.javasdk.testkit.eval.JudgeAgent"]""")

  private val libraryDescriptor =
    descriptor(
      "jar:file:/libs/library.jar!",
      "akka-javasdk-components_com.library_library.conf",
      """akka.javasdk.components {
        |  agent = ["com.library.LibAgent"]
        |  http-endpoint = ["com.library.LibEndpoint", "com.library.OtherEndpoint"]
        |}""".stripMargin)
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

  "ComponentLocator.classifyDescriptors" should {

    "sort service, SDK provided and library descriptors" in {
      val classified = ComponentLocator.classifyDescriptors(
        Seq(libraryDescriptor, testkitDescriptor, serviceDescriptor),
        project = None,
        libraryComponentsListed = false)
      classified.service shouldBe Seq(serviceDescriptor)
      classified.sdkProvided shouldBe Seq(testkitDescriptor)
      classified.library shouldBe Seq(libraryDescriptor)
    }

    "treat service descriptors with the same file name, from main and test classes, as the service" in {
      val serviceTestDescriptor =
        descriptor(
          "file:/service/target/test-classes",
          serviceDescriptor.fileName,
          """akka.javasdk.service = true
            |akka.javasdk.components.agent = ["com.example.TestAgent"]""".stripMargin)
      val classified = ComponentLocator.classifyDescriptors(
        Seq(serviceDescriptor, libraryDescriptor, serviceTestDescriptor),
        project = None,
        libraryComponentsListed = false)
      classified.service shouldBe Seq(serviceDescriptor, serviceTestDescriptor)
      classified.library shouldBe Seq(libraryDescriptor)
    }

    "treat an unmarked descriptor with the file name of a marked descriptor as the service" in {
      val staleTestDescriptor =
        descriptor("file:/service/target/test-classes", serviceDescriptor.fileName, "")
      val classified = ComponentLocator.classifyDescriptors(
        Seq(serviceDescriptor, staleTestDescriptor, libraryDescriptor),
        project = None,
        libraryComponentsListed = false)
      classified.service shouldBe Seq(serviceDescriptor, staleTestDescriptor)
      classified.library shouldBe Seq(libraryDescriptor)
    }

    "treat the unmarked descriptor of the project as the service" in {
      val classified = ComponentLocator.classifyDescriptors(
        Seq(libraryDescriptor, testkitDescriptor),
        project = Some(ProjectCoordinates("com.library", "library")),
        libraryComponentsListed = false)
      classified.service shouldBe Seq(libraryDescriptor)
      classified.library shouldBe empty
    }

    "not match a descriptor of another group with the same artifact id as the project" in {
      val sameArtifactId =
        descriptor("jar:file:/libs/other.jar!", "akka-javasdk-components_com.other_library.conf", "")
      val classified = ComponentLocator.classifyDescriptors(
        Seq(libraryDescriptor, sameArtifactId),
        project = Some(ProjectCoordinates("com.other", "library")),
        libraryComponentsListed = false)
      classified.service shouldBe Seq(sameArtifactId)
      classified.library shouldBe Seq(libraryDescriptor)
    }

    "not match the project when a descriptor is marked" in {
      val sameArtifactId =
        descriptor("jar:file:/libs/other.jar!", "akka-javasdk-components_com.other_service.conf", "")
      val classified = ComponentLocator.classifyDescriptors(
        Seq(serviceDescriptor, sameArtifactId),
        project = Some(ProjectCoordinates("com.other", "service")),
        libraryComponentsListed = false)
      classified.service shouldBe Seq(serviceDescriptor)
      classified.library shouldBe Seq(sameArtifactId)
    }

    "fail when there is no service descriptor" in {
      val exception = intercept[IllegalStateException] {
        ComponentLocator.classifyDescriptors(
          Seq(libraryDescriptor, testkitDescriptor),
          project = Some(ProjectCoordinates("com.example", "service")),
          libraryComponentsListed = false)
      }
      exception.getMessage should include("Found no component descriptor for the service")
      exception.getMessage should include(s"only descriptors without the service marker [${libraryDescriptor.url}]")
      exception.getMessage should include("earlier SDK version")
      (exception.getMessage should not).include("`non-akka-service`")
      exception.getMessage should include(s"If this is a test of a component library")
    }

    "advise to recompile when there are no descriptors" in {
      val exception = intercept[IllegalStateException] {
        ComponentLocator.classifyDescriptors(Seq(testkitDescriptor), project = None, libraryComponentsListed = false)
      }
      exception.getMessage should include("Found no component descriptor for the service")
      exception.getMessage should include("Run `mvn clean compile`")
    }

    "allow no service descriptor when library components are listed" in {
      val classified =
        ComponentLocator.classifyDescriptors(
          Seq(libraryDescriptor, testkitDescriptor),
          project = None,
          libraryComponentsListed = true)
      classified.service shouldBe empty
      classified.sdkProvided shouldBe Seq(testkitDescriptor)
      classified.library shouldBe Seq(libraryDescriptor)
    }

    "fail on service descriptors from more than one artifact" in {
      val otherService =
        descriptor(
          "jar:file:/libs/other.jar!",
          "akka-javasdk-components_com.other_other.conf",
          "akka.javasdk.service = true")
      val exception = intercept[IllegalStateException] {
        ComponentLocator.classifyDescriptors(
          Seq(serviceDescriptor, otherService),
          project = None,
          libraryComponentsListed = true)
      }
      exception.getMessage should include("Found service component descriptors from more than one artifact")
      exception.getMessage should include(serviceDescriptor.url.toString)
      exception.getMessage should include(otherService.url.toString)
      exception.getMessage should include("`non-akka-service` set to `true`")
    }
  }

  "ComponentLocator.unlistedLibraryComponentsMessage" should {

    "list library components with their descriptor and the config to load them" in {
      val message = ComponentLocator.unlistedLibraryComponentsMessage(Seq(libraryDescriptor), Set.empty).get
      message should include(s"[${libraryDescriptor.url}]")
      message should include("agent: [com.library.LibAgent]")
      message should include("http-endpoint: [com.library.LibEndpoint, com.library.OtherEndpoint]")
      message should include(s"$LibraryComponentsConfigPath {")
      message should include("""agent = ["com.library.LibAgent"]""")
      message should include("""http-endpoint = ["com.library.LibEndpoint", "com.library.OtherEndpoint"]""")
      message should include("#_using_components_from_libraries")
    }

    "leave out listed components, without the config to load them" in {
      val message = ComponentLocator
        .unlistedLibraryComponentsMessage(
          Seq(libraryDescriptor),
          Set("com.library.LibAgent", "com.library.LibEndpoint"))
        .get
      (message should not).include("LibAgent")
      (message should not).include("com.library.LibEndpoint")
      message should include("http-endpoint: [com.library.OtherEndpoint]")
      message should include(s"not listed in config [$LibraryComponentsConfigPath]")
      (message should not).include(s"$LibraryComponentsConfigPath {")
      message should include("#_using_components_from_libraries")
    }

    "return nothing when all library components are listed or there are none" in {
      ComponentLocator.unlistedLibraryComponentsMessage(Seq.empty, Set.empty) shouldBe None
      ComponentLocator.unlistedLibraryComponentsMessage(
        Seq(libraryDescriptor),
        Set("com.library.LibAgent", "com.library.LibEndpoint", "com.library.OtherEndpoint")) shouldBe None
    }
  }

  "ComponentLocator.loadLibraryComponents" should {
    val dynamicAccess = new ReflectiveDynamicAccess(getClass.getClassLoader)

    "return nothing when config has no library components" in {
      ComponentLocator.loadLibraryComponents(
        listedLibraryComponents(ConfigFactory.empty()),
        dynamicAccess,
        Set.empty) shouldBe empty
      val emptyBlock = ConfigFactory.parseString("akka.javasdk.library.components {}")
      ComponentLocator.loadLibraryComponents(
        listedLibraryComponents(emptyBlock),
        dynamicAccess,
        Set.empty) shouldBe empty
      val referenceDefaults = ConfigFactory.defaultReference()
      ComponentLocator.loadLibraryComponents(
        listedLibraryComponents(referenceDefaults),
        dynamicAccess,
        Set.empty) shouldBe empty
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

      val result = ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)

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

      val result = ComponentLocator.loadLibraryComponents(
        listedLibraryComponents(config),
        dynamicAccess,
        Set(classOf[EmployeeEntity]))

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
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
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
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
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
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
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
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
      }
      exception.getMessage should include(s"Library component class [${classOf[Counter].getName}]")
      exception.getMessage should include(
        "does not match component type [event-sourced-entity], expected a class extending akka.javasdk.eventsourcedentity.EventSourcedEntity")
    }

    "fail on class listed as view that does not extend View" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  view = ["${classOf[Counter].getName}"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
      }
      exception.getMessage should include(
        "does not match component type [view], expected a class extending akka.javasdk.view.View")
    }

    "fail on class listed as endpoint without the endpoint annotation" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  grpc-endpoint = ["${classOf[GetHelloEndpoint].getName}"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
      }
      exception.getMessage should include(
        "does not match component type [grpc-endpoint], expected a class annotated with @GrpcEndpoint")
    }

    "fail on class listed as component without the component annotation" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  key-value-entity = ["${classOf[UnannotatedCounter].getName}"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
      }
      exception.getMessage should include(
        "does not match component type [key-value-entity], expected a class extending akka.javasdk.keyvalueentity.KeyValueEntity and annotated with @Component")
    }

    "fail on unknown key next to the component list" in {
      val config = ConfigFactory.parseString("""akka.javasdk.library.component.view = ["com.example.SomeView"]""")

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
      }
      exception.getMessage should include("Unknown key(s) [component] in config [akka.javasdk.library]")
    }

    "fail with the full path when a component type is not a list" in {
      val config = ConfigFactory.parseString("""akka.javasdk.library.components.view = "com.example.SomeView"""")

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
      }
      exception.getMessage should include(s"Config [$LibraryComponentsConfigPath.view] must be a list of class names")
    }

    "fail on an SDK component" in {
      val config = ConfigFactory.parseString(s"""
          |akka.javasdk.library.components {
          |  event-sourced-entity = ["${classOf[SessionMemoryEntity].getName}"]
          |}
          |""".stripMargin)

      val exception = intercept[IllegalStateException] {
        ComponentLocator.loadLibraryComponents(listedLibraryComponents(config), dynamicAccess, Set.empty)
      }
      exception.getMessage should include(s"SDK component(s) [${classOf[SessionMemoryEntity].getName}]")
    }
  }

  "ComponentLocator.locateComponents" should {

    // service main and test classes, the testkit and a library, each a separate classpath entry
    def withClasspath[T](mainMarked: Boolean, testMarked: Boolean)(test: DynamicAccess => T): T = {
      def marker(marked: Boolean) = if (marked) "akka.javasdk.service = true" else ""
      val serviceName = "akka-javasdk-components_com.example_service.conf"
      val entries = Seq(
        serviceName -> s"""${marker(mainMarked)}
          |akka.javasdk.components.key-value-entity = ["${classOf[Counter].getName}"]""".stripMargin,
        serviceName -> s"""${marker(testMarked)}
          |akka.javasdk.components.event-sourced-entity = ["${classOf[EmployeeEntity].getName}"]""".stripMargin,
        "akka-javasdk-components_akka-javasdk-testkit.conf" ->
        s"""akka.javasdk.components.view = ["${classOf[UserByEmailWithGet].getName}"]""",
        "akka-javasdk-components_com.library_library.conf" ->
        s"""akka.javasdk.components.http-endpoint = ["${classOf[GetHelloEndpoint].getName}", "${classOf[
          PostHelloEndpoint].getName}"]
           |akka.javasdk.service-setup = "com.library.Setup"""".stripMargin)
      val dirs = entries.map { case (fileName, content) =>
        val dir = Files.createTempDirectory("component-locator-spec")
        Files.createDirectories(dir.resolve("META-INF"))
        Files.writeString(dir.resolve("META-INF").resolve(fileName), content)
        dir
      }
      val classLoader = new URLClassLoader(dirs.map(_.toUri.toURL).toArray, getClass.getClassLoader) {
        // only the descriptors above, not those of the test classpath
        override def getResources(name: String): java.util.Enumeration[URL] = findResources(name)
      }
      try test(new ReflectiveDynamicAccess(classLoader))
      finally {
        classLoader.close()
        dirs.foreach { dir =>
          val paths = Files.walk(dir)
          try paths.sorted(java.util.Comparator.reverseOrder[Path]()).forEach(p => Files.delete(p))
          finally paths.close()
        }
      }
    }

    def config(content: String) =
      ConfigFactory.parseString(content).withFallback(ConfigFactory.defaultReference()).resolve()

    "load the service and SDK components but not unlisted library components" in {
      val located =
        withClasspath(mainMarked = true, testMarked = true)(ComponentLocator.locateComponents(_, config("")))
      located.components should contain theSameElementsAs Seq(
        classOf[Counter],
        classOf[EmployeeEntity],
        classOf[UserByEmailWithGet])
      located.service shouldBe None
    }

    "load the library components listed in config" in {
      val located = withClasspath(mainMarked = true, testMarked = true)(
        ComponentLocator.locateComponents(
          _,
          config(s"""akka.javasdk.library.components.http-endpoint = ["${classOf[GetHelloEndpoint].getName}"]""")))
      located.components should contain theSameElementsAs Seq(
        classOf[Counter],
        classOf[EmployeeEntity],
        classOf[UserByEmailWithGet],
        classOf[GetHelloEndpoint])
    }

    "load a stale unmarked descriptor with the file name of the marked service descriptor" in {
      val located =
        withClasspath(mainMarked = true, testMarked = false)(ComponentLocator.locateComponents(_, config("")))
      located.components should contain theSameElementsAs Seq(
        classOf[Counter],
        classOf[EmployeeEntity],
        classOf[UserByEmailWithGet])
    }

    "load the unmarked descriptors of the project, such as in the tests of a library" in {
      val located = withClasspath(mainMarked = false, testMarked = false)(
        ComponentLocator.locateComponents(
          _,
          config("""
          |akka.javasdk.dev-mode.project-group-id = com.example
          |akka.javasdk.dev-mode.project-artifact-id = service""".stripMargin)))
      located.components should contain theSameElementsAs Seq(
        classOf[Counter],
        classOf[EmployeeEntity],
        classOf[UserByEmailWithGet])
    }
  }

  "ComponentLocator.isComponentDescriptor" should {

    "match artifact-specific descriptor filenames" in {
      ComponentLocator.isComponentDescriptor("akka-javasdk-components_com.example_my-service.conf") shouldBe true
      ComponentLocator.isComponentDescriptor("akka-javasdk-components_io.akka_api.conf") shouldBe true
      ComponentLocator.isComponentDescriptor("akka-javasdk-components_org.company_lib.conf") shouldBe true
    }

    "not match legacy descriptor filename" in {
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
