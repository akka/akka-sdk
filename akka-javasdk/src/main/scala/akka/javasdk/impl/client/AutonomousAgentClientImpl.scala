/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.client

import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.Metadata
import akka.javasdk.agent.autonomous.AgentSetup
import akka.javasdk.agent.autonomous.AgentState
import akka.javasdk.agent.autonomous.AutonomousAgent
import akka.javasdk.agent.autonomous.Notification
import akka.javasdk.agent.task.Task
import akka.javasdk.agent.task.TaskKey
import akka.javasdk.client.AutonomousAgentClient
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.agent.autonomous.AgentSetupImpl
import akka.javasdk.impl.agent.autonomous.CapabilityConverter
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.SpiAutonomousAgent.{ Notification => SpiNotification }
import akka.runtime.sdk.spi.{ ComponentClients => RuntimeComponentClients }
import akka.stream.Materializer
import akka.stream.javadsl.Source
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
    val taskKey = new TaskKey(java.util.UUID.randomUUID().toString, task.name())
    log.debug(
      "runSingleTask: agent [{}] instance [{}] task [{}] - [{}]",
      agentComponentId,
      agentInstanceId,
      task.description(),
      taskKey)

    val taskClient =
      new TaskClientImpl(
        taskKey.id(),
        runtimeComponentClients,
        serializer,
        callMetadata,
        Materializer.matFromSystem(system))

    taskClient
      .createAsync(task)
      .asScala
      .flatMap { _ =>
        log.debug(
          "runSingleTask: task created [{}], sending AssignTask with stopWhenDone to agent instance [{}]",
          taskKey,
          agentInstanceId)

        runtimeComponentClients.autonomousAgentClient
          .assignTask(
            agentComponentId,
            agentInstanceId,
            taskKey.id(),
            stopWhenDone = true,
            callMetadata.flatMap(_.asInstanceOf[MetadataImpl].context))
          .map { _ =>
            log.debug("runSingleTask: AssignTask ack for task [{}], returning id [{}]", taskKey, agentInstanceId)
            taskKey.id()
          }
      }
      .asJava
  }

  override def assignTasksAsync(taskIds: String*): CompletionStage[Done] = {
    log.debug(
      "assignTasks: agent [{}] instance [{}] tasks [{}]",
      agentComponentId,
      agentInstanceId,
      taskIds.mkString(", "))

    taskIds
      .foldLeft(Future.successful(Done.done())) { (prev, taskId) =>
        prev.flatMap { _ =>
          runtimeComponentClients.autonomousAgentClient
            .assignTask(
              agentComponentId,
              agentInstanceId,
              taskId,
              stopWhenDone = false,
              callMetadata.flatMap(_.asInstanceOf[MetadataImpl].context))
        }
      }
      .asJava
  }

  override def setupAsync(setup: AgentSetup): CompletionStage[Done] = {
    val setupImpl = setup.asInstanceOf[AgentSetupImpl]
    log.debug("setup: agent [{}] instance [{}]", agentComponentId, agentInstanceId)

    val converter = agentCapabilityConverter.getOrElse(
      throw new IllegalStateException("Agent capability converter not available in this context"))

    val spiCapabilities = converter.toSpiCapabilities(setupImpl.capabilities)
    runtimeComponentClients.autonomousAgentClient
      .applySetup(agentComponentId, agentInstanceId, setupImpl.goal, spiCapabilities)
      .asJava
  }

  override def getStateAsync: CompletionStage[AgentState] = {
    log.debug("getState: agent [{}] instance [{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .getState(agentComponentId, agentInstanceId)
      .map { spiState =>
        new AgentState(
          spiState.phase,
          spiState.paused,
          spiState.goal,
          new AutonomousAgent.TokenUsage(spiState.totalInputTokens, spiState.totalOutputTokens),
          spiState.currentTask.map(t => new TaskKey(t.id, t.name)).toJava,
          spiState.pendingTaskIds.asJava)
      }
      .asJava
  }

  override def pauseAsync(): CompletionStage[Done] = {
    log.debug("pause: agent [{}] instance [{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .pause(agentComponentId, agentInstanceId)
      .asJava
  }

  override def resumeAsync(): CompletionStage[Done] = {
    log.debug("resume: agent [{}] instance [{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .resume(agentComponentId, agentInstanceId)
      .asJava
  }

  override def stopAsync(): CompletionStage[Done] = {
    log.debug("stop: agent [{}] instance [{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .stop(agentComponentId, agentInstanceId)
      .asJava
  }

  override def notificationStream(): Source[Notification, NotUsed] = {
    log.debug("notificationStream: agent [{}] instance [{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .notificationStream(agentComponentId, agentInstanceId)
      .collect(toNotification)
      .asJava
  }

  private val toNotification: PartialFunction[SpiNotification, Notification] = {
    case _: SpiNotification.Activated        => new Notification.Activated
    case _: SpiNotification.Deactivated      => new Notification.Deactivated
    case _: SpiNotification.IterationStarted => new Notification.IterationStarted
    case c: SpiNotification.IterationCompleted =>
      new Notification.IterationCompleted(new AutonomousAgent.TokenUsage(c.inputTokens, c.outputTokens))
    case f: SpiNotification.IterationFailed => new Notification.IterationFailed(f.reason)
    case _: SpiNotification.Stopped         => new Notification.Stopped
    case t: SpiNotification.TaskStarted     => new Notification.TaskStarted(t.taskKey.id, t.taskKey.name)
    case t: SpiNotification.TaskCompleted   => new Notification.TaskCompleted(t.taskKey.id)
    case t: SpiNotification.TaskFailed      => new Notification.TaskFailed(t.taskKey.id, t.reason)
    // ignore unknown because the runtime should be able to add new notification events without breaking old SDK
  }
}
