/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.ledger

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.Optional

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

import akka.javasdk.agent.MessageContent
import akka.javasdk.ledger.ActivityRecord
import akka.javasdk.ledger.EvaluationRecord
import akka.javasdk.ledger.Failure
import akka.javasdk.ledger.GuardrailDecisionRecord
import akka.javasdk.ledger.InteractionMetadata
import akka.javasdk.ledger.InteractionRecord
import akka.javasdk.ledger.ToolCall
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAutonomousAgent
import akka.runtime.sdk.spi.SpiLedger
import akka.runtime.sdk.spi.{ LedgerClient => SpiLedgerClient }
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

class LedgerClientUnknownValuesSpec extends AnyWordSpec with Matchers with TableDrivenPropertyChecks {

  private val failureReasons = Table[SpiLedger.FailureReason, Failure.FailureReason](
    ("spi", "public"),
    (SpiLedger.FailureReason.Unspecified, Failure.FailureReason.UNSPECIFIED),
    (SpiLedger.FailureReason.Model, Failure.FailureReason.MODEL),
    (SpiLedger.FailureReason.RateLimit, Failure.FailureReason.RATE_LIMIT),
    (SpiLedger.FailureReason.Timeout, Failure.FailureReason.TIMEOUT),
    (SpiLedger.FailureReason.UnsupportedFeature, Failure.FailureReason.UNSUPPORTED_FEATURE),
    (SpiLedger.FailureReason.Internal, Failure.FailureReason.INTERNAL),
    (SpiLedger.FailureReason.OutputParsing, Failure.FailureReason.OUTPUT_PARSING),
    (SpiLedger.FailureReason.ToolCall, Failure.FailureReason.TOOL_CALL),
    (SpiLedger.FailureReason.McpToolCall, Failure.FailureReason.MCP_TOOL_CALL),
    (SpiLedger.FailureReason.Guardrail, Failure.FailureReason.GUARDRAIL),
    (SpiLedger.FailureReason.ContentLoading, Failure.FailureReason.CONTENT_LOADING),
    (SpiLedger.FailureReason.Sanitizer, Failure.FailureReason.SANITIZER),
    (SpiLedger.FailureReason.Cancelled, Failure.FailureReason.CANCELLED))

  private val finishReasons = Table[SpiLedger.FinishReason, InteractionMetadata.FinishReason](
    ("spi", "public"),
    (SpiLedger.FinishReason.Unspecified, InteractionMetadata.FinishReason.UNSPECIFIED),
    (SpiLedger.FinishReason.Stop, InteractionMetadata.FinishReason.STOP),
    (SpiLedger.FinishReason.Length, InteractionMetadata.FinishReason.LENGTH))

  private val triggers = Table[SpiLedger.EvaluationTrigger, EvaluationRecord.Trigger](
    ("spi", "public"),
    (SpiLedger.EvaluationTrigger.Unspecified, EvaluationRecord.Trigger.UNSPECIFIED),
    (SpiLedger.EvaluationTrigger.Manual, EvaluationRecord.Trigger.MANUAL),
    (SpiLedger.EvaluationTrigger.OnInteraction, EvaluationRecord.Trigger.ON_INTERACTION))

  private val boundaries = Table[SpiLedger.GuardrailBoundary, GuardrailDecisionRecord.Boundary](
    ("spi", "public"),
    (SpiLedger.GuardrailBoundary.Unspecified, GuardrailDecisionRecord.Boundary.UNSPECIFIED),
    (SpiLedger.GuardrailBoundary.BeforeModelCall, GuardrailDecisionRecord.Boundary.BEFORE_MODEL_CALL),
    (SpiLedger.GuardrailBoundary.BeforeAgentResponse, GuardrailDecisionRecord.Boundary.BEFORE_AGENT_RESPONSE),
    (SpiLedger.GuardrailBoundary.BeforeToolCall, GuardrailDecisionRecord.Boundary.BEFORE_TOOL_CALL))

  private val decisions = Table[SpiLedger.GuardrailDecision, GuardrailDecisionRecord.Decision](
    ("spi", "public"),
    (new SpiLedger.GuardrailDecision.Unspecified(""), GuardrailDecisionRecord.Decision.UNSPECIFIED),
    (new SpiLedger.GuardrailDecision.Allow(""), GuardrailDecisionRecord.Decision.ALLOW),
    (new SpiLedger.GuardrailDecision.Deny(""), GuardrailDecisionRecord.Decision.DENY),
    (new SpiLedger.GuardrailDecision.Fail(""), GuardrailDecisionRecord.Decision.FAIL))

