/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous.capability

import java.util

import akka.annotation.InternalApi
import akka.javasdk.agent.task.TaskDefinition

/**
 * INTERNAL API
 */
@InternalApi
final case class TaskAcceptanceImpl(taskDefinitions: util.List[TaskDefinition[_]], maxIterations: Option[Int])
    extends AgentCapability

/**
 * INTERNAL API
 */
@InternalApi
object TaskAcceptanceImpl {
  def create(tasks: Array[TaskDefinition[_]]): TaskAcceptanceImpl =
    TaskAcceptanceImpl(taskDefinitions = util.List.of(tasks: _*), maxIterations = None)
}
