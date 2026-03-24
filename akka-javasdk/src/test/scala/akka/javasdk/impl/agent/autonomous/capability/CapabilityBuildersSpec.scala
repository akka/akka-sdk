/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import scala.jdk.CollectionConverters._

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
  abstract class AgentA extends AutonomousAgent
  abstract class AgentB extends AutonomousAgent
  abstract class DummyDeveloper extends AutonomousAgent
  abstract class DummyReviewer extends AutonomousAgent

  "canAcceptTask" should {

    "create with task definition (no config)" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canAcceptTask(task1)
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala should have size 1
      val ta = definition.capabilities.asScala.head.asTaskAcceptance
      ta.taskDefinitions.asScala should have size 1
      ta.taskDefinitions.asScala.head.name() shouldBe "Task1"
    }

    "accumulate multiple task types" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canAcceptTask(task1)
        .canAcceptTask(task2)
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala should have size 2
      definition.capabilities.asScala.head.asTaskAcceptance.taskDefinitions.asScala.head.name() shouldBe "Task1"
      definition.capabilities.asScala(1).asTaskAcceptance.taskDefinitions.asScala.head.name() shouldBe "Task2"
    }

    "use config default for max iterations" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canAcceptTask(task1)
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala.head.asTaskAcceptance.maxIterations shouldBe None
    }

    "set explicit max iterations via lambda" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canAcceptTask(task1, task => task.maxIterationsPerTask(5))
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala.head.asTaskAcceptance.maxIterations shouldBe Some(5)
    }

    "set handoff targets via lambda" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canAcceptTask(task1, task => task.canHandoffTo(classOf[AgentA], classOf[AgentB]))
        .asInstanceOf[AgentDefinitionImpl]

      val ta = definition.capabilities.asScala.head.asTaskAcceptance
      ta.handoffTargets.asScala should have size 2
      ta.handoffTargets.asScala.head shouldBe classOf[AgentA]
      ta.handoffTargets.asScala(1) shouldBe classOf[AgentB]
    }

    "scope handoffs per task" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canAcceptTask(
          task1,
          task =>
            task
              .maxIterationsPerTask(10)
              .canHandoffTo(classOf[AgentA]))
        .canAcceptTask(
          task2,
          task =>
            task
              .maxIterationsPerTask(3)
              .canHandoffTo(classOf[AgentB]))
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala should have size 2

      val ta1 = definition.capabilities.asScala.head.asTaskAcceptance
      ta1.taskDefinitions.asScala.head.name() shouldBe "Task1"
      ta1.maxIterations shouldBe Some(10)
      ta1.handoffTargets.asScala should contain only classOf[AgentA]

      val ta2 = definition.capabilities.asScala(1).asTaskAcceptance
      ta2.taskDefinitions.asScala.head.name() shouldBe "Task2"
      ta2.maxIterations shouldBe Some(3)
      ta2.handoffTargets.asScala should contain only classOf[AgentB]
    }

    "start with empty handoff targets" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canAcceptTask(task1)
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala.head.asTaskAcceptance.handoffTargets.asScala shouldBe empty
    }
  }

  "canDelegateTo" should {

    "create with target agent (no config)" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canDelegateTo(classOf[AgentA])
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala should have size 1
      val d = definition.capabilities.asScala.head.asDelegation
      d.delegationTargets.asScala should have size 1
      d.maxParallel shouldBe None
    }

    "set max parallel workers via lambda" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canDelegateTo(classOf[AgentA], delegation => delegation.maxParallelWorkers(3))
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala.head.asDelegation.maxParallel shouldBe Some(3)
    }
  }

  "canLeadTeam" should {

    "create with member types" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canLeadTeam(team =>
          team
            .withMember(classOf[DummyDeveloper], member => member.maxInstances(3))
            .withMember(classOf[DummyReviewer]))
        .asInstanceOf[AgentDefinitionImpl]

      val leadership = definition.capabilities.asScala.head.asTeamLeadership
      leadership.members should have size 2
      leadership.members.head.agentClass shouldBe classOf[DummyDeveloper]
      leadership.members.head.maxMemberInstances shouldBe 3
      leadership.members(1).agentClass shouldBe classOf[DummyReviewer]
      leadership.members(1).maxMemberInstances shouldBe 1
    }

    "default maxInstances to 1" in {
      val definition = AgentDefinitionImpl
        .empty()
        .canLeadTeam(team => team.withMember(classOf[DummyDeveloper]))
        .asInstanceOf[AgentDefinitionImpl]

      val leadership = definition.capabilities.asScala.head.asTeamLeadership
      leadership.members.head.maxMemberInstances shouldBe 1
    }
  }

  "mixed capabilities" should {

    "accumulate on AgentDefinition" in {
      val definition = AgentDefinitionImpl
        .empty()
        .goal("Test agent")
        .canAcceptTask(task1, task => task.maxIterationsPerTask(5))
        .canDelegateTo(classOf[AgentA], delegation => delegation.maxParallelWorkers(2))
        .canLeadTeam(team => team.withMember(classOf[DummyDeveloper]))
        .asInstanceOf[AgentDefinitionImpl]

      definition.capabilities.asScala should have size 3
      definition.capabilities.asScala(0).asTaskAcceptance
      definition.capabilities.asScala(1).asDelegation
      definition.capabilities.asScala(2).asTeamLeadership
    }
  }
}