  private val failModes = Table[SpiLedger.GuardrailFailMode, GuardrailDecisionRecord.FailMode](
    ("spi", "public"),
    (SpiLedger.GuardrailFailMode.Unspecified, GuardrailDecisionRecord.FailMode.UNSPECIFIED),
    (SpiLedger.GuardrailFailMode.FailClosed, GuardrailDecisionRecord.FailMode.FAIL_CLOSED),
    (SpiLedger.GuardrailFailMode.FailOpen, GuardrailDecisionRecord.FailMode.FAIL_OPEN))

  private val toolOrigins = Table[SpiLedger.ToolOrigin, GuardrailDecisionRecord.ToolOrigin](
    ("spi", "public"),
    (SpiLedger.ToolOrigin.Unspecified, new GuardrailDecisionRecord.ToolOrigin.Unspecified()),
    (SpiLedger.ToolOrigin.FunctionTool, new GuardrailDecisionRecord.ToolOrigin.FunctionTool()),
    (
      new SpiLedger.ToolOrigin.RemoteMcp("https://mcp.example.com"),
      new GuardrailDecisionRecord.ToolOrigin.RemoteMcp("https://mcp.example.com")))

  private val toolCallStatuses = Table[SpiLedger.ToolCallStatus, ToolCall.Status](
    ("spi", "public"),
    (SpiLedger.ToolCallStatus.Unspecified, ToolCall.Status.UNSPECIFIED),
    (SpiLedger.ToolCallStatus.Completed, ToolCall.Status.COMPLETED),
    (SpiLedger.ToolCallStatus.Failed, ToolCall.Status.FAILED),
    (SpiLedger.ToolCallStatus.NotRun, ToolCall.Status.NOT_RUN),
    (SpiLedger.ToolCallStatus.Pending, ToolCall.Status.PENDING),
    (SpiLedger.ToolCallStatus.NoResult, ToolCall.Status.NO_RESULT))

  private val outcomes = Table[SpiLedger.EvaluationOutcome, Class[_ <: EvaluationRecord.Outcome]](
    ("spi", "public"),
    (
      new SpiLedger.EvaluationVerdict(
        new SpiLedger.Evaluation(passed = true, explanation = "", score = None, label = None, attributes = Map.empty)),
      classOf[EvaluationRecord.Outcome.Verdict]),
    (new SpiLedger.EvaluationInconclusive("no verdict"), classOf[EvaluationRecord.Outcome.Inconclusive]),
    (new SpiLedger.EvaluationNotApplicable("no tool call"), classOf[EvaluationRecord.Outcome.NotApplicable]),
    (new SpiLedger.EvaluationFailure("model unavailable"), classOf[EvaluationRecord.Outcome.Failed]))

  private val messageContents = Table[SpiLedger.MessageContent, MessageContent](
    ("spi", "public"),
    (new SpiLedger.TextContent("What is the answer?"), new MessageContent.TextMessageContent("What is the answer?")),
    (
      new SpiLedger.ImageUriContent("https://example.com/chart.png", "high", Some("image/png")),
      new MessageContent.ImageUrlMessageContent(
        URI.create("https://example.com/chart.png"),
        MessageContent.ImageMessageContent.DetailLevel.HIGH,
        Optional.of("image/png"))),
    (
      new SpiLedger.PdfUriContent("https://example.com/report.pdf"),
      new MessageContent.PdfUrlMessageContent(URI.create("https://example.com/report.pdf"))))

