/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Instant
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters.RichOption

import akka.Done
import akka.actor.typed.ActorSystem
import akka.annotation.InternalApi
import akka.javasdk.DependencyProvider
import akka.javasdk.Metadata
import akka.javasdk.agent.SessionMessage
import akka.javasdk.agent.SessionMessage.AiMessage
import akka.javasdk.agent.SessionMessage.MultimodalUserMessage
import akka.javasdk.agent.SessionMessage.TokenUsage
import akka.javasdk.agent.SessionMessage.ToolCallRequest
import akka.javasdk.agent.SessionMessage.ToolCallResponse
import akka.javasdk.agent.SessionMessage.UserMessage
import akka.javasdk.agent._
import akka.javasdk.agent.task.BacklogEntity
import akka.javasdk.agent.task.BacklogNotification
import akka.javasdk.agent.task.BacklogState
import akka.javasdk.agent.task.TaskAttachment
import akka.javasdk.agent.task.TaskEntity
import akka.javasdk.agent.task.TaskNotification
import akka.javasdk.agent.task.TaskState
import akka.javasdk.agent.task.TaskStatus
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.agent.autonomous.AgentDefinitionImpl
import akka.javasdk.impl.client.EntityClientImpl
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAutonomousAgent
import akka.runtime.sdk.spi.SpiBacklog
import akka.runtime.sdk.spi.SpiBacklogOperations
import akka.runtime.sdk.spi.SpiTask
import akka.runtime.sdk.spi.SpiTaskOperations
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
import io.opentelemetry.context.{ Context => TelemetryContext }
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[impl] final class AutonomousAgentImpl(
    componentId: String,
    instanceId: String,
    factory: AgentContext => AnyRef,
    sdkExecutionContext: ExecutionContext,
    tracerFactory: () => Tracer,
    serializer: Serializer,
    regionInfo: RegionInfo,
    componentClient: Option[OtelContext] => ComponentClient,
    dependencyProvider: Option[DependencyProvider],
    config: Config,
    _system: ActorSystem[_],
    agentDefinition: AgentDefinitionImpl,
    override val goal: String,
    override val modelProvider: SpiAgent.ModelProvider,
    override val toolDescriptors: Seq[SpiAgent.ToolDescriptor],
    override val mcpClientDescriptors: Seq[SpiAgent.McpToolEndpointDescriptor],
    override val requestGuardrails: Seq[SpiAgent.Guardrail],
    override val responseGuardrails: Seq[SpiAgent.Guardrail],
    override val capabilities: Seq[SpiAutonomousAgent.Capability])
    extends SpiAutonomousAgent {
  import AgentImpl._

  implicit val system: ActorSystem[_] = _system

  private val log = LoggerFactory.getLogger(classOf[AutonomousAgentImpl])

  private def deriveMemoryClient(telemetryContext: Option[OtelContext]): SessionMemory = {
    // always enabled
    val memoryConfig = ConfigFactory
      .parseString("enabled=true")
      .withFallback(config.getConfig("akka.javasdk.agent.memory"))
    new SessionMemoryClient(componentClient(telemetryContext), memoryConfig)
  }

  private lazy val toolExecutor: ToolExecutor = {
    val agentContext =
      new AgentContextImpl(instanceId, regionInfo.selfRegion, Metadata.EMPTY, telemetryContext = None, tracerFactory)
    val agentInstance = factory(agentContext)

    // Agent's own @FunctionTool methods
    val agentInvokers = FunctionTools.toolInvokersFor(agentInstance)

    // Definition's additional tool instances or classes
    val definitionToolInvokers: Map[String, FunctionTools.FunctionToolInvoker] =
      agentDefinition.toolInstancesOrClasses.asScala.flatMap {
        case cls: Class[_] if Reflect.isToolCandidate(cls) =>
          FunctionTools.toolComponentInvokersFor(cls, componentClient(None))
        case cls: Class[_] =>
          FunctionTools.toolInvokersFor(cls, dependencyProvider)
        case any =>
          FunctionTools.toolInvokersFor(any)
      }.toMap

    new ToolExecutor(agentInvokers ++ definitionToolInvokers, serializer)
  }

  private val taskRuleRunner = new TaskRuleRunner(system.classicSystem, serializer)

  // Pre-resolve TaskEntity methods for calling via EntityClientImpl
  private val taskCreateMethod = classOf[TaskEntity].getMethod("create", classOf[TaskEntity.CreateRequest])
  private val taskGetStateMethod = classOf[TaskEntity].getMethod("getState")
  private val taskAssignMethod = classOf[TaskEntity].getMethod("assign", classOf[String])
  private val taskStartMethod = classOf[TaskEntity].getMethod("start")
  private val taskCompleteMethod = classOf[TaskEntity].getMethod("complete", classOf[String])
  private val taskRejectResultMethod =
    classOf[TaskEntity].getMethod("rejectResult", classOf[TaskEntity.RejectResultRequest])
  private val taskFailMethod = classOf[TaskEntity].getMethod("fail", classOf[String])
  private val taskCancelMethod = classOf[TaskEntity].getMethod("cancel", classOf[String])
  private val taskReassignMethod = classOf[TaskEntity].getMethod("reassign", classOf[TaskEntity.ReassignRequest])

  private def taskEntityClient(taskId: String): EntityClientImpl =
    componentClient(None).forEventSourcedEntity(taskId).asInstanceOf[EntityClientImpl]

  // Pre-resolve BacklogEntity methods for calling via EntityClientImpl
  private val backlogCreateMethod = classOf[BacklogEntity].getMethod("create", classOf[String])
  private val backlogAddTaskMethod = classOf[BacklogEntity].getMethod("addTask", classOf[String])
  private val backlogClaimMethod = classOf[BacklogEntity].getMethod("claim", classOf[BacklogEntity.ClaimRequest])
  private val backlogReleaseMethod = classOf[BacklogEntity].getMethod("release", classOf[String])
  private val backlogTransferMethod =
    classOf[BacklogEntity].getMethod("transfer", classOf[BacklogEntity.TransferRequest])
  private val backlogCancelUnclaimedMethod = classOf[BacklogEntity].getMethod("cancelUnclaimed")
  private val backlogCloseMethod = classOf[BacklogEntity].getMethod("close")
  private val backlogGetStateMethod = classOf[BacklogEntity].getMethod("getState")

  private def backlogEntityClient(backlogId: String): EntityClientImpl =
    componentClient(None).forEventSourcedEntity(backlogId).asInstanceOf[EntityClientImpl]

  // --- SpiAutonomousAgent ---

  override val taskOperations: SpiTaskOperations = new SpiTaskOperations {
    override def createTask(request: SpiTask.CreateTaskRequest, context: Option[TelemetryContext]): Future[String] = {
      val taskId = UUID.randomUUID().toString
      val attachments = request.attachments.map(spiToAttachment).asJava
      val createReq = new TaskEntity.CreateRequest(
        request.name,
        request.description,
        request.instructions.orNull,
        request.resultTypeName.orNull,
        request.dependencyTaskIds.asJava,
        attachments,
        request.ruleClassNames.asJava)
      taskEntityClient(taskId)
        .methodRefOneArg[TaskEntity.CreateRequest, Done](taskCreateMethod)
        .withMetadata(MetadataImpl.of(context))
        .invokeAsync(createReq)
        .asScala
        .map(_ => taskId)(sdkExecutionContext)
    }

    override def getTaskState(taskId: String, context: Option[TelemetryContext]): Future[SpiTask.SpiTaskState] =
      taskEntityClient(taskId)
        .methodRefNoArg[TaskState](taskGetStateMethod)
        .withMetadata(MetadataImpl.of(context))
        .invokeAsync()
        .asScala
        .map(toSpiTaskState)(sdkExecutionContext)

    override def assignTask(taskId: String, assignee: String, context: Option[TelemetryContext]): Future[Done] =
      taskEntityClient(taskId)
        .methodRefOneArg[String, Done](taskAssignMethod)
        .withMetadata(MetadataImpl.of(context))
        .invokeAsync(assignee)
        .asScala

    override def startTask(taskId: String, context: Option[TelemetryContext]): Future[Done] =
      taskEntityClient(taskId)
        .methodRefNoArg[Done](taskStartMethod)
        .withMetadata(MetadataImpl.of(context))
        .invokeAsync()
        .asScala

    override def completeTask(taskId: String, resultJson: String, context: Option[TelemetryContext]): Future[Done] =
      taskEntityClient(taskId)
        .methodRefNoArg[TaskState](taskGetStateMethod)
        .withMetadata(MetadataImpl.of(context))
        .invokeAsync()
        .asScala
        .flatMap { taskState =>
          taskRuleRunner.evaluate(taskState, resultJson) match {
            case TaskRuleRunner.RuleOutcome.Accepted =>
              taskEntityClient(taskId)
                .methodRefOneArg[String, Done](taskCompleteMethod)
                .withMetadata(MetadataImpl.of(context))
                .invokeAsync(resultJson)
                .asScala

            case TaskRuleRunner.RuleOutcome.Rejected(ruleClassName, reason) =>
              log.warn(
                "Task [{}] [{}] completion rejected by rule [{}]: {}",
                taskState.name(),
                taskId,
                ruleClassName,
                reason)
              val rejectRequest = new TaskEntity.RejectResultRequest(ruleClassName, reason)
              taskEntityClient(taskId)
                .methodRefOneArg[TaskEntity.RejectResultRequest, Done](taskRejectResultMethod)
                .withMetadata(MetadataImpl.of(context))
                .invokeAsync(rejectRequest)
                .asScala
                .flatMap(_ => Future.failed(new SpiTask.TaskResultRejectedException(reason)))(sdkExecutionContext)
          }
        }(sdkExecutionContext)

    override def failTask(taskId: String, reason: String, context: Option[TelemetryContext]): Future[Done] =
      taskEntityClient(taskId)
        .methodRefOneArg[String, Done](taskFailMethod)
        .withMetadata(MetadataImpl.of(context))
        .invokeAsync(reason)
        .asScala

    override def cancelTask(taskId: String, reason: String, context: Option[TelemetryContext]): Future[Done] =
      taskEntityClient(taskId)
        .methodRefOneArg[String, Done](taskCancelMethod)
        .withMetadata(MetadataImpl.of(context))
        .invokeAsync(reason)
        .asScala

    override def reassignTask(
        taskId: String,
        request: SpiTask.ReassignRequest,
        context: Option[TelemetryContext]): Future[Done] = {
      val reassignReq = new TaskEntity.ReassignRequest(request.newAgentComponentId, request.context)
      taskEntityClient(taskId)
        .methodRefOneArg[TaskEntity.ReassignRequest, Done](taskReassignMethod)
        .withMetadata(MetadataImpl.of(context))
        .invokeAsync(reassignReq)
        .asScala
    }

    override val taskEntityType: String = Reflect.readComponentId(classOf[TaskEntity])

    override def decodeNotification(payload: BytesPayload): SpiTask.SpiTaskNotification =
      toSpiTaskNotification(serializer.fromBytes(payload).asInstanceOf[TaskNotification])
  }

  override val backlogOperations: SpiBacklogOperations = new SpiBacklogOperations {
    override def createBacklog(backlogId: String, name: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[String, Done](backlogCreateMethod)
        .invokeAsync(name)
        .asScala

    override def addTask(backlogId: String, taskId: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[String, Done](backlogAddTaskMethod)
        .invokeAsync(taskId)
        .asScala

    override def claimTask(backlogId: String, taskId: String, claimedBy: String): Future[Done] = {
      backlogEntityClient(backlogId)
        .methodRefOneArg[BacklogEntity.ClaimRequest, Done](backlogClaimMethod)
        .invokeAsync(new BacklogEntity.ClaimRequest(taskId, claimedBy))
        .asScala
    }

    override def releaseTask(backlogId: String, taskId: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[String, Done](backlogReleaseMethod)
        .invokeAsync(taskId)
        .asScala

    override def transferTask(backlogId: String, taskId: String, transferredTo: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[BacklogEntity.TransferRequest, Done](backlogTransferMethod)
        .invokeAsync(new BacklogEntity.TransferRequest(taskId, transferredTo))
        .asScala

    override def cancelUnclaimed(backlogId: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefNoArg[Done](backlogCancelUnclaimedMethod)
        .invokeAsync()
        .asScala

    override def closeBacklog(backlogId: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefNoArg[Done](backlogCloseMethod)
        .invokeAsync()
        .asScala

    override def getState(backlogId: String): Future[SpiBacklog.SpiBacklogState] =
      backlogEntityClient(backlogId)
        .methodRefNoArg[BacklogState](backlogGetStateMethod)
        .invokeAsync()
        .asScala
        .map(toSpiBacklogState)(sdkExecutionContext)

    override val backlogEntityType: String = Reflect.readComponentId(classOf[BacklogEntity])

    override def decodeNotification(payload: BytesPayload): SpiBacklog.SpiBacklogNotification =
      toSpiBacklogNotification(serializer.fromBytes(payload).asInstanceOf[BacklogNotification])
  }

  override def getSessionHistory(
      sessionId: String,
      context: Option[TelemetryContext]): Future[Seq[SpiAgent.ContextMessage]] =
    Future {
      val history = deriveMemoryClient(context).getHistory(sessionId)
      toSpiContextMessages(history)
    }(sdkExecutionContext)

  override def addToSessionHistory(
      sessionId: String,
      messages: Seq[SpiAgent.ContextMessage],
      inputTokens: Int,
      outputTokens: Int,
      context: Option[TelemetryContext]): Future[Done] =
    Future {
      if (messages.nonEmpty) {
        val now = Instant.now()
        val tokenUsage = new TokenUsage(inputTokens, outputTokens)
        val sessionMemoryClient = deriveMemoryClient(context)
        messages.head match {
          case u: SpiAgent.ContextMessage.UserMessage
              if u.contents.forall(_.isInstanceOf[SpiAgent.TextMessageContent]) =>
            val text = u.contents.collect { case t: SpiAgent.TextMessageContent => t.text }.mkString(" ")
            sessionMemoryClient.addInteraction(
              sessionId,
              new UserMessage(now, text, componentId),
              toSessionMessages(now, messages.tail, tokenUsage).asJava)
          case u: SpiAgent.ContextMessage.UserMessage =>
            val contents = u.contents.map {
              case t: SpiAgent.TextMessageContent =>
                new SessionMessage.MessageContent.TextMessageContent(t.text): SessionMessage.MessageContent
              case img: SpiAgent.ImageUriMessageContent =>
                new SessionMessage.MessageContent.ImageUriMessageContent(
                  img.uri.toString,
                  fromSpiDetailLevel(img.detailLevel),
                  img.mimeType.toJava): SessionMessage.MessageContent
              case pdf: SpiAgent.PdfUriMessageContent =>
                new SessionMessage.MessageContent.PdfUriMessageContent(pdf.uri.toString): SessionMessage.MessageContent
            }.asJava
            sessionMemoryClient.addInteraction(
              sessionId,
              new MultimodalUserMessage(now, contents, componentId),
              toSessionMessages(now, messages.tail, tokenUsage).asJava)
          case _ =>
            // No user message — partial interaction (e.g. tool call responses
            // completing the previous AI message's tool calls)
            sessionMemoryClient.addInteraction(
              sessionId,
              null.asInstanceOf[UserMessage],
              toSessionMessages(now, messages, tokenUsage).asJava)
        }
      }
      Done
    }(sdkExecutionContext)

  override def callToolFunction(request: SpiAgent.ToolCallCommand): Future[String] =
    Future(toolExecutor.execute(request))(sdkExecutionContext)

  // --- Helpers ---

  private def toSpiTaskState(state: akka.javasdk.agent.task.TaskState): SpiTask.SpiTaskState = {
    val spiStatus = state.status() match {
      case TaskStatus.PENDING         => SpiTask.SpiTaskStatus.Pending
      case TaskStatus.ASSIGNED        => SpiTask.SpiTaskStatus.Assigned
      case TaskStatus.IN_PROGRESS     => SpiTask.SpiTaskStatus.InProgress
      case TaskStatus.RESULT_REJECTED => SpiTask.SpiTaskStatus.ResultRejected
      case TaskStatus.COMPLETED       => SpiTask.SpiTaskStatus.Completed
      case TaskStatus.FAILED          => SpiTask.SpiTaskStatus.Failed
      case TaskStatus.CANCELLED       => SpiTask.SpiTaskStatus.Cancelled
    }
    val resultTypeName = Option(state.resultTypeName())
    val resultSchema = resultTypeName
      .filterNot(_ == classOf[String].getName)
      .flatMap { typeName =>
        try Some(JsonSchema.jsonSchemaFor(Class.forName(typeName)))
        catch {
          case scala.util.control.NonFatal(e) =>
            log.debug("Could not generate schema for result type [{}]: {}", typeName, e.getMessage)
            None
        }
      }
    val attachments = Option(state.attachments())
      .map(_.asScala.toSeq.map(attachmentToSpi))
      .getOrElse(Seq.empty)
    new SpiTask.SpiTaskState(
      taskId = state.taskId(),
      name = state.name(),
      description = state.description(),
      instructions = Option(state.instructions()).filter(_.nonEmpty),
      status = spiStatus,
      resultTypeName = resultTypeName,
      resultSchema = resultSchema,
      resultJson = Option(state.result()),
      failureReason = Option(state.failureReason()),
      dependencyTaskIds = state.dependencyTaskIds().asScala.toSeq,
      attachments = attachments,
      reassignmentContext = Option(state.reassignmentContext()).map(_.asScala.toSeq).getOrElse(Seq.empty))
  }

  private def toSpiTaskNotification(notification: TaskNotification): SpiTask.SpiTaskNotification =
    notification match {
      case c: TaskNotification.Completed =>
        new SpiTask.SpiTaskNotification.StatusChanged(c.taskId(), c.taskName(), SpiTask.SpiTaskStatus.Completed, "")
      case r: TaskNotification.ResultRejected =>
        new SpiTask.SpiTaskNotification.StatusChanged(
          r.taskId(),
          r.taskName(),
          SpiTask.SpiTaskStatus.ResultRejected,
          r.reason())
      case f: TaskNotification.Failed =>
        new SpiTask.SpiTaskNotification.StatusChanged(
          f.taskId(),
          f.taskName(),
          SpiTask.SpiTaskStatus.Failed,
          f.reason())
      case c: TaskNotification.Cancelled =>
        new SpiTask.SpiTaskNotification.StatusChanged(
          c.taskId(),
          c.taskName(),
          SpiTask.SpiTaskStatus.Cancelled,
          c.reason())
    }

  private def toSpiBacklogNotification(notification: BacklogNotification): SpiBacklog.SpiBacklogNotification =
    notification match {
      case c: BacklogNotification.BacklogCreated =>
        new SpiBacklog.SpiBacklogNotification.BacklogCreated(c.name())
      case a: BacklogNotification.TaskAdded =>
        new SpiBacklog.SpiBacklogNotification.TaskAdded(a.taskId())
      case c: BacklogNotification.TaskClaimed =>
        new SpiBacklog.SpiBacklogNotification.TaskClaimed(c.taskId(), c.claimedBy())
      case r: BacklogNotification.TaskReleased =>
        new SpiBacklog.SpiBacklogNotification.TaskReleased(r.taskId())
      case t: BacklogNotification.TaskTransferred =>
        new SpiBacklog.SpiBacklogNotification.TaskTransferred(t.taskId(), t.transferredTo())
      case _: BacklogNotification.UnclaimedCancelled =>
        SpiBacklog.SpiBacklogNotification.UnclaimedCancelled
      case _: BacklogNotification.BacklogClosed =>
        SpiBacklog.SpiBacklogNotification.BacklogClosed
    }

  private def toSpiBacklogState(state: BacklogState): SpiBacklog.SpiBacklogState = {
    import scala.jdk.CollectionConverters._
    val entries = state.entries().asScala.toSeq.map { entry =>
      new SpiBacklog.SpiBacklogEntry(
        taskId = entry.taskId(),
        claimedBy = if (entry.claimedBy().isPresent) Some(entry.claimedBy().get()) else None)
    }
    new SpiBacklog.SpiBacklogState(state.name(), entries, state.closed())
  }

  private def attachmentToSpi(ref: TaskAttachment): SpiAgent.MessageContent =
    AgentImpl.toSpiMessageContent(ref.toMessageContent())

  private def spiToAttachment(mc: SpiAgent.MessageContent): TaskAttachment =
    TaskAttachment.fromMessageContent(AgentImpl.fromSpiMessageContent(mc))

  private def toSessionMessages(
      now: Instant,
      messages: Seq[SpiAgent.ContextMessage],
      tokenUsage: TokenUsage): Seq[SessionMessage] =
    messages.collect {
      case m: SpiAgent.ContextMessage.AiMessage =>
        val toolCallRequests = m.toolRequests.map { req =>
          new ToolCallRequest(req.id, req.name, req.arguments)
        }.asJava
        new AiMessage(now, m.content, componentId, toolCallRequests, m.thinking.toJava, tokenUsage, m.attributes.asJava)
      case m: SpiAgent.ContextMessage.ToolCallResponseMessage =>
        new ToolCallResponse(now, componentId, m.id, m.name, m.content)
    }
}
