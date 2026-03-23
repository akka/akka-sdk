/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.autonomous.AgentDefinition
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.task.Task
import akka.javasdk.impl.agent.autonomous.AgentDefinitionImpl
import akka.javasdk.impl.agent.autonomous.AutonomousAgentImplSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CapabilityBuildersSpec extends AnyWordSpec with Matchers with AutonomousAgentImplSupport {

  private val task1 = Task
    .define("Task1")
    .description("First task")
    .resultConformsTo(classOf[String])

  private val task2 = Task
    .define("Task2")
    .description("Second task")
    .resultConformsTo(classOf[String])

  // Dummy agent classes for testing
  abstract class DummyAgent extends AutonomousAgent
  abstract class DummyAgent2 extends AutonomousAgent
  abstract class DummyDeveloper extends AutonomousAgent
  abstract class DummyReviewer extends AutonomousAgent

  private def build(definition: AgentDefinition): AgentDefinitionImpl =
    definition.asInstanceOf[AgentDefinitionImpl].build()

  "canAcceptTasks" should {

    "create with task definitions" in {
      val built = build(AgentDefinitionImpl.empty().canAcceptTasks(task1, task2))
      val caps = built.capabilities.asScala
      caps should have size 1
      val ta = caps.head.asInstanceOf[TaskAcceptanceImpl]
      ta.taskDefinitions.asScala should have size 2
      ta.taskDefinitions.asScala.head.name() shouldBe "Task1"
      ta.taskDefinitions.asScala(1).name() shouldBe "Task2"
    }

    "use config default for max iterations" in {
      val built = build(AgentDefinitionImpl.empty().canAcceptTasks(task1))
      val ta = built.capabilities.asScala.head.asInstanceOf[TaskAcceptanceImpl]
      ta.maxIterations shouldBe None
    }

    "set explicit max iterations" in {
      val built = build(AgentDefinitionImpl.empty().canAcceptTasks(task1).maxIterationsPerTask(5))
      val ta = built.capabilities.asScala.head.asInstanceOf[TaskAcceptanceImpl]
      ta.maxIterations shouldBe Some(5)
    }

    "be immutable — maxIterationsPerTask returns new instance" in {
      val original = AgentDefinitionImpl.empty().canAcceptTasks(task1)
      val modified = original.maxIterationsPerTask(5)

      val origBuilt = build(original)
      val modBuilt = build(modified)

      origBuilt.capabilities.asScala.head.asInstanceOf[TaskAcceptanceImpl].maxIterations shouldBe None
      modBuilt.capabilities.asScala.head.asInstanceOf[TaskAcceptanceImpl].maxIterations shouldBe Some(5)
    }

    "support multiple task groups with different limits" in {
      val built = build(
        AgentDefinitionImpl
          .empty()
          .canAcceptTasks(task1)
          .maxIterationsPerTask(5)
          .canAcceptTasks(task2)
          .maxIterationsPerTask(10))
      val caps = built.capabilities.asScala
      caps should have size 2
      caps.head.asInstanceOf[TaskAcceptanceImpl].maxIterations shouldBe Some(5)
      caps(1).asInstanceOf[TaskAcceptanceImpl].maxIterations shouldBe Some(10)
    }
  }

  "canHandoffTo" should {

    "create handoff capability" in {
      val built = build(AgentDefinitionImpl.empty().canHandoffTo(classOf[DummyAgent]))
      val caps = built.capabilities.asScala
      caps should have size 1
      caps.head shouldBe a[HandoffImpl]
      caps.head.asInstanceOf[HandoffImpl].targetAgent shouldBe classOf[DummyAgent]
    }

    "support multiple handoff targets" in {
      val built = build(
        AgentDefinitionImpl
          .empty()
          .canHandoffTo(classOf[DummyAgent])
          .canHandoffTo(classOf[DummyAgent2]))
      val caps = built.capabilities.asScala
      caps should have size 2
      caps.head.asInstanceOf[HandoffImpl].targetAgent shouldBe classOf[DummyAgent]
      caps(1).asInstanceOf[HandoffImpl].targetAgent shouldBe classOf[DummyAgent2]
    }
  }

  "canDelegateTo" should {

    "create delegation capability" in {
      val built = build(AgentDefinitionImpl.empty().canDelegateTo(classOf[DummyAgent]))
      val caps = built.capabilities.asScala
      caps should have size 1
      caps.head shouldBe a[DelegationImpl]
      val d = caps.head.asInstanceOf[DelegationImpl]
      d.delegationTargets.asScala should contain(classOf[DummyAgent])
      d.maxParallel shouldBe None
    }

    "set max parallel workers" in {
      val built = build(
        AgentDefinitionImpl
          .empty()
          .canDelegateTo(classOf[DummyAgent])
          .maxParallelWorkers(3))
      val d = built.capabilities.asScala.head.asInstanceOf[DelegationImpl]
      d.maxParallel shouldBe Some(3)
    }

    "be immutable — maxParallelWorkers returns new instance" in {
      val original = AgentDefinitionImpl.empty().canDelegateTo(classOf[DummyAgent])
      val modified = original.maxParallelWorkers(5)

      build(original).capabilities.asScala.head.asInstanceOf[DelegationImpl].maxParallel shouldBe None
      build(modified).capabilities.asScala.head.asInstanceOf[DelegationImpl].maxParallel shouldBe Some(5)
    }
  }

  "canLeadTeam" should {

    "create team with members" in {
      val built = build(
        AgentDefinitionImpl
          .empty()
          .canLeadTeam()
          .withMember(classOf[DummyDeveloper])
          .maxInstances(3)
          .withMember(classOf[DummyReviewer]))
      val caps = built.capabilities.asScala
      caps should have size 1
      val tl = caps.head.asInstanceOf[TeamLeadershipImpl]
      tl.members should have size 2
      tl.members.head.agentClass shouldBe classOf[DummyDeveloper]
      tl.members.head.maxMemberInstances shouldBe 3
      tl.members(1).agentClass shouldBe classOf[DummyReviewer]
      tl.members(1).maxMemberInstances shouldBe 1
    }

    "default maxInstances to 1" in {
      val built = build(
        AgentDefinitionImpl
          .empty()
          .canLeadTeam()
          .withMember(classOf[DummyDeveloper]))
      val tl = built.capabilities.asScala.head.asInstanceOf[TeamLeadershipImpl]
      tl.members.head.maxMemberInstances shouldBe 1
    }
  }

  "mixed capabilities" should {

    "support task acceptance + handoff + delegation" in {
      val built = build(
        AgentDefinitionImpl
          .empty()
          .goal("Test agent")
          .canAcceptTasks(task1)
          .maxIterationsPerTask(5)
          .canHandoffTo(classOf[DummyAgent])
          .canDelegateTo(classOf[DummyAgent2])
          .maxParallelWorkers(2))
      val caps = built.capabilities.asScala
      caps should have size 3
      caps.head shouldBe a[TaskAcceptanceImpl]
      caps(1) shouldBe a[HandoffImpl]
      caps(2) shouldBe a[DelegationImpl]
    }

    "support task acceptance + team leadership" in {
      val built = build(
        AgentDefinitionImpl
          .empty()
          .canAcceptTasks(task1)
          .maxIterationsPerTask(40)
          .canLeadTeam()
          .withMember(classOf[DummyDeveloper])
          .maxInstances(3)
          .withMember(classOf[DummyReviewer]))
      val caps = built.capabilities.asScala
      caps should have size 2
      caps.head shouldBe a[TaskAcceptanceImpl]
      caps(1) shouldBe a[TeamLeadershipImpl]
    }

    "general config methods finalize pending capability" in {
      val built = build(
        AgentDefinitionImpl
          .empty()
          .canAcceptTasks(task1)
          .maxIterationsPerTask(5)
          .goal("Updated goal"))
      built.goal shouldBe "Updated goal"
      val caps = built.capabilities.asScala
      caps should have size 1
      caps.head.asInstanceOf[TaskAcceptanceImpl].maxIterations shouldBe Some(5)
    }
  }

  "dynamic agent (no capabilities)" should {

    "create empty definition" in {
      val built = build(AgentDefinitionImpl.empty())
      built.capabilities.asScala shouldBe empty
    }
  }
}