  private val activityEvents = Table[SpiLedger.ActivityEvent, ActivityRecord.Event](
    ("spi", "public"),
    (new SpiLedger.ActivityEvent.TaskAssigned("task-1"), new ActivityRecord.Event.TaskAssigned("task-1")),
    (
      new SpiLedger.ActivityEvent.TaskStarted("task-1", "research", 5),
      new ActivityRecord.Event.TaskStarted("task-1", "research", 5)),
    (new SpiLedger.ActivityEvent.TaskCompleted("task-1", 3), new ActivityRecord.Event.TaskCompleted("task-1", 3)),
    (
      new SpiLedger.ActivityEvent.TaskFailed("task-1", "failed", 5),
      new ActivityRecord.Event.TaskFailed("task-1", "failed", 5)),
    (
      new SpiLedger.ActivityEvent.TaskCancelled("task-1", "cancelled", 0),
      new ActivityRecord.Event.TaskCancelled("task-1", "cancelled", 0)),
    (
      new SpiLedger.ActivityEvent.TaskResultRejected("task-1", "rejected", 2),
      new ActivityRecord.Event.TaskResultRejected("task-1", "rejected", 2)),
    (
      new SpiLedger.ActivityEvent.IterationFailed(Some("task-1"), Some(2), "failed"),
      new ActivityRecord.Event.IterationFailed("failed", Optional.of("task-1"), Optional.of(2))),
    (
      new SpiLedger.ActivityEvent.DelegationStarted(
        Seq(new SpiAutonomousAgent.AgentRef("worker-agent", "worker-1")),
        1,
        Seq("subtask-1")),
      new ActivityRecord.Event.DelegationStarted(
        Seq("worker-agent").asJava,
        1,
        Seq("subtask-1").asJava,
        Seq("worker-1").asJava)),
    (
      new SpiLedger.ActivityEvent.DelegationResolved(1, 0, Seq("subtask-1"), Seq.empty),
      new ActivityRecord.Event.DelegationResolved(1, 0, Seq("subtask-1").asJava, Seq.empty[String].asJava)),
    (
      new SpiLedger.ActivityEvent.HandoffStarted("task-1", "specialist-agent"),
      new ActivityRecord.Event.HandoffStarted("task-1", "specialist-agent")),
    (
      new SpiLedger.ActivityEvent.HandoffReceived(
        "task-1",
        new SpiAutonomousAgent.AgentRef("triage-agent", "triage-1")),
      new ActivityRecord.Event.HandoffReceived("task-1", "triage-agent", "triage-1")),
    (new SpiLedger.ActivityEvent.Other("worker_task_received"), new ActivityRecord.Event.Other("worker_task_received")))

  // The kind that an activity event of a newer runtime reads with.
  private val newerEventKind = "agent_paused"

  // The SPI traits are sealed, so the spec cannot extend them. Each one compiles to an interface. Its only abstract
  // method, the explanation of a decision or the kind of an activity event, reads as empty or as newerEventKind.
  private def unknownValue[T <: AnyRef](implicit tag: ClassTag[T]): T = {
    val name = s"unknown ${tag.runtimeClass.getSimpleName}"
    val handler: InvocationHandler = (proxy: AnyRef, method: Method, args: Array[AnyRef]) =>
      method.getName match {
        case "equals"      => Boolean.box(proxy eq args(0))
        case "hashCode"    => Int.box(System.identityHashCode(proxy))
        case "toString"    => name
        case "explanation" => ""
        case "kind"        => newerEventKind
        case other         => throw new UnsupportedOperationException(other)
      }
    Proxy.newProxyInstance(tag.runtimeClass.getClassLoader, Array(tag.runtimeClass), handler).asInstanceOf[T]
  }

  private def spiInteractionRecord(
      finishReason: SpiLedger.FinishReason = SpiLedger.FinishReason.Stop,
      failure: Option[SpiLedger.Failure] = None,
      userMessage: Seq[SpiLedger.MessageContent] = Seq.empty,
      modelResponses: Seq[SpiLedger.ModelResponse] = Seq.empty,
      toolCallResponses: Seq[SpiLedger.ToolCallResponse] = Seq.empty): SpiLedger.InteractionRecord =
    new SpiLedger.InteractionRecord(
      interactionId = "interaction-1",
      sessionId = "session-1",
      agentComponentId = "support-agent",
      flowId = None,
      metadata = new SpiLedger.InteractionMetadata(
        modelConfig = new SpiLedger.ModelConfig(
          providerName = "openai",
          modelName = "gpt-4o",
          baseUrl = "",
          temperature = -1.0,
          topP = -1.0,
          topK = -1,
          maxTokens = -1),
        modelConfigMap = Map.empty,
        callStartedAt = Instant.EPOCH,
        callFinishedAt = Instant.EPOCH,
        finishReason = finishReason),
      systemMessage = "",
      userMessage = userMessage,
      modelResponses = modelResponses,
      toolCallResponses = toolCallResponses,
      taskContext = None,
      failure = failure,
      timestamp = Instant.EPOCH)

