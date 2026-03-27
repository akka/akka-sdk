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
import akka.javasdk.agent.MemoryProvider
import akka.javasdk.agent.SessionMessage
import akka.javasdk.agent.SessionMessage.AiMessage
import akka.javasdk.agent.SessionMessage.MultimodalUserMessage
import akka.javasdk.agent.SessionMessage.TokenUsage
import akka.javasdk.agent.SessionMessage.ToolCallRequest
import akka.javasdk.agent.SessionMessage.ToolCallResponse
import akka.javasdk.agent.SessionMessage.UserMessage
import akka.javasdk.agent._
import akka.javasdk.agent.task.BacklogEntity
import akka.javasdk.agent.task.BacklogState
import akka.javasdk.agent.task.TaskAttachment
import akka.javasdk.agent.task.TaskEntity
import akka.javasdk.agent.task.TaskStatus
import akka.javasdk.client.ComponentClient
import akka.javasdk.impl.JsonSchema
import akka.javasdk.impl.agent.SessionMemoryClient.MemorySettings
import akka.javasdk.impl.agent.autonomous.AgentDefinitionImpl
import akka.javasdk.impl.client.EntityClientImpl
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAutonomousAgent
import akka.runtime.sdk.spi.SpiBacklog
import akka.runtime.sdk.spi.SpiBacklogOperations
import akka.runtime.sdk.spi.SpiTask
import akka.runtime.sdk.spi.SpiTaskOperations
import com.typesafe.config.Config
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.{ Context => OtelContext }
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

  private lazy val sessionMemoryClient: SessionMemory =
    deriveMemoryClient(agentDefinition.memoryProvider, telemetryContext = None)

  private def deriveMemoryClient(
      memoryProvider: MemoryProvider,
      telemetryContext: Option[OtelContext]): SessionMemory = {
    memoryProvider match {
      case _: MemoryProvider.Disabled =>
        new SessionMemoryClient(componentClient(telemetryContext), MemorySettings.disabled())

      case p: MemoryProvider.LimitedWindowMemoryProvider =>
        new SessionMemoryClient(
          componentClient(telemetryContext),
          new MemorySettings(p.read(), p.write(), p.readLastN(), p.filters()))

      case p: MemoryProvider.CustomMemoryProvider =>
        p.sessionMemory()

      case p: MemoryProvider.FromConfig =>
        val actualPath =
          if (p.configPath() == "")
            "akka.javasdk.agent.memory"
          else
            p.configPath()
        new SessionMemoryClient(componentClient(telemetryContext), config.getConfig(actualPath))
    }
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

  // Pre-resolve TaskEntity methods for calling via EntityClientImpl
  private val taskCreateMethod = classOf[TaskEntity].getMethod("create", classOf[TaskEntity.CreateRequest])
  private val taskGetStateMethod = classOf[TaskEntity].getMethod("getState")
  private val taskAssignMethod = classOf[TaskEntity].getMethod("assign", classOf[String])
  private val taskStartMethod = classOf[TaskEntity].getMethod("start")
  private val taskCompleteMethod = classOf[TaskEntity].getMethod("complete", classOf[String])
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
    override def createTask(request: SpiTask.CreateTaskRequest): Future[String] = {
      val taskId = UUID.randomUUID().toString
      val attachments = request.attachments.map(spiToAttachment).asJava
      val createReq = new TaskEntity.CreateRequest(
        request.name,
        request.description,
        request.instructions.orNull,
        request.resultTypeName.orNull,
        request.dependencyTaskIds.asJava,
        attachments)
      taskEntityClient(taskId)
        .methodRefOneArg[TaskEntity.CreateRequest, Done](taskCreateMethod)
        .invokeAsync(createReq)
        .asScala
        .map(_ => taskId)(sdkExecutionContext)
    }

    override def getTaskState(taskId: String): Future[SpiTask.SpiTaskState] =
      taskEntityClient(taskId)
        .methodRefNoArg[akka.javasdk.agent.task.TaskState](taskGetStateMethod)
        .invokeAsync()
        .asScala
        .map(toSpiTaskState)(sdkExecutionContext)

    override def assignTask(taskId: String, assignee: String): Future[Done] =
      taskEntityClient(taskId)
        .methodRefOneArg[String, Done](taskAssignMethod)
        .invokeAsync(assignee)
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def startTask(taskId: String): Future[Done] =
      taskEntityClient(taskId)
        .methodRefNoArg[Done](taskStartMethod)
        .invokeAsync()
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def completeTask(taskId: String, resultJson: String): Future[Done] =
      taskEntityClient(taskId)
        .methodRefOneArg[String, Done](taskCompleteMethod)
        .invokeAsync(resultJson)
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def failTask(taskId: String, reason: String): Future[Done] =
      taskEntityClient(taskId)
        .methodRefOneArg[String, Done](taskFailMethod)
        .invokeAsync(reason)
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def cancelTask(taskId: String, reason: String): Future[Done] =
      taskEntityClient(taskId)
        .methodRefOneArg[String, Done](taskCancelMethod)
        .invokeAsync(reason)
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def reassignTask(taskId: String, request: SpiTask.ReassignRequest): Future[Done] = {
      val reassignReq = new TaskEntity.ReassignRequest(request.newAgentComponentId, request.context)
      taskEntityClient(taskId)
        .methodRefOneArg[TaskEntity.ReassignRequest, Done](taskReassignMethod)
        .invokeAsync(reassignReq)
        .asScala
        .map(_ => Done)(sdkExecutionContext)
    }
  }

  override val backlogOperations: SpiBacklogOperations = new SpiBacklogOperations {
    override def createBacklog(backlogId: String, name: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[String, Done](backlogCreateMethod)
        .invokeAsync(name)
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def addTask(backlogId: String, taskId: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[String, Done](backlogAddTaskMethod)
        .invokeAsync(taskId)
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def claimTask(backlogId: String, taskId: String, claimedBy: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[BacklogEntity.ClaimRequest, Done](backlogClaimMethod)
        .invokeAsync(new BacklogEntity.ClaimRequest(taskId, claimedBy))
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def releaseTask(backlogId: String, taskId: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[String, Done](backlogReleaseMethod)
        .invokeAsync(taskId)
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def transferTask(backlogId: String, taskId: String, transferredTo: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefOneArg[BacklogEntity.TransferRequest, Done](backlogTransferMethod)
        .invokeAsync(new BacklogEntity.TransferRequest(taskId, transferredTo))
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def cancelUnclaimed(backlogId: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefNoArg[Done](backlogCancelUnclaimedMethod)
        .invokeAsync()
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def closeBacklog(backlogId: String): Future[Done] =
      backlogEntityClient(backlogId)
        .methodRefNoArg[Done](backlogCloseMethod)
        .invokeAsync()
        .asScala
        .map(_ => Done)(sdkExecutionContext)

    override def getState(backlogId: String): Future[SpiBacklog.SpiBacklogState] =
      backlogEntityClient(backlogId)
        .methodRefNoArg[BacklogState](backlogGetStateMethod)
        .invokeAsync()
        .asScala
        .map(toSpiBacklogState)(sdkExecutionContext)
  }

  override def getSessionHistory(sessionId: String): Future[Seq[SpiAgent.ContextMessage]] =
    Future {
      val history = sessionMemoryClient.getHistory(sessionId)
      toSpiContextMessages(history)
    }(sdkExecutionContext)

  override def addToSessionHistory(sessionId: String, messages: Seq[SpiAgent.ContextMessage]): Future[Done] =
    Future {
      if (messages.nonEmpty) {
        val now = Instant.now()
        messages.head match {
          case u: SpiAgent.ContextMessage.UserMessage
              if u.contents.forall(_.isInstanceOf[SpiAgent.TextMessageContent]) =>
            val text = u.contents.collect { case t: SpiAgent.TextMessageContent => t.text }.mkString(" ")
            sessionMemoryClient.addInteraction(
              sessionId,
              new UserMessage(now, text, componentId),
              toSessionMessages(now, messages.tail).asJava)
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
              toSessionMessages(now, messages.tail).asJava)
          case _ =>
            // No user message — partial interaction (e.g. tool call responses
            // completing the previous AI message's tool calls)
            sessionMemoryClient.addInteraction(
              sessionId,
              null.asInstanceOf[UserMessage],
              toSessionMessages(now, messages).asJava)
        }
      }
      Done
    }(sdkExecutionContext)

  override def callToolFunction(request: SpiAgent.ToolCallCommand): Future[String] =
    Future(toolExecutor.execute(request))(sdkExecutionContext)

  // --- Helpers ---

  private def toSpiTaskState(state: akka.javasdk.agent.task.TaskState): SpiTask.SpiTaskState = {
    val spiStatus = state.status() match {
      case TaskStatus.PENDING     => SpiTask.SpiTaskStatus.Pending
      case TaskStatus.ASSIGNED    => SpiTask.SpiTaskStatus.Assigned
      case TaskStatus.IN_PROGRESS => SpiTask.SpiTaskStatus.InProgress
      case TaskStatus.COMPLETED   => SpiTask.SpiTaskStatus.Completed
      case TaskStatus.FAILED      => SpiTask.SpiTaskStatus.Failed
      case TaskStatus.CANCELLED   => SpiTask.SpiTaskStatus.Cancelled
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

  private def toSessionMessages(now: Instant, messages: Seq[SpiAgent.ContextMessage]): Seq[SessionMessage] =
    messages.collect {
      case m: SpiAgent.ContextMessage.AiMessage =>
        val toolCallRequests = m.toolRequests.map { req =>
          new ToolCallRequest(req.id, req.name, req.arguments)
        }.asJava
        new AiMessage(
          now,
          m.content,
          componentId,
          toolCallRequests,
          m.thinking.toJava,
          TokenUsage.EMPTY,
          m.attributes.asJava)
      case m: SpiAgent.ContextMessage.ToolCallResponseMessage =>
        new ToolCallResponse(now, componentId, m.id, m.name, m.content)
    }
}
