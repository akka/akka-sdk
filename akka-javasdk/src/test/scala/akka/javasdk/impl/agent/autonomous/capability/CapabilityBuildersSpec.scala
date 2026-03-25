/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import scala.jdk.CollectionConverters._

import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.capability.Delegation
import akka.javasdk.agent.autonomous.capability.TaskAcceptance
import akka.javasdk.agent.task.Task
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

  "TaskAcceptance" should {

    "create with task definitions" in {
      val acceptance = TaskAcceptance.of(task1, task2).impl

      acceptance.taskDefinitions.asScala should have size 2
      acceptance.taskDefinitions.asScala.head.name() shouldBe "Task1"
      acceptance.taskDefinitions.asScala(1).name() shouldBe "Task2"
    }

    "use config default for max iterations" in {
      TaskAcceptance.of(task1).impl.maxIterations shouldBe None
    }

    "set explicit max iterations" in {
      TaskAcceptance.of(task1).maxIterationsPerTask(5).impl.maxIterations shouldBe Some(5)
    }

    "start with empty handoff targets" in {
      TaskAcceptance.of(task1).impl.handoffTargets.asScala shouldBe empty
    }

    "be immutable — maxIterationsPerTask returns new instance" in {
      val original = TaskAcceptance.of(task1)
      val modified = original.maxIterationsPerTask(5)

      original.impl.maxIterations shouldBe None
      modified.impl.maxIterations shouldBe Some(5)
    }
  }

  "Delegation" should {

    "create empty via static factory" in {
      val delegation = Delegation.to().impl

      delegation.delegationTargets.asScala shouldBe empty
      delegation.maxParallel shouldBe None
    }

    "set max parallel workers" in {
      Delegation.to().maxParallelWorkers(3).impl.maxParallel shouldBe Some(3)
    }

    "be immutable — maxParallelWorkers returns new instance" in {
      val original = Delegation.to()
      val modified = original.maxParallelWorkers(5)

      original.impl.maxParallel shouldBe None
      modified.impl.maxParallel shouldBe Some(5)
    }
  }

  "TeamLeadership" should {

    // Dummy agent classes for testing
    abstract class DummyDeveloper extends AutonomousAgent
    abstract class DummyReviewer extends AutonomousAgent

    "create with member types" in {
      val developer = new TeamMemberImpl(classOf[DummyDeveloper], 3)
      val reviewer = new TeamMemberImpl(classOf[DummyReviewer], 1)
      val leadership = TeamLeadershipImpl.create(developer, Array(reviewer))

      leadership.members should have size 2
      leadership.members.head.agentClass shouldBe classOf[DummyDeveloper]
      leadership.members.head.maxMemberInstances shouldBe 3
      leadership.members(1).agentClass shouldBe classOf[DummyReviewer]
      leadership.members(1).maxMemberInstances shouldBe 1
    }

    "default maxInstances to 1" in {
      val member = new TeamMemberImpl(classOf[DummyDeveloper], 1)

      member.maxMemberInstances shouldBe 1
    }

    "set maxInstances on member type" in {
      val original = new TeamMemberImpl(classOf[DummyDeveloper], 1)
      val modified = original.maxInstances(5).asInstanceOf[TeamMemberImpl]

      modified.maxMemberInstances shouldBe 5
      original.maxMemberInstances shouldBe 1 // original unchanged
    }
  }
}