  private def spiModelResponse(toolCallStatus: SpiLedger.ToolCallStatus): SpiLedger.ModelResponse =
    new SpiLedger.ModelResponse(
      id = "m1",
      content = "",
      tokenUsage = new SpiAgent.SpiTokenUsage(10, 5),
      thinking = "",
      toolCalls = Seq(
        new SpiLedger.ToolCall(
          id = "t1",
          name = "calc",
          arguments = """{"expr":"2+2"}""",
          response = "4",
          status = toolCallStatus)))

  private def spiEvaluationRecord(
      trigger: SpiLedger.EvaluationTrigger,
      evaluationId: String = "evaluation-1",
      subject: SpiLedger.EvaluationSubject = new SpiLedger.InteractionSubject("interaction-1", "support-agent"),
      outcome: SpiLedger.EvaluationOutcome = new SpiLedger.EvaluationInconclusive("no verdict"))
      : SpiLedger.EvaluationRecord =
    new SpiLedger.EvaluationRecord(
      evaluationId = evaluationId,
      evaluatorComponentId = "quality-evaluator",
      controlId = None,
      trigger = trigger,
      subject = subject,
      outcome = outcome,
      timestamp = Instant.EPOCH,
      attributes = Map.empty)

  private def spiGuardrailDecisionRecord(
      boundary: SpiLedger.GuardrailBoundary = SpiLedger.GuardrailBoundary.BeforeModelCall,
      decision: SpiLedger.GuardrailDecision = new SpiLedger.GuardrailDecision.Allow(""),
      failMode: SpiLedger.GuardrailFailMode = SpiLedger.GuardrailFailMode.FailClosed,
      toolOrigin: SpiLedger.ToolOrigin = SpiLedger.ToolOrigin.Unspecified,
      recordId: String = "guardrail-1"): SpiLedger.GuardrailDecisionRecord =
    new SpiLedger.GuardrailDecisionRecord(
      recordId = recordId,
      agentComponentId = "support-agent",
      interactionId = "interaction-1",
      sessionId = "session-1",
      flowId = None,
      name = "no-refunds",
      controlId = None,
      category = "policy",
      boundary = boundary,
      decision = decision,
      reportOnly = false,
      failMode = failMode,
      duration = Duration.ZERO,
      toolCall = Some(new SpiLedger.GuardrailToolCall(toolCallId = None, toolName = "refund", origin = toolOrigin)),
      timestamp = Instant.EPOCH)

  private def spiActivityRecord(events: SpiLedger.ActivityEvent*): SpiLedger.ActivityRecord =
    new SpiLedger.ActivityRecord(
      recordId = "activity-1",
      agentComponentId = "coordinator-agent",
      agentInstanceId = "coordinator-1",
      flowId = "flow-1",
      events = events,
      timestamp = Instant.EPOCH)

  private def ledgerClient(
      interaction: SpiLedger.InteractionRecord,
      evaluations: Seq[SpiLedger.EvaluationRecord],
      guardrailDecisions: Seq[SpiLedger.GuardrailDecisionRecord] = Seq.empty,
      flowPage: SpiLedger.FlowPage = new SpiLedger.FlowPage(Seq.empty, 0L, hasMore = false)): LedgerClientImpl = {
    val spiLedgerClient = new SpiLedgerClient {
      override def getInteraction(interactionId: String): Future[SpiLedger.InteractionRecord] =
        Future.successful(interaction)

      override def getEvaluation(evaluationId: String): Future[SpiLedger.EvaluationRecord] =
        evaluations.find(_.evaluationId == evaluationId) match {
          case Some(evaluation) => Future.successful(evaluation)
          case None             => Future.failed(new NoSuchElementException(evaluationId))
        }

      override def getClassification(classificationId: String): Future[SpiLedger.ClassificationRecord] =
        Future.failed(new NoSuchElementException(classificationId))

      override def getEvaluations(
          interactionId: String,
          from: Option[Instant],
          limit: Int): Future[Seq[SpiLedger.EvaluationRecord]] =
        Future.successful(evaluations)

      override def getWorkflowEvaluations(
          workflowComponentId: String,
          workflowId: String,
          from: Option[Instant],
          limit: Int): Future[Seq[SpiLedger.EvaluationRecord]] =
        Future.successful(evaluations)

      override def getInteractions(
          agentComponentId: String,
          sessionId: String,
          from: Option[Instant],
          limit: Int): Future[Seq[SpiLedger.InteractionRecord]] =
        Future.successful(Seq(interaction))

      override def getGuardrailDecisions(
          interactionId: String,
          from: Option[Instant],
          limit: Int): Future[Seq[SpiLedger.GuardrailDecisionRecord]] =
        Future.successful(guardrailDecisions)

      override def getFlowRecords(flowId: String, fromPosition: Long, limit: Int): Future[SpiLedger.FlowPage] =
        Future.successful(flowPage)
    }
    new LedgerClientImpl(spiLedgerClient, ExecutionContext.parasitic)
  }

