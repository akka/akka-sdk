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
    val taskId = java.util.UUID.randomUUID().toString
    log.debug(
      "runSingleTask: agent [{}] instance [{}] task [{}] - [{}]",
      agentComponentId,
      agentInstanceId,
      task.description(),
      taskId)

    val taskClient =
      new TaskClientImpl(taskId, runtimeComponentClients, serializer, callMetadata, Materializer.matFromSystem(system))

    taskClient
      .createAsync(task)
      .asScala
      .flatMap { _ =>
        log.debug(
          "runSingleTask: task created [{}], sending AssignTask with stopWhenDone to agent instance [{}]",
          taskId,
          agentInstanceId)

        runtimeComponentClients.autonomousAgentClient
          .assignTask(
            agentComponentId,
            agentInstanceId,
            taskId,
            stopWhenDone = true,
            callMetadata.flatMap(_.asInstanceOf[MetadataImpl].context))
          .map { _ =>
            log.debug("runSingleTask: AssignTask ack for task [{}], returning id [{}]", taskId, agentInstanceId)
            taskId
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
          spiState.suspended,
          spiState.goal,
          new AutonomousAgent.TokenUsage(spiState.totalInputTokens, spiState.totalOutputTokens),
          spiState.currentTask.map(t => new TaskKey(t.id, t.name)).toJava,
          spiState.pendingTaskIds.asJava)
      }
      .asJava
  }

  override def suspendAsync(): CompletionStage[Done] = {
    log.debug("pause: agent [{}] instance [{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .suspend(agentComponentId, agentInstanceId, callMetadata.flatMap(_.asInstanceOf[MetadataImpl].context))
      .asJava
  }

  override def resumeAsync(): CompletionStage[Done] = {
    log.debug("resume: agent [{}] instance [{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .resume(agentComponentId, agentInstanceId, callMetadata.flatMap(_.asInstanceOf[MetadataImpl].context))
      .asJava
  }

  override def terminateAsync(): CompletionStage[Done] = {
    log.debug("stop: agent [{}] instance [{}]", agentComponentId, agentInstanceId)
    runtimeComponentClients.autonomousAgentClient
      .terminate(agentComponentId, agentInstanceId, callMetadata.flatMap(_.asInstanceOf[MetadataImpl].context))
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
    // Lifecycle
    case _: SpiNotification.Activated        => new Notification.Activated
    case _: SpiNotification.Deactivated      => new Notification.Deactivated
    case _: SpiNotification.IterationStarted => new Notification.IterationStarted
    case c: SpiNotification.IterationCompleted =>
      new Notification.IterationCompleted(new AutonomousAgent.TokenUsage(c.inputTokens, c.outputTokens))
    case f: SpiNotification.IterationFailed =>
      new Notification.IterationFailed(f.reason, f.taskId.toJava, f.iterationNumber.map(Integer.valueOf).toJava)
    case p: SpiNotification.Suspended => new Notification.Suspended(p.reason)
    case r: SpiNotification.Resumed   => new Notification.Resumed(r.reason)
    case s: SpiNotification.Stopped   => new Notification.Stopped(s.reason)

    // Task
    case t: SpiNotification.TaskAssigned  => new Notification.TaskAssigned(t.taskId)
    case t: SpiNotification.TaskStarted   => new Notification.TaskStarted(t.taskKey.id, t.taskKey.name)
    case t: SpiNotification.TaskCompleted => new Notification.TaskCompleted(t.taskKey.id, t.taskKey.name)
    case t: SpiNotification.TaskResultRejected =>
      new Notification.TaskResultRejected(t.taskKey.id, t.taskKey.name, t.reason)
    case t: SpiNotification.TaskFailed =>
      new Notification.TaskFailed(t.taskKey.id, t.taskKey.name, t.reason)
    case t: SpiNotification.TaskCancelled =>
      new Notification.TaskCancelled(t.taskKey.id, t.taskKey.name, t.reason)
    case t: SpiNotification.TaskDependencyWait =>
      new Notification.TaskDependencyWait(t.taskId, t.pendingDependencyTaskIds.asJava)
    case d: SpiNotification.DependencyResolved =>
      new Notification.DependencyResolved(d.taskId, d.dependencyTaskId, d.success, d.reason)

    // Handoff
    case h: SpiNotification.HandoffStarted =>
      new Notification.HandoffStarted(h.taskKey.id, h.taskKey.name, h.target.componentId, h.target.instanceId)
    case h: SpiNotification.HandoffReceived =>
      new Notification.HandoffReceived(h.taskId, h.source.componentId, h.source.instanceId)

    // Delegation
    case d: SpiNotification.DelegationStarted =>
      new Notification.DelegationStarted(
        d.workerComponentIds.asJava,
        d.delegationCount,
        d.subtaskIds.asJava,
        d.workerInstanceIds.asJava)
    case d: SpiNotification.DelegationResolved =>
      new Notification.DelegationResolved(
        d.succeeded,
        d.failed,
        d.succeededSubtaskIds.asJava,
        d.failedSubtaskIds.asJava)
    case w: SpiNotification.WorkerTaskReceived =>
      new Notification.WorkerTaskReceived(w.subtaskId, w.orchestrator.componentId, w.orchestrator.instanceId)
    case w: SpiNotification.WorkerTaskCompleted =>
      new Notification.WorkerTaskCompleted(w.subtaskId)

    // Team
    case t: SpiNotification.TeamCreated =>
      new Notification.TeamCreated(t.teamId, t.members.map(_.componentId).asJava, t.members.map(_.instanceId).asJava)
    case t: SpiNotification.TeamMemberReady =>
      new Notification.TeamMemberReady(t.teamId, t.member.componentId, t.member.instanceId)
    case t: SpiNotification.TeamMemberSetupFailed =>
      new Notification.TeamMemberSetupFailed(t.teamId, t.member.componentId, t.member.instanceId, t.reason)
    case t: SpiNotification.TeamMemberStopped =>
      new Notification.TeamMemberStopped(t.teamId, t.member.componentId, t.member.instanceId)
    case t: SpiNotification.TeamDisbanded =>
      new Notification.TeamDisbanded(t.teamId)
    case t: SpiNotification.TeamJoined =>
      new Notification.TeamJoined(t.lead.componentId, t.lead.instanceId)

    // Backlog
    case b: SpiNotification.BacklogAssigned =>
      new Notification.BacklogAssigned(b.backlogId, b.backlogName)
    case b: SpiNotification.BacklogClosed =>
      new Notification.BacklogClosed(b.backlogId, b.backlogName)
    case b: SpiNotification.BacklogAccessGranted =>
      new Notification.BacklogAccessGranted(b.backlogId, b.backlogName)
    case b: SpiNotification.BacklogTaskClaimed =>
      new Notification.BacklogTaskClaimed(b.backlogId, b.backlogName, b.taskId)

    // Conversation
    case c: SpiNotification.ConversationCreated =>
      new Notification.ConversationCreated(
        c.conversationId,
        c.pattern,
        c.topic,
        c.participants.map(_.componentId).asJava,
        c.participants.map(_.instanceId).asJava)
    case c: SpiNotification.ConversationParticipantReady =>
      new Notification.ConversationParticipantReady(
        c.conversationId,
        c.participant.componentId,
        c.participant.instanceId)
    case c: SpiNotification.ConversationParticipantSetupFailed =>
      new Notification.ConversationParticipantSetupFailed(
        c.conversationId,
        c.participant.componentId,
        c.participant.instanceId,
        c.reason)
    case c: SpiNotification.ConversationEnded =>
      new Notification.ConversationEnded(c.conversationId)
    case c: SpiNotification.ConversationTurnReceived =>
      new Notification.ConversationTurnReceived(c.conversationId, c.participant.componentId, c.participant.instanceId)
    case c: SpiNotification.ConversationJoined =>
      new Notification.ConversationJoined(c.conversationId, c.moderator.componentId, c.moderator.instanceId)
    case c: SpiNotification.ParticipantTurnSubmitted =>
      new Notification.ParticipantTurnSubmitted(c.conversationId, c.iterationsUsed)

    // Messaging
    case m: SpiNotification.MessageReceived =>
      new Notification.MessageReceived(m.from.componentId, m.from.instanceId, m.text)
    case c: SpiNotification.ContactAdded =>
      new Notification.ContactAdded(c.contact.componentId, c.contact.instanceId)

    // Struggle signals
    case s: SpiNotification.TaskStruggleDetected =>
      new Notification.TaskStruggleDetected(s.taskKey.id, s.taskKey.name, s.reason, s.iteration, s.maxIterations)
    case s: SpiNotification.TaskDependencyStuck =>
      new Notification.TaskDependencyStuck(s.taskId, s.pendingDependencyTaskIds.asJava, s.waitDurationSeconds)
    case s: SpiNotification.TaskApproachingMaxIterations =>
      new Notification.TaskApproachingMaxIterations(s.taskKey.id, s.taskKey.name, s.iteration, s.maxIterations)
    case s: SpiNotification.RepeatedIterationFailure =>
      new Notification.RepeatedIterationFailure(s.iterationsFailed, s.lastReason)

    // ignore unknown because the runtime should be able to add new notification events without breaking old SDK
  }
}
