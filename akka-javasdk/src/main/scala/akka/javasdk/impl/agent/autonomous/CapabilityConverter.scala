/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous

import scala.jdk.CollectionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.capability.AgentCapability
import akka.javasdk.agent.task.TaskDefinition
import akka.javasdk.agent.task.TaskTemplate
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.agent.autonomous.capability.DelegationImpl
import akka.javasdk.impl.agent.autonomous.capability.TeamMemberImpl
import akka.javasdk.impl.agent.autonomous.capability.TaskAcceptanceImpl
import akka.javasdk.impl.agent.autonomous.capability.TeamLeadershipImpl
import akka.javasdk.impl.reflection.Reflect
import akka.runtime.sdk.spi.AutonomousAgentDescriptor
import akka.runtime.sdk.spi.SpiAutonomousAgent
import akka.runtime.sdk.spi.SpiTask

/**
 * INTERNAL API
 *
 * Converts SDK capability types to SPI capability types. Holds the agent registry needed to resolve delegation and
 * handoff target references.
 */
@InternalApi
private[javasdk] class CapabilityConverter(
    agentDefinitionMap: Map[String, (AgentDefinitionImpl, Seq[SpiTask.SpiTaskDefinition])],
    agentDescriptors: Seq[AutonomousAgentDescriptor],
    defaultMaxIterationsPerTask: Int,
    defaultMaxParallelWorkers: Int) {

  def toSpiCapabilities(sdkCapabilities: java.util.List[AgentCapability]): Seq[SpiAutonomousAgent.Capability] = {
    val allCapabilities = sdkCapabilities.asScala.toSeq

    val spiTaskAcceptances: Seq[SpiAutonomousAgent.Capability] = allCapabilities.collect {
      case ta: TaskAcceptanceImpl =>
        new SpiAutonomousAgent.TaskAcceptance(
          taskDefinitions = ta.taskDefinitions.asScala.toSeq.map(CapabilityConverter.toSpiTaskDefinition),
          maxIterationsPerTask = ta.maxIterations.getOrElse(defaultMaxIterationsPerTask),
          handoffs = resolveHandoffTargets(ta.handoffTargets.asScala.toSeq))
    }

    val delegations = allCapabilities.collect { case d: DelegationImpl => d }
    val spiDelegationOrchestrators: Seq[SpiAutonomousAgent.Capability] =
      if (delegations.nonEmpty) {
        val delegationGroups = delegations.map { d =>
          new SpiAutonomousAgent.DelegationGroup(
            delegationTargets = resolveDelegationTargets(d.delegationTargets.asScala.toSeq),
            maxParallelWorkers = d.maxParallel.getOrElse(defaultMaxParallelWorkers))
        }
        Seq(new SpiAutonomousAgent.DelegationOrchestrator(delegationGroups))
      } else Seq.empty

    val teamLeaderships = allCapabilities.collect { case t: TeamLeadershipImpl => t }
    val spiTeamLeads: Seq[SpiAutonomousAgent.Capability] =
      if (teamLeaderships.nonEmpty) {
        val teamMembers = teamLeaderships.flatMap(_.members).map(resolveTeamMember)
        Seq(new SpiAutonomousAgent.TeamLead(teamMembers))
      } else Seq.empty

    spiTaskAcceptances ++ spiDelegationOrchestrators ++ spiTeamLeads
  }

  private def resolveHandoffTargets(
      sdkHandoffTargets: Seq[Class[_ <: AutonomousAgent]]): Seq[SpiAutonomousAgent.HandoffTarget] =
    sdkHandoffTargets.map { targetAgentClass =>
      val targetComponentId = Reflect.readComponentId(targetAgentClass)
      val (_, targetTaskDefinitions) = agentDefinitionMap.getOrElse(
        targetComponentId,
        throw new IllegalStateException(
          s"Handoff target [$targetComponentId] (${targetAgentClass.getName}) not found. " +
          "Ensure the target agent is a registered AutonomousAgent component."))
      val targetDescriptor = agentDescriptors.find(_.componentId == targetComponentId)
      new SpiAutonomousAgent.HandoffTarget(
        agentComponentId = targetComponentId,
        description = targetDescriptor.flatMap(_.description),
        acceptedTasks = targetTaskDefinitions)
    }

  private def resolveTeamMember(teamMember: TeamMemberImpl): SpiAutonomousAgent.TeamMemberType = {
    val targetComponentId = Reflect.readComponentId(teamMember.agentClass)
    val (_, targetTaskDefinitions) = agentDefinitionMap.getOrElse(
      targetComponentId,
      throw new IllegalStateException(
        s"Team member type [$targetComponentId] (${teamMember.agentClass.getName}) not found. " +
        "Ensure the target agent is a registered AutonomousAgent component."))
    val targetDescriptor = agentDescriptors.find(_.componentId == targetComponentId)
    new SpiAutonomousAgent.TeamMemberType(
      agentComponentId = targetComponentId,
      description = targetDescriptor.flatMap(_.description),
      maxInstances = teamMember.maxMemberInstances,
      acceptedTasks = targetTaskDefinitions)
  }

  private def resolveDelegationTargets(
      sdkDelegationTargets: Seq[Class[_ <: AutonomousAgent]]): Seq[SpiAutonomousAgent.DelegationTarget] =
    sdkDelegationTargets.map { targetAgentClass =>
      val targetComponentId = Reflect.readComponentId(targetAgentClass)
      val (_, targetTaskDefinitions) = agentDefinitionMap.getOrElse(
        targetComponentId,
        throw new IllegalStateException(
          s"Delegation target [$targetComponentId] (${targetAgentClass.getName}) not found. " +
          "Ensure the target agent is a registered AutonomousAgent component."))
      val targetDescriptor = agentDescriptors.find(_.componentId == targetComponentId)
      new SpiAutonomousAgent.DelegationTarget(
        agentComponentId = targetComponentId,
        description = targetDescriptor.flatMap(_.description),
        acceptedTasks = targetTaskDefinitions)
    }
}

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object CapabilityConverter {

  def toSpiTaskDefinition(taskDefinition: TaskDefinition[_]): SpiTask.SpiTaskDefinition = {
    val resultType = taskDefinition.resultType()
    val resultSchema =
      if (resultType == classOf[String]) None
      else {
        try Some(JsonSchema.jsonSchemaFor(resultType))
        catch { case scala.util.control.NonFatal(_) => None }
      }
    val (instructionTemplate, templateParameters) = taskDefinition match {
      case template: TaskTemplate[_] =>
        val parameters = template.templateParameterNames().asScala.toSeq.map { name =>
          new SpiTask.SpiTemplateParameter(name, name)
        }
        (Option(template.instructionTemplate()).filter(_.nonEmpty), parameters)
      case _ => (None, Seq.empty)
    }
    new SpiTask.SpiTaskDefinition(
      name = taskDefinition.name(),
      description = taskDefinition.description(),
      resultTypeName = resultType.getName,
      resultSchema = resultSchema,
      instructionTemplate = instructionTemplate,
      templateParameters = templateParameters)
  }
}