  "Mapping a ledger record" should {

    "keep the mapping of every known failure reason" in {
      failureReasons.map(_._2).toSet shouldBe Failure.FailureReason.values().toSet
      forAll(failureReasons) { (spiReason, publicReason) =>
        val record =
          LedgerClientImpl.toInteractionRecord(
            spiInteractionRecord(failure = Some(new SpiLedger.Failure(spiReason, "failed"))))
        record.failure().get().reason() shouldBe publicReason
      }
    }

    "keep the mapping of every known finish reason" in {
      finishReasons.map(_._2).toSet shouldBe InteractionMetadata.FinishReason.values().toSet
      forAll(finishReasons) { (spiReason, publicReason) =>
        val record = LedgerClientImpl.toInteractionRecord(spiInteractionRecord(finishReason = spiReason))
        record.metadata().finishReason() shouldBe publicReason
      }
    }

    "keep the mapping of every known trigger" in {
      triggers.map(_._2).toSet shouldBe EvaluationRecord.Trigger.values().toSet
      forAll(triggers) { (spiTrigger, publicTrigger) =>
        val record = LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(spiTrigger))
        record.trigger() shouldBe publicTrigger
      }
    }

    "keep the mapping of every known guardrail boundary" in {
      boundaries.map(_._2).toSet shouldBe GuardrailDecisionRecord.Boundary.values().toSet
      forAll(boundaries) { (spiBoundary, publicBoundary) =>
        val record = LedgerClientImpl.toGuardrailDecisionRecord(spiGuardrailDecisionRecord(boundary = spiBoundary))
        record.boundary() shouldBe publicBoundary
      }
    }

    "keep the mapping of every known guardrail decision" in {
      decisions.map(_._2).toSet shouldBe GuardrailDecisionRecord.Decision.values().toSet
      forAll(decisions) { (spiDecision, publicDecision) =>
        val record = LedgerClientImpl.toGuardrailDecisionRecord(spiGuardrailDecisionRecord(decision = spiDecision))
        record.decision() shouldBe publicDecision
      }
    }

    "keep the mapping of every known guardrail fail mode" in {
      failModes.map(_._2).toSet shouldBe GuardrailDecisionRecord.FailMode.values().toSet
      forAll(failModes) { (spiFailMode, publicFailMode) =>
        val record = LedgerClientImpl.toGuardrailDecisionRecord(spiGuardrailDecisionRecord(failMode = spiFailMode))
        record.failMode() shouldBe publicFailMode
      }
    }

    "keep the mapping and the kind of every known activity event" in {
      activityEvents
        .map(_._2.getClass)
        .toSet shouldBe classOf[ActivityRecord.Event].getPermittedSubclasses.toSet
      forAll(activityEvents) { (spiEvent, publicEvent) =>
        val event = LedgerClientImpl.toActivityRecord(spiActivityRecord(spiEvent)).events().get(0)
        event shouldBe publicEvent
        event.kind() shouldBe spiEvent.kind
      }
    }

    "keep the mapping of every known tool origin" in {
      toolOrigins
        .map(_._2.getClass)
        .toSet shouldBe classOf[GuardrailDecisionRecord.ToolOrigin].getPermittedSubclasses.toSet
      forAll(toolOrigins) { (spiOrigin, publicOrigin) =>
        val record = LedgerClientImpl.toGuardrailDecisionRecord(spiGuardrailDecisionRecord(toolOrigin = spiOrigin))
        record.toolCall().get().origin() shouldBe publicOrigin
      }
    }

    "keep the mapping of every known tool call status" in {
      toolCallStatuses.map(_._2).toSet shouldBe ToolCall.Status.values().toSet
      forAll(toolCallStatuses) { (spiStatus, publicStatus) =>
        val record =
          LedgerClientImpl.toInteractionRecord(spiInteractionRecord(modelResponses = Seq(spiModelResponse(spiStatus))))
        record.toolCalls().get(0).status() shouldBe publicStatus
      }
    }

