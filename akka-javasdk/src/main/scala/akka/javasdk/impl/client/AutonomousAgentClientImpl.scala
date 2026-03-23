/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.FutureConverters._

import akka.Done
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.autonomous.AgentSetup
import akka.javasdk.agent.task.Task
import akka.javasdk.client.AutonomousAgentClient
import akka.javasdk.impl.agent.autonomous.AgentSetupImpl
import akka.javasdk.impl.agent.autonomous.CapabilityConverter
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.{ ComponentClients => RuntimeComponentClients }
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] final class AutonomousAgentClientImpl(
    agentInstanceId: String,
    agentComponentId: String,
    runtimeComponentClients: RuntimeComponentClients,
    agentCapabilityConverter: Option[CapabilityConverter],
    serializer: Serializer,
    callMetadata: Option[Metadata])(implicit ec: ExecutionContext, system: ActorSystem[_])
    extends AutonomousAgentClient {

  private val log = LoggerFactory.getLogger(classOf[AutonomousAgentClientImpl])

  override def runSingleTaskAsync(task: Task[_]): CompletionStage[String] = {
    val taskId = java.util.UUID.randomUUID().toString
    log.debug(
      "runSingleTask: agent=[{}] instance=[{}] task=[{}] taskId=[{}]",
      agentComponentId,
      agentInstanceId,
      task.description(),
      taskId)
    val taskClient = new TaskClientImpl(taskId, runtimeComponentClients, serializer, callMetadata)
    taskClient
      .createAsync(task)
      .asScala
      .flatMap { _ =>
        log.debug(
          "runSingleTask: task created [{}], sending AssignTask to agent instance [{}]",
          taskId,
          agentInstanceId)
        runtimeComponentClients.autonomousAgentClient
          .assignTask(agentComponentId, agentInstanceId, taskId)
          .flatMap { _ =>
            log.debug(
              "runSingleTask: AssignTask ack for task [{}], sending AutoStop to instance [{}]",
              taskId,
              agentInstanceId)
            runtimeComponentClients.autonomousAgentClient
              .autoStop(agentComponentId, agentInstanceId)
          }
          .map { _ =>
            log.debug("runSingleTask: AutoStop ack for instance [{}], returning taskId [{}]", agentInstanceId, taskId)
            taskId
          }
      }
      .asJava
  }

  override def assignTasksAsync(taskIds: String*): CompletionStage[Done] = {
    log.debug("assignTasks: agent=[{}] instance=[{}] tasks={}", agentComponentId, agentInstanceId, taskIds)
    taskIds
      .foldLeft(Future.successful(())) { (prev, taskId) =>
        prev.flatMap { _ =>
          log.debug("assignTasks: sending AssignTask [{}] to agent instance [{}]", taskId, agentInstanceId)
          runtimeComponentClients.autonomousAgentClient
            .assignTask(agentComponentId, agentInstanceId, taskId)
            .map(_ => ())
        }
      }
      .map(_ => Done.done())
      .asJava
  }

  override def setupAsync(setup: AgentSetup): CompletionStage[Done] = {
    val setupImpl = setup.asInstanceOf[AgentSetupImpl].build()
    log.debug("setup: agent=[{}] instance=[{}]", agentComponentId, agentInstanceId)
    val converter = agentCapabilityConverter.getOrElse(
      throw new IllegalStateException("Agent capability converter not available in this context"))
    val spiCapabilities = converter.toSpiCapabilities(setupImpl.capabilities)
    runtimeComponentClients.autonomousAgentClient
      .applySetup(agentComponentId, agentInstanceId, setupImpl.goal, spiCapabilities)
      .map(_ => Done.done())
      .asJava
  }

  override def stopAsync(): CompletionStage[Done] = {
    log.debug("stop: agent=[{}] instance=[{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .stop(agentComponentId, agentInstanceId)
      .map(_ => Done.done())
      .asJava
  }
}
