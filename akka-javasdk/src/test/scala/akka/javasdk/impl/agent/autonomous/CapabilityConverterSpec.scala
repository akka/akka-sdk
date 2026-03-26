/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import akka.javasdk.agent.autonomous.capability.Delegation
import akka.javasdk.agent.autonomous.capability.TaskAcceptance
import akka.javasdk.agent.task.Task
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl
import akka.runtime.sdk.spi.SpiAutonomousAgent
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CapabilityConverterSpec extends AnyWordSpec with Matchers {

  // --- Test fixtures ---

  case class TypedResult(value: String, score: Int)

  private val stringTask = Task
    .define("StringTask")
    .description("A task with String result")
    .resultConformsTo(classOf[String])

  private val typedTask = Task
    .define("TypedTask")
    .description("A task with typed result")
    .resultConformsTo(classOf[TypedResult])

  // --- toSpiTaskDefinition tests ---

  "CapabilityConverter.toSpiTaskDefinition" should {

    "convert a String result task definition" in {
      val spiTaskDefinition = CapabilityConverter.toSpiTaskDefinition(stringTask)

      spiTaskDefinition.name shouldBe "StringTask"
      spiTaskDefinition.description shouldBe "A task with String result"
      spiTaskDefinition.resultTypeName shouldBe "java.lang.String"
      spiTaskDefinition.resultSchema shouldBe None
      spiTaskDefinition.instructionTemplate shouldBe None
      spiTaskDefinition.templateParameters shouldBe empty
    }

    "convert a typed result task definition with schema" in {
      val spiTaskDefinition = CapabilityConverter.toSpiTaskDefinition(typedTask)

      spiTaskDefinition.name shouldBe "TypedTask"
      spiTaskDefinition.description shouldBe "A task with typed result"
      spiTaskDefinition.resultTypeName should include("TypedResult")
      spiTaskDefinition.resultSchema shouldBe defined
    }

    "convert a task with instructions" in {
      val taskWithInstructions = stringTask.instructions("Do this specific thing")
      val spiTaskDefinition = CapabilityConverter.toSpiTaskDefinition(taskWithInstructions)

      // instructions are per-request, not part of the definition
      spiTaskDefinition.name shouldBe "StringTask"
    }
  }

  // --- toSpiCapabilities tests ---

  "CapabilityConverter.toSpiCapabilities" should {

    val converter = new CapabilityConverter(
      agentDefinitionMap = Map.empty,
      agentDescriptors = Seq.empty,
      requestBasedAgentDescriptors = Seq.empty,
      defaultMaxIterationsPerTask = 10,
      defaultMaxParallelWorkers = 3)

    "convert task acceptance with default max iterations" in {
      val taskAcceptance = TaskAcceptance.of(stringTask)
      val capabilities = converter.toSpiCapabilities(java.util.List.of(taskAcceptance))

      capabilities should have size 1
      val spiTaskAcceptance = capabilities.head.asInstanceOf[SpiAutonomousAgent.TaskAcceptance]
      spiTaskAcceptance.taskDefinitions should have size 1
      spiTaskAcceptance.taskDefinitions.head.name shouldBe "StringTask"
      spiTaskAcceptance.maxIterationsPerTask shouldBe 10
      spiTaskAcceptance.handoffs shouldBe empty
    }

    "convert task acceptance with explicit max iterations" in {
      val taskAcceptance = TaskAcceptance.of(stringTask).maxIterationsPerTask(5)
      val capabilities = converter.toSpiCapabilities(java.util.List.of(taskAcceptance))

      capabilities should have size 1
      val spiTaskAcceptance = capabilities.head.asInstanceOf[SpiAutonomousAgent.TaskAcceptance]
      spiTaskAcceptance.maxIterationsPerTask shouldBe 5
    }

    "convert multiple task acceptances" in {
      val acceptance1 = TaskAcceptance.of(stringTask)
      val acceptance2 = TaskAcceptance.of(typedTask).maxIterationsPerTask(20)
      val capabilities = converter.toSpiCapabilities(java.util.List.of(acceptance1, acceptance2))

      capabilities should have size 2
      val first = capabilities.head.asInstanceOf[SpiAutonomousAgent.TaskAcceptance]
      val second = capabilities(1).asInstanceOf[SpiAutonomousAgent.TaskAcceptance]
      first.taskDefinitions.head.name shouldBe "StringTask"
      first.maxIterationsPerTask shouldBe 10
      second.taskDefinitions.head.name shouldBe "TypedTask"
      second.maxIterationsPerTask shouldBe 20
    }

    "convert delegation with default max parallel workers" in {
      val delegation = DelegationImpl.create(Array())
      val capabilities = converter.toSpiCapabilities(java.util.List.of(delegation))

      capabilities should have size 1
      val orchestrator = capabilities.head.asInstanceOf[SpiAutonomousAgent.DelegationOrchestrator]
      orchestrator.delegationGroups should have size 1
      orchestrator.delegationGroups.head.maxParallelWorkers shouldBe 3
    }

    "convert delegation with explicit max parallel workers" in {
      val delegation = Delegation.to().maxParallelWorkers(7).asInstanceOf[DelegationImpl]
      val capabilities = converter.toSpiCapabilities(java.util.List.of(delegation))

      val orchestrator = capabilities.head.asInstanceOf[SpiAutonomousAgent.DelegationOrchestrator]
      orchestrator.delegationGroups.head.maxParallelWorkers shouldBe 7
    }

    "convert mixed task acceptance and delegation" in {
      val taskAcceptance = TaskAcceptance.of(stringTask)
      val delegation = DelegationImpl.create(Array())
      val capabilities = converter.toSpiCapabilities(java.util.List.of(taskAcceptance, delegation))

      capabilities should have size 2
      capabilities.head shouldBe a[SpiAutonomousAgent.TaskAcceptance]
      capabilities(1) shouldBe a[SpiAutonomousAgent.DelegationOrchestrator]
    }

    "produce empty capabilities from empty input" in {
      val capabilities = converter.toSpiCapabilities(java.util.List.of())
      capabilities shouldBe empty
    }

    "merge multiple delegations into single orchestrator" in {
      val delegation1 = DelegationImpl.create(Array()).maxParallelWorkers(2).asInstanceOf[DelegationImpl]
      val delegation2 = DelegationImpl.create(Array()).maxParallelWorkers(5).asInstanceOf[DelegationImpl]
      val capabilities = converter.toSpiCapabilities(java.util.List.of(delegation1, delegation2))

      val orchestrators = capabilities.collect { case o: SpiAutonomousAgent.DelegationOrchestrator => o }
      orchestrators should have size 1
      orchestrators.head.delegationGroups should have size 2
    }

    "convert request-based delegation with default max parallel workers" in {
      val delegation = DelegationImpl.createRequestBased(Array())
      val capabilities = converter.toSpiCapabilities(java.util.List.of(delegation))

      capabilities should have size 1
      val orchestrator = capabilities.head.asInstanceOf[SpiAutonomousAgent.DelegationOrchestrator]
      orchestrator.delegationGroups should have size 1
      orchestrator.delegationGroups.head.delegationTargets shouldBe empty
      orchestrator.delegationGroups.head.requestBasedTargets shouldBe empty
      orchestrator.delegationGroups.head.maxParallelWorkers shouldBe 3
    }

    "merge autonomous and request-based delegations into single orchestrator" in {
      val autonomousDelegation = DelegationImpl.create(Array()).maxParallelWorkers(2).asInstanceOf[DelegationImpl]
      val requestBasedDelegation =
        DelegationImpl.createRequestBased(Array()).maxParallelWorkers(4).asInstanceOf[DelegationImpl]
      val capabilities =
        converter.toSpiCapabilities(java.util.List.of(autonomousDelegation, requestBasedDelegation))

      val orchestrators = capabilities.collect { case o: SpiAutonomousAgent.DelegationOrchestrator => o }
      orchestrators should have size 1
      orchestrators.head.delegationGroups should have size 2
      orchestrators.head.delegationGroups(0).maxParallelWorkers shouldBe 2
      orchestrators.head.delegationGroups(1).maxParallelWorkers shouldBe 4
    }
  }
}