    "keep the mapping of every known evaluation outcome" in {
      outcomes.map(_._2).toSet[Class[_]] shouldBe classOf[EvaluationRecord.Outcome].getPermittedSubclasses.toSet
      forAll(outcomes) { (spiOutcome, publicOutcome) =>
        val record =
          LedgerClientImpl.toEvaluationRecord(
            spiEvaluationRecord(SpiLedger.EvaluationTrigger.Manual, outcome = spiOutcome))
        record.outcome().getClass shouldBe publicOutcome
      }
    }

    "keep the mapping of every known message content" in {
      forAll(messageContents) { (spiContent, publicContent) =>
        val record = LedgerClientImpl.toInteractionRecord(spiInteractionRecord(userMessage = Seq(spiContent)))
        record.inputMessage().asScala.toSeq shouldBe Seq(publicContent)
      }
    }

    "read a failure reason that the SDK does not know as unspecified" in {
      val unknown = unknownValue[SpiLedger.FailureReason]
      forAll(failureReasons) { (known, _) => unknown should not be known }

      val record = LedgerClientImpl.toInteractionRecord(
        spiInteractionRecord(failure = Some(new SpiLedger.Failure(unknown, "a reason from a newer runtime"))))

      val failure = record.failure().get()
      failure.reason() shouldBe Failure.FailureReason.UNSPECIFIED
      failure.description() shouldBe "a reason from a newer runtime"
      record shouldBe LedgerClientImpl.toInteractionRecord(
        spiInteractionRecord(failure =
          Some(new SpiLedger.Failure(SpiLedger.FailureReason.Unspecified, "a reason from a newer runtime"))))
    }

    "read a finish reason that the SDK does not know as unspecified" in {
      val unknown = unknownValue[SpiLedger.FinishReason]
      forAll(finishReasons) { (known, _) => unknown should not be known }

      val record = LedgerClientImpl.toInteractionRecord(spiInteractionRecord(finishReason = unknown))

      record.metadata().finishReason() shouldBe InteractionMetadata.FinishReason.UNSPECIFIED
      record.metadata().modelConfig().modelName() shouldBe "gpt-4o"
      record shouldBe LedgerClientImpl.toInteractionRecord(
        spiInteractionRecord(finishReason = SpiLedger.FinishReason.Unspecified))
    }

    "read a trigger that the SDK does not know as unspecified" in {
      val unknown = unknownValue[SpiLedger.EvaluationTrigger]
      forAll(triggers) { (known, _) => unknown should not be known }

      val record = LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(unknown))

