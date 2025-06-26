/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.util.concurrent.ConcurrentHashMap

import akka.annotation.InternalApi
import akka.javasdk.agent.ModelProvider

/**
 * INTERNAL API: Makes it possible to override the ModelProvider for agents when running tests, without requiring that
 * the agent itself is using dependency injection of ModelProvider.
 */
@InternalApi private[javasdk] class OverrideModelProvider {
  private val modelProviderByAgentId = new ConcurrentHashMap[String, ModelProvider]()

  def setModelProviderForAgent(agentId: String, modelProvider: ModelProvider): Unit =
    modelProviderByAgentId.put(agentId, modelProvider)

  def getModelProviderForAgent(agentId: String): Option[ModelProvider] =
    Option(modelProviderByAgentId.get(agentId))

}
