/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.autonomous.AgentSetup
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.task.Task
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl
import akka.javasdk.impl.agent.autonomous.capability.HandoffImpl
import akka.javasdk.impl.agent.autonomous.capability.TaskAcceptanceImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentSetupSpec extends AnyWordSpec with Matchers with AutonomousAgentImplSupport {

  private val testTask = Task
    .define("TestTask")
    .description("A test task")
    .resultConformsTo(classOf[String])

  // Dummy agent classes for testing
  abstract class DummyAgent extends AutonomousAgent
  abstract class DummyAgent2 extends AutonomousAgent
  abstract class DummyDeveloper extends AutonomousAgent
  abstract class DummyReviewer extends AutonomousAgent

  "AgentSetup" should {

    "start empty" in {
      val setup = AgentSetup.create().impl

      setup.goal shouldBe None
      setup.capabilities.asScala shouldBe empty
    }

    "set goal" in {
      val setup = AgentSetup.create().goal("Do something").impl

      setup.goal shouldBe Some("Do something")
    }

    "override goal" in {
      val setup = AgentSetup.create().goal("First goal").goal("Second goal").impl

      setup.goal shouldBe Some("Second goal")
    }

    "canAcceptTasks" in {
      val setup = AgentSetup.create().canAcceptTasks(testTask).impl

      setup.capabilities.asScala should have size 1
      setup.capabilities.asScala.head.asTaskAcceptance
    }

    "canAcceptTasks with maxIterationsPerTask" in {
      val setup = AgentSetup.create().canAcceptTasks(testTask).maxIterationsPerTask(5).impl

      setup.capabilities.asScala should have size 1
      setup.capabilities.asScala.head.asTaskAcceptance.maxIterations shouldBe Some(5)
    }

    "canHandoffTo" in {
      val setup = AgentSetup.create().canHandoffTo(classOf[DummyAgent]).impl

      setup.capabilities.asScala should have size 1
      setup.capabilities.asScala.head.asHandoff.targetAgent shouldBe classOf[DummyAgent]
    }

    "canDelegateTo" in {
      val setup = AgentSetup.create().canDelegateTo(classOf[DummyAgent]).impl

      setup.capabilities.asScala should have size 1
      setup.capabilities.asScala.head.asDelegation
    }

    "canDelegateTo with maxParallelWorkers" in {
      val setup = AgentSetup.create().canDelegateTo(classOf[DummyAgent]).maxParallelWorkers(3).impl

      setup.capabilities.asScala should have size 1
      setup.capabilities.asScala.head.asDelegation.maxParallel shouldBe Some(3)
    }

    "canLeadTeam with members" in {
      val setup = AgentSetup
        .create()
        .canLeadTeam()
        .withMember(classOf[DummyDeveloper])
        .maxInstances(3)
        .withMember(classOf[DummyReviewer])
        .impl

      setup.capabilities.asScala should have size 1
      val tl = setup.capabilities.asScala.head.asTeamLeadership
      tl.members should have size 2
      tl.members.head.agentClass shouldBe classOf[DummyDeveloper]
      tl.members.head.maxMemberInstances shouldBe 3
      tl.members(1).agentClass shouldBe classOf[DummyReviewer]
      tl.members(1).maxMemberInstances shouldBe 1
    }

    "set goal and capabilities together" in {
      val setup = AgentSetup
        .create()
        .goal("Analyse data")
        .canAcceptTasks(testTask)
        .maxIterationsPerTask(5)
        .canDelegateTo(classOf[DummyAgent])
        .maxParallelWorkers(2)
        .impl

      setup.goal shouldBe Some("Analyse data")
      setup.capabilities.asScala should have size 2
    }

    "be immutable — each method returns a new instance" in {
      val setup1 = AgentSetup.create()
      val setup2 = setup1.goal("A goal")
      val setup3 = setup2.canAcceptTasks(testTask)

      setup1.impl.goal shouldBe None
      setup1.impl.capabilities.asScala shouldBe empty

      setup2.impl.goal shouldBe Some("A goal")
      setup2.impl.capabilities.asScala shouldBe empty

      setup3.impl.goal shouldBe Some("A goal")
      setup3.impl.capabilities.asScala should have size 1
    }

    "mixed capabilities" in {
      val setup = AgentSetup
        .create()
        .goal("Test")
        .canAcceptTasks(testTask)
        .maxIterationsPerTask(5)
        .canHandoffTo(classOf[DummyAgent])
        .canDelegateTo(classOf[DummyAgent2])
        .maxParallelWorkers(2)
        .impl

      setup.capabilities.asScala should have size 3
      setup.capabilities.asScala.head shouldBe a[TaskAcceptanceImpl]
      setup.capabilities.asScala(1) shouldBe a[HandoffImpl]
      setup.capabilities.asScala(2) shouldBe a[DelegationImpl]
    }
  }
}