      record.trigger() shouldBe EvaluationRecord.Trigger.UNSPECIFIED
      record.evaluatorComponentId() shouldBe "quality-evaluator"
      record shouldBe LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(SpiLedger.EvaluationTrigger.Unspecified))
    }

    "read a guardrail boundary, decision, fail mode and tool origin that the SDK does not know as unspecified" in {
      val unknownBoundary = unknownValue[SpiLedger.GuardrailBoundary]
      val unknownDecision = unknownValue[SpiLedger.GuardrailDecision]
      val unknownFailMode = unknownValue[SpiLedger.GuardrailFailMode]
      val unknownOrigin = unknownValue[SpiLedger.ToolOrigin]
      forAll(boundaries) { (known, _) => unknownBoundary should not be known }
      forAll(decisions) { (known, _) => unknownDecision should not be known }
      forAll(failModes) { (known, _) => unknownFailMode should not be known }
      forAll(toolOrigins) { (known, _) => unknownOrigin should not be known }

      val record = LedgerClientImpl.toGuardrailDecisionRecord(
        spiGuardrailDecisionRecord(
          boundary = unknownBoundary,
          decision = unknownDecision,
          failMode = unknownFailMode,
          toolOrigin = unknownOrigin))

      record.boundary() shouldBe GuardrailDecisionRecord.Boundary.UNSPECIFIED
      record.decision() shouldBe GuardrailDecisionRecord.Decision.UNSPECIFIED
      record.failMode() shouldBe GuardrailDecisionRecord.FailMode.UNSPECIFIED
      record.toolCall().get().origin() shouldBe new GuardrailDecisionRecord.ToolOrigin.Unspecified()
      record.name() shouldBe "no-refunds"
      record shouldBe LedgerClientImpl.toGuardrailDecisionRecord(
        spiGuardrailDecisionRecord(
          boundary = SpiLedger.GuardrailBoundary.Unspecified,
          decision = new SpiLedger.GuardrailDecision.Unspecified(""),
          failMode = SpiLedger.GuardrailFailMode.Unspecified,
          toolOrigin = SpiLedger.ToolOrigin.Unspecified))
    }

    "read a tool call status that the SDK does not know as unspecified" in {
      val unknown = unknownValue[SpiLedger.ToolCallStatus]
      forAll(toolCallStatuses) { (known, _) => unknown should not be known }

      val record =
        LedgerClientImpl.toInteractionRecord(spiInteractionRecord(modelResponses = Seq(spiModelResponse(unknown))))

      val toolCall = record.toolCalls().get(0)
      toolCall.status() shouldBe ToolCall.Status.UNSPECIFIED
      toolCall.id() shouldBe "t1"
      toolCall.name() shouldBe "calc"
      toolCall.arguments() shouldBe """{"expr":"2+2"}"""
      toolCall.response() shouldBe "4"
      record shouldBe LedgerClientImpl.toInteractionRecord(
        spiInteractionRecord(modelResponses = Seq(spiModelResponse(SpiLedger.ToolCallStatus.Unspecified))))
    }

    "read an evaluation outcome that the SDK does not know as inconclusive" in {
      val unknown = unknownValue[SpiLedger.EvaluationOutcome]
      forAll(outcomes) { (known, _) => unknown should not be known }

      val record =
        LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(SpiLedger.EvaluationTrigger.Manual, outcome = unknown))

      record.outcome() shouldBe new EvaluationRecord.Outcome.Inconclusive(
        "The outcome of the evaluation is unknown to this SDK")
    }

    "leave a message content that the SDK does not know out of the input message and the tool call responses" in {
      val unknown = unknownValue[SpiLedger.MessageContent]
      forAll(messageContents) { (known, _) => unknown should not be known }
      val text = new SpiLedger.TextContent("42")

      val record = LedgerClientImpl.toInteractionRecord(
        spiInteractionRecord(
          userMessage = Seq(unknown, text),
          toolCallResponses = Seq(new SpiLedger.ToolCallResponse("call-1", "lookup", Seq(text, unknown)))))

      val known = Seq(new MessageContent.TextMessageContent("42"))
      record.inputMessage().asScala.toSeq shouldBe known
      record.toolCallResponses().asScala.toSeq.map(_.contents().asScala.toSeq) shouldBe Seq(known)
    }

    "read an activity event that the SDK does not know as other, with the kind of the event" in {
      val unknown = unknownValue[SpiLedger.ActivityEvent]
      forAll(activityEvents) { (known, _) => unknown should not be known }

      val record = LedgerClientImpl.toActivityRecord(
        spiActivityRecord(new SpiLedger.ActivityEvent.TaskAssigned("task-1"), unknown))

      record.events().asScala.toSeq shouldBe Seq(
        new ActivityRecord.Event.TaskAssigned("task-1"),
        new ActivityRecord.Event.Other(newerEventKind))
    }
  }

  "The ledger client" should {

    "leave a flow record that the SDK does not know out of the page, and keep the next position and has-more flag" in {
      val client = ledgerClient(
        spiInteractionRecord(),
        Seq.empty,
        flowPage = new SpiLedger.FlowPage(
          Seq(
            spiInteractionRecord(),
            unknownValue[SpiLedger.FlowRecord],
            spiActivityRecord(new SpiLedger.ActivityEvent.TaskAssigned("task-1"))),
          4L,
          hasMore = true))

      val page = client.getFlowRecords("flow-1")

      page.records().asScala.map(_.getClass) shouldBe Seq(classOf[InteractionRecord], classOf[ActivityRecord])
      page.nextPosition() shouldBe 4L
      page.hasMore() shouldBe true
    }

    "keep the next position of a page whose records the SDK all leaves out" in {
      val client = ledgerClient(
        spiInteractionRecord(),
        Seq.empty,
        flowPage = new SpiLedger.FlowPage(Seq(unknownValue[SpiLedger.FlowRecord]), 3L, hasMore = true))

      val page = client.getFlowRecords("flow-1", 2L)

      page.records() shouldBe empty
      page.nextPosition() shouldBe 3L
      page.hasMore() shouldBe true
    }

    "keep an evaluation with an outcome that the SDK does not know in a flow page, and leave out an evaluation of a subject that is not an interaction" in {
      val ofInteraction = spiEvaluationRecord(
        SpiLedger.EvaluationTrigger.OnInteraction,
        "evaluation-1",
        outcome = unknownValue[SpiLedger.EvaluationOutcome])
      val client = ledgerClient(
        spiInteractionRecord(),
        Seq.empty,
        flowPage = new SpiLedger.FlowPage(
          Seq(
            ofInteraction,
            spiEvaluationRecord(
              SpiLedger.EvaluationTrigger.OnInteraction,
              "evaluation-2",
              subject = new SpiLedger.WorkflowSubject("order-1", "order-workflow")),
            spiEvaluationRecord(
              SpiLedger.EvaluationTrigger.OnInteraction,
              "evaluation-3",
              subject = unknownValue[SpiLedger.EvaluationSubject])),
          4L,
          hasMore = false))

      val records = client.getFlowRecords("flow-1").records().asScala.toSeq

      records shouldBe Seq(LedgerClientImpl.toEvaluationRecord(ofInteraction))
      records.head.asInstanceOf[EvaluationRecord].outcome() shouldBe a[EvaluationRecord.Outcome.Inconclusive]
    }

    "read the interactions and the guardrail decisions of a session that hold values the SDK does not know" in {
      val client = ledgerClient(
        spiInteractionRecord(finishReason = unknownValue[SpiLedger.FinishReason]),
        Seq.empty,
        Seq(
          spiGuardrailDecisionRecord(decision = new SpiLedger.GuardrailDecision.Deny(""), recordId = "guardrail-1"),
          spiGuardrailDecisionRecord(decision = unknownValue[SpiLedger.GuardrailDecision], recordId = "guardrail-2")))

      val interactions = client.getInteractions("support-agent", "session-1").asScala.toSeq
      interactions.map(_.interactionId()) shouldBe Seq("interaction-1")
      interactions.head.metadata().finishReason() shouldBe InteractionMetadata.FinishReason.UNSPECIFIED

      val decisions = client.getGuardrailDecisions("interaction-1").asScala.toSeq
      decisions.map(_.recordId()) shouldBe Seq("guardrail-1", "guardrail-2")
      decisions.map(_.decision()) shouldBe Seq(
        GuardrailDecisionRecord.Decision.DENY,
        GuardrailDecisionRecord.Decision.UNSPECIFIED)
    }

    "read an interaction and an evaluation that hold values the SDK does not know" in {
      val client = ledgerClient(
        spiInteractionRecord(
          finishReason = unknownValue[SpiLedger.FinishReason],
          failure = Some(new SpiLedger.Failure(unknownValue[SpiLedger.FailureReason], "failed")),
          modelResponses = Seq(spiModelResponse(unknownValue[SpiLedger.ToolCallStatus]))),
        Seq(spiEvaluationRecord(unknownValue[SpiLedger.EvaluationTrigger])))

      val interaction = client.getInteraction("interaction-1")
      interaction.metadata().finishReason() shouldBe InteractionMetadata.FinishReason.UNSPECIFIED
      interaction.failure().get().reason() shouldBe Failure.FailureReason.UNSPECIFIED
      interaction.toolCalls().get(0).status() shouldBe ToolCall.Status.UNSPECIFIED

      client.getEvaluation("evaluation-1").trigger() shouldBe EvaluationRecord.Trigger.UNSPECIFIED
    }

    "read every evaluation of a list in which one holds a trigger that the SDK does not know" in {
      val client = ledgerClient(
        spiInteractionRecord(),
        Seq(
          spiEvaluationRecord(SpiLedger.EvaluationTrigger.OnInteraction, "evaluation-1"),
          spiEvaluationRecord(unknownValue[SpiLedger.EvaluationTrigger], "evaluation-2"),
          spiEvaluationRecord(SpiLedger.EvaluationTrigger.Manual, "evaluation-3")))

      val evaluations = client.getEvaluations("interaction-1").asScala.toSeq

      evaluations.map(_.evaluationId()) shouldBe Seq("evaluation-1", "evaluation-2", "evaluation-3")
      evaluations.map(_.trigger()) shouldBe Seq(
        EvaluationRecord.Trigger.ON_INTERACTION,
        EvaluationRecord.Trigger.UNSPECIFIED,
        EvaluationRecord.Trigger.MANUAL)
    }
  }
}
