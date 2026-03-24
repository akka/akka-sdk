/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import java.util

import akka.annotation.InternalApi
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.capability.TaskAcceptance
import akka.javasdk.agent.task.TaskDefinition

/**
 * INTERNAL API
 */
@InternalApi
final case class TaskAcceptanceImpl(
    taskDefinitions: util.List[TaskDefinition[_]],
    maxIterations: Option[Int],
    handoffTargets: util.List[Class[_ <: AutonomousAgent]])
    extends TaskAcceptance {

  override def maxIterationsPerTask(max: Int): TaskAcceptance =
    copy(maxIterations = Some(max))

  override def canHandoffTo(agents: Class[_ <: AutonomousAgent]*): TaskAcceptance = {
    val result = new util.ArrayList[Class[_ <: AutonomousAgent]](handoffTargets)
    agents.foreach(result.add)
    copy(handoffTargets = util.Collections.unmodifiableList(result))
  }
}

/**
 * INTERNAL API
 */
@InternalApi
object TaskAcceptanceImpl {
  def create(tasks: Array[TaskDefinition[_]]): TaskAcceptanceImpl =
    TaskAcceptanceImpl(taskDefinitions = util.List.of(tasks: _*), maxIterations = None, handoffTargets = util.List.of())
}
