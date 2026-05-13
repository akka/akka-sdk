/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.autonomous.AgentSetup
import akka.javasdk.agent.autonomous.capability.TaskAcceptance
import akka.javasdk.agent.task.Task
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentSetupSpec extends AnyWordSpec with Matchers with AutonomousAgentImplSupport {

  private val testTask = Task
    .define("TestTask")
    .description("A test task")
    .resultConformsTo(classOf[String])

  "AgentSetup" should {

    "start empty" in {
      val setup = AgentSetup.create().impl

      setup.instructions shouldBe None
      setup.capabilities.asScala shouldBe empty
    }

    "set instructions" in {
      val setup = AgentSetup.create().instructions("Do something").impl

      setup.instructions shouldBe Some("Do something")
    }

    "override instructions" in {
      val setup = AgentSetup.create().instructions("First instructions").instructions("Second instructions").impl

      setup.instructions shouldBe Some("Second instructions")
    }

    "set capabilities" in {
      val setup = AgentSetup.create().capability(TaskAcceptance.of(testTask)).impl

      setup.capabilities.asScala should have size 1
      setup.capabilities.asScala.head.asTaskAcceptance
    }

    "accumulate capabilities" in {
      val setup = AgentSetup
        .create()
        .capability(TaskAcceptance.of(testTask))
        .capability(DelegationImpl.create(Array()))
        .impl

      setup.capabilities.asScala should have size 2
      setup.capabilities.asScala.head.asTaskAcceptance
      setup.capabilities.asScala(1).asDelegation
    }

    "set instructions and capabilities together" in {
      val setup = AgentSetup
        .create()
        .instructions("Analyse data")
        .capability(TaskAcceptance.of(testTask).maxIterationsPerTask(5))
        .capability(DelegationImpl.create(Array()).maxParallelWorkers(2))
        .impl

      setup.instructions shouldBe Some("Analyse data")
      setup.capabilities.asScala should have size 2
    }

    "be immutable — each method returns a new instance" in {
      val setup1 = AgentSetup.create()
      val setup2 = setup1.instructions("Some instructions")
      val setup3 = setup2.capability(TaskAcceptance.of(testTask))

      setup1.impl.instructions shouldBe None
      setup1.impl.capabilities.asScala shouldBe empty

      setup2.impl.instructions shouldBe Some("Some instructions")
      setup2.impl.capabilities.asScala shouldBe empty

      setup3.impl.instructions shouldBe Some("Some instructions")
      setup3.impl.capabilities.asScala should have size 1
    }
  }
}
