/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.ledger

import java.time.Duration
import java.time.Instant
import java.util
import java.util.Optional
import java.util.concurrent.CompletionException

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import akka.javasdk.ledger.ActivityRecord
import akka.javasdk.ledger.ClassificationRecord
import akka.javasdk.ledger.EvaluationRecord
import akka.javasdk.ledger.GuardrailDecisionRecord
import akka.javasdk.ledger.InteractionRecord
import akka.runtime.sdk.spi.SpiAgent
import akka.runtime.sdk.spi.SpiAutonomousAgent
import akka.runtime.sdk.spi.SpiLedger
import akka.runtime.sdk.spi.{ LedgerClient => SpiLedgerClient }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LedgerClientImplSpec extends AnyWordSpec with Matchers {

  // Answers a flow read with the given page and records the arguments of each flow read.
  private final class FlowReadingSpiLedgerClient(page: Future[SpiLedger.FlowPage]) extends SpiLedgerClient {
    val flowReads: mutable.Buffer[(String, Long, Int)] = mutable.ArrayBuffer.empty

    override def getFlowRecords(flowId: String, fromPosition: Long, limit: Int): Future[SpiLedger.FlowPage] = {
      flowReads += ((flowId, fromPosition, limit))
      page
    }

    override def getInteraction(interactionId: String): Future[SpiLedger.InteractionRecord] = unexpected
    override def getEvaluation(evaluationId: String): Future[SpiLedger.EvaluationRecord] = unexpected
    override def getClassification(classificationId: String): Future[SpiLedger.ClassificationRecord] = unexpected
    override def getEvaluations(
        interactionId: String,
        from: Option[Instant],
        limit: Int): Future[Seq[SpiLedger.EvaluationRecord]] = unexpected
    override def getWorkflowEvaluations(
        workflowComponentId: String,
        workflowId: String,
        from: Option[Instant],
        limit: Int): Future[Seq[SpiLedger.EvaluationRecord]] = unexpected
    override def getInteractions(
        agentComponentId: String,
        sessionId: String,
        from: Option[Instant],
        limit: Int): Future[Seq[SpiLedger.InteractionRecord]] = unexpected
    override def getGuardrailDecisions(
        interactionId: String,
        from: Option[Instant],
        limit: Int): Future[Seq[SpiLedger.GuardrailDecisionRecord]] = unexpected

    private def unexpected[T]: Future[T] = Future.failed(new UnsupportedOperationException("not a flow read"))
  }

  private def spiActivityRecord(events: SpiLedger.ActivityEvent*): SpiLedger.ActivityRecord =
    new SpiLedger.ActivityRecord(
      recordId = "activity-1",
      agentComponentId = "coordinator-agent",
      agentInstanceId = "coordinator-1",
      flowId = "flow-1",
      events = events,
      timestamp = Instant.ofEpochSecond(7))

  private def spiEvaluationRecord(controlId: Option[String]): SpiLedger.EvaluationRecord =
    new SpiLedger.EvaluationRecord(
      evaluationId = "evaluation-1",
      evaluatorComponentId = "quality-evaluator",
      controlId = controlId,
      trigger = SpiLedger.EvaluationTrigger.OnInteraction,
      subject = new SpiLedger.InteractionSubject("interaction-1", "support-agent"),
      outcome = new SpiLedger.EvaluationInconclusive("no verdict"),
      timestamp = Instant.EPOCH,
      attributes = Map.empty)

  private val spiWorkflowEvaluationRecord: SpiLedger.EvaluationRecord =
    new SpiLedger.EvaluationRecord(
      evaluationId = "evaluation-2",
      evaluatorComponentId = "order-evaluator",
      controlId = None,
      trigger = SpiLedger.EvaluationTrigger.Manual,
      subject = new SpiLedger.WorkflowSubject("workflow-1", "order-workflow"),
      outcome = new SpiLedger.EvaluationInconclusive("no verdict"),
      timestamp = Instant.EPOCH,
      attributes = Map("interactionIds" -> "i1,i2"))

  private def spiInteractionRecord(modelResponses: SpiLedger.ModelResponse*): SpiLedger.InteractionRecord =
    new SpiLedger.InteractionRecord(
      interactionId = "interaction-1",
      sessionId = "session-1",
      agentComponentId = "support-agent",
      flowId = None,
      metadata = new SpiLedger.InteractionMetadata(
        new SpiLedger.ModelConfig("anthropic", "claude", "", -1.0, -1.0, -1, -1),
        Map.empty,
        Instant.EPOCH,
        Instant.EPOCH,
        SpiLedger.FinishReason.Stop),
      systemMessage = "",
      userMessage = Seq.empty,
      modelResponses = modelResponses,
      toolCallResponses = Seq.empty,
      taskContext = None,
      failure = None,
      timestamp = Instant.EPOCH)

  private def spiGuardrailDecisionRecord(
      controlId: Option[String],
      toolCall: Option[SpiLedger.GuardrailToolCall]): SpiLedger.GuardrailDecisionRecord =
    new SpiLedger.GuardrailDecisionRecord(
      recordId = "guardrail-1",
      agentComponentId = "support-agent",
      interactionId = "interaction-1",
      sessionId = "session-1",
      flowId = Some("flow-1"),
      name = "no-refunds",
      controlId = controlId,
      category = "policy",
      boundary = SpiLedger.GuardrailBoundary.BeforeToolCall,
      decision = new SpiLedger.GuardrailDecision.Deny("refunds are not allowed"),
      reportOnly = true,
      failMode = SpiLedger.GuardrailFailMode.FailOpen,
      duration = Duration.ofMillis(12),
      toolCall = toolCall,
      timestamp = Instant.EPOCH)

  private def spiModelResponse(tokenUsage: SpiAgent.SpiTokenUsage): SpiLedger.ModelResponse =
    new SpiLedger.ModelResponse("m1", "", tokenUsage, "", Seq.empty)

  /** An SPI ledger client that holds no records. */
  private class EmptySpiLedgerClient extends SpiLedgerClient {
    override def getInteraction(interactionId: String): Future[SpiLedger.InteractionRecord] =
      Future.failed(new NoSuchElementException(interactionId))

    override def getEvaluation(evaluationId: String): Future[SpiLedger.EvaluationRecord] =
      Future.failed(new NoSuchElementException(evaluationId))

    override def getClassification(classificationId: String): Future[SpiLedger.ClassificationRecord] =
      Future.failed(new NoSuchElementException(classificationId))

    override def getEvaluations(
        interactionId: String,
        from: Option[Instant],
        limit: Int): Future[Seq[SpiLedger.EvaluationRecord]] =
      Future.successful(Seq.empty)

    override def getWorkflowEvaluations(
        workflowComponentId: String,
        workflowId: String,
        from: Option[Instant],
        limit: Int): Future[Seq[SpiLedger.EvaluationRecord]] =
      Future.successful(Seq.empty)

    override def getInteractions(
        agentComponentId: String,
        sessionId: String,
        from: Option[Instant],
        limit: Int): Future[Seq[SpiLedger.InteractionRecord]] =
      Future.successful(Seq.empty)

    override def getGuardrailDecisions(
        interactionId: String,
        from: Option[Instant],
        limit: Int): Future[Seq[SpiLedger.GuardrailDecisionRecord]] =
      Future.successful(Seq.empty)

    override def getFlowRecords(flowId: String, fromPosition: Long, limit: Int): Future[SpiLedger.FlowPage] =
      Future.successful(new SpiLedger.FlowPage(Seq.empty, fromPosition, hasMore = false))
  }

  "Mapping an interaction record" should {

    "carry the token counts of each model response" in {
      val record = LedgerClientImpl.toInteractionRecord(
        spiInteractionRecord(
          spiModelResponse(new SpiAgent.SpiTokenUsage(100, 10, 11, 22, 133)),
          spiModelResponse(new SpiAgent.SpiTokenUsage(50, 5))))

      val tokenUsage = record.modelResponses().get(0).tokenUsage()
      tokenUsage.inputTokens() shouldEqual 100
      tokenUsage.outputTokens() shouldEqual 10
      tokenUsage.cacheReadInputTokens() shouldEqual 11
      tokenUsage.cacheWriteInputTokens() shouldEqual 22
      tokenUsage.totalInputTokens() shouldEqual 133

      record.totalInputTokens() shouldEqual 183
      record.totalOutputTokens() shouldEqual 15
    }
  }

  "Mapping an evaluation record" should {

    "carry the control id of the evaluator" in {
      val record = LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(Some("EVAL-1")))

      record.controlId() shouldEqual Optional.of("EVAL-1")
    }

    "carry an empty control id when the evaluator declares none" in {
      val record = LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(None))

      record.controlId() shouldEqual Optional.empty()
    }

    "map a workflow subject and the attributes" in {
      val record = LedgerClientImpl.toEvaluationRecord(spiWorkflowEvaluationRecord)

      record.trigger() shouldEqual EvaluationRecord.Trigger.MANUAL
      record.subject() shouldEqual new EvaluationRecord.Subject.Workflow("workflow-1", "order-workflow")
      record.attributes() shouldEqual util.Map.of("interactionIds", "i1,i2")
    }

    "map an interaction subject" in {
      val record = LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(controlId = None))

      record.subject() shouldEqual new EvaluationRecord.Subject.Interaction("interaction-1", "support-agent")
      record.attributes() shouldEqual util.Map.of()
    }

    "carry a not applicable outcome" in {
      val spiRecord = new SpiLedger.EvaluationRecord(
        evaluationId = "evaluation-1",
        evaluatorComponentId = "quality-evaluator",
        controlId = None,
        trigger = SpiLedger.EvaluationTrigger.OnInteraction,
        subject = new SpiLedger.InteractionSubject("interaction-1", "support-agent"),
        outcome = new SpiLedger.EvaluationNotApplicable("no tool call"),
        timestamp = Instant.EPOCH,
        attributes = Map.empty)

      val record = LedgerClientImpl.toEvaluationRecord(spiRecord)

      record.outcome() shouldEqual new EvaluationRecord.Outcome.NotApplicable("no tool call")
      record.evaluation() shouldEqual Optional.empty()
    }
  }

  "Fetching the evaluations of a workflow run" should {

    "read the newest records of the run through the SPI client" in {
      var requested: Option[(String, String, Option[Instant], Int)] = None
      val spiLedgerClient = new EmptySpiLedgerClient {
        override def getWorkflowEvaluations(
            workflowComponentId: String,
            workflowId: String,
            from: Option[Instant],
            limit: Int): Future[Seq[SpiLedger.EvaluationRecord]] = {
          requested = Some((workflowComponentId, workflowId, from, limit))
          Future.successful(Seq(spiWorkflowEvaluationRecord))
        }
      }
      val client = new LedgerClientImpl(spiLedgerClient, ExecutionContext.parasitic)

      val records = client.getWorkflowEvaluations("order-workflow", "workflow-1")

      records.asScala.map(_.evaluationId()) shouldEqual Seq("evaluation-2")
      requested shouldBe Some(("order-workflow", "workflow-1", None, 100))
    }
  }

  "Fetching a classification with an unknown id" should {

    "throw NoSuchElementException from the blocking variant" in {
      val client = new LedgerClientImpl(new EmptySpiLedgerClient, ExecutionContext.parasitic)

      intercept[NoSuchElementException] {
        client.getClassification("unknown")
      }
    }

    "fail the stage of the async variant with NoSuchElementException" in {
      val client = new LedgerClientImpl(new EmptySpiLedgerClient, ExecutionContext.parasitic)

      val failure = intercept[CompletionException] {
        client.getClassificationAsync("unknown").toCompletableFuture.join()
      }
      failure.getCause shouldBe a[NoSuchElementException]
    }
  }

  "Mapping a classification record" should {

    "carry the result, the attributes and the references" in {
      val record = LedgerClientImpl.toClassificationRecord(
        new SpiLedger.ClassificationRecord(
          classificationId = "classification-1",
          classifierComponentId = "sentiment-classifier",
          label = Some("positive"),
          score = Some(0.9),
          confidence = Some(0.8),
          attributes = Map("source" -> "judge"),
          flowId = Some("flow-1"),
          evaluationId = Some("evaluation-1"),
          timestamp = Instant.EPOCH))

      record shouldEqual new ClassificationRecord(
        "classification-1",
        "sentiment-classifier",
        Optional.of(0.9),
        Optional.of("positive"),
        Optional.of(0.8),
        Map("source" -> "judge").asJava,
        Optional.of("flow-1"),
        Optional.of("evaluation-1"),
        Instant.EPOCH)
    }

    "carry empty optionals for a classification without a result or references" in {
      val record = LedgerClientImpl.toClassificationRecord(
        new SpiLedger.ClassificationRecord(
          classificationId = "classification-2",
          classifierComponentId = "sentiment-classifier",
          label = None,
          score = None,
          confidence = None,
          attributes = Map.empty,
          flowId = None,
          evaluationId = None,
          timestamp = Instant.EPOCH))

      record.label() shouldEqual Optional.empty()
      record.score() shouldEqual Optional.empty()
      record.confidence() shouldEqual Optional.empty()
      record.attributes() shouldEqual util.Map.of()
      record.flowId() shouldEqual Optional.empty()
      record.evaluationId() shouldEqual Optional.empty()
    }
  }

  "Mapping a guardrail decision record" should {

    "carry the decision and the tool call" in {
      val record = LedgerClientImpl.toGuardrailDecisionRecord(
        spiGuardrailDecisionRecord(
          controlId = Some("GR-1"),
          toolCall = Some(
            new SpiLedger.GuardrailToolCall(
              toolCallId = Some("call-1"),
              toolName = "RefundTools_refund",
              origin = new SpiLedger.ToolOrigin.RemoteMcp("https://mcp.example.com")))))

      record.recordId() shouldEqual "guardrail-1"
      record.agentComponentId() shouldEqual "support-agent"
      record.interactionId() shouldEqual "interaction-1"
      record.sessionId() shouldEqual "session-1"
      record.flowId() shouldEqual Optional.of("flow-1")
      record.name() shouldEqual "no-refunds"
      record.controlId() shouldEqual Optional.of("GR-1")
      record.category() shouldEqual "policy"
      record.boundary() shouldEqual GuardrailDecisionRecord.Boundary.BEFORE_TOOL_CALL
      record.decision() shouldEqual GuardrailDecisionRecord.Decision.DENY
      record.explanation() shouldEqual "refunds are not allowed"
      record.reportOnly() shouldBe true
      record.failMode() shouldEqual GuardrailDecisionRecord.FailMode.FAIL_OPEN
      record.duration() shouldEqual Duration.ofMillis(12)
      record.toolCall() shouldEqual Optional.of(
        new GuardrailDecisionRecord.ToolCall(
          Optional.of("call-1"),
          "RefundTools_refund",
          new GuardrailDecisionRecord.ToolOrigin.RemoteMcp("https://mcp.example.com")))
      record.timestamp() shouldEqual Instant.EPOCH
    }

    "carry empty optionals when the guardrail decided on no tool call and declares no control id" in {
      val record =
        LedgerClientImpl.toGuardrailDecisionRecord(spiGuardrailDecisionRecord(controlId = None, toolCall = None))

      record.controlId() shouldEqual Optional.empty()
      record.toolCall() shouldEqual Optional.empty()
    }
  }

  "Mapping an activity record" should {

    "carry every field of the record and of each event" in {
      val record = LedgerClientImpl.toActivityRecord(
        spiActivityRecord(
          new SpiLedger.ActivityEvent.TaskAssigned("task-1"),
          new SpiLedger.ActivityEvent.TaskStarted("task-1", "Research quantum computing", 5),
          new SpiLedger.ActivityEvent.TaskCompleted("task-1", 3),
          new SpiLedger.ActivityEvent.TaskFailed("task-2", "iteration limit reached", 6),
          new SpiLedger.ActivityEvent.TaskCancelled("task-3", "dependency failed", 0),
          new SpiLedger.ActivityEvent.TaskResultRejected("task-4", "title missing", 2),
          new SpiLedger.ActivityEvent.IterationFailed(Some("task-5"), Some(4), "rate limited"),
          new SpiLedger.ActivityEvent.IterationFailed(None, None, "model unavailable"),
          new SpiLedger.ActivityEvent.DelegationStarted(
            workers = Seq(
              new SpiAutonomousAgent.AgentRef("worker-agent", "worker-1"),
              new SpiAutonomousAgent.AgentRef("reviewer-agent", "reviewer-1")),
            delegationCount = 2,
            subtaskIds = Seq("subtask-1", "subtask-2")),
          new SpiLedger.ActivityEvent.DelegationResolved(
            succeeded = 2,
            failed = 1,
            succeededSubtaskIds = Seq("subtask-1", "subtask-2"),
            failedSubtaskIds = Seq("subtask-3")),
          new SpiLedger.ActivityEvent.HandoffStarted("task-6", "specialist-agent"),
          new SpiLedger.ActivityEvent.HandoffReceived(
            "task-7",
            new SpiAutonomousAgent.AgentRef("triage-agent", "triage-1")),
          new SpiLedger.ActivityEvent.Other("worker_task_received")))

      record.recordId() shouldEqual "activity-1"
      record.agentComponentId() shouldEqual "coordinator-agent"
      record.agentInstanceId() shouldEqual "coordinator-1"
      record.flowId() shouldEqual "flow-1"
      record.timestamp() shouldEqual Instant.ofEpochSecond(7)
      record.events().asScala.toSeq shouldEqual Seq(
        new ActivityRecord.Event.TaskAssigned("task-1"),
        new ActivityRecord.Event.TaskStarted("task-1", "Research quantum computing", 5),
        new ActivityRecord.Event.TaskCompleted("task-1", 3),
        new ActivityRecord.Event.TaskFailed("task-2", "iteration limit reached", 6),
        new ActivityRecord.Event.TaskCancelled("task-3", "dependency failed", 0),
        new ActivityRecord.Event.TaskResultRejected("task-4", "title missing", 2),
        new ActivityRecord.Event.IterationFailed("rate limited", Optional.of("task-5"), Optional.of(4)),
        new ActivityRecord.Event.IterationFailed("model unavailable", Optional.empty(), Optional.empty()),
        new ActivityRecord.Event.DelegationStarted(
          Seq("worker-agent", "reviewer-agent").asJava,
          2,
          Seq("subtask-1", "subtask-2").asJava,
          Seq("worker-1", "reviewer-1").asJava),
        new ActivityRecord.Event.DelegationResolved(
          2,
          1,
          Seq("subtask-1", "subtask-2").asJava,
          Seq("subtask-3").asJava),
        new ActivityRecord.Event.HandoffStarted("task-6", "specialist-agent"),
        new ActivityRecord.Event.HandoffReceived("task-7", "triage-agent", "triage-1"),
        new ActivityRecord.Event.Other("worker_task_received"))
    }
  }

  "Reading the records of a flow" should {

    "map a page of each of the four kinds to the public records, in order, with its next position and has-more flag" in {
      val interaction = spiInteractionRecord()
      val activity = spiActivityRecord(new SpiLedger.ActivityEvent.TaskAssigned("task-1"))
      val decision = spiGuardrailDecisionRecord(controlId = None, toolCall = None)
      val evaluation = spiEvaluationRecord(controlId = None)
      val spiClient = new FlowReadingSpiLedgerClient(
        Future.successful(new SpiLedger.FlowPage(Seq(interaction, activity, decision, evaluation), 5L, hasMore = true)))

      val page = new LedgerClientImpl(spiClient, ExecutionContext.parasitic).getFlowRecords("flow-1")

      val records = page.records().asScala.toSeq
      records.map(_.getClass) shouldEqual Seq(
        classOf[InteractionRecord],
        classOf[ActivityRecord],
        classOf[GuardrailDecisionRecord],
        classOf[EvaluationRecord])
      records shouldEqual Seq(
        LedgerClientImpl.toInteractionRecord(interaction),
        LedgerClientImpl.toActivityRecord(activity),
        LedgerClientImpl.toGuardrailDecisionRecord(decision),
        LedgerClientImpl.toEvaluationRecord(evaluation))
      page.nextPosition() shouldEqual 5L
      page.hasMore() shouldBe true
    }

    "map an empty last page" in {
      val spiClient =
        new FlowReadingSpiLedgerClient(Future.successful(new SpiLedger.FlowPage(Seq.empty, 5L, hasMore = false)))

      val page = new LedgerClientImpl(spiClient, ExecutionContext.parasitic).getFlowRecords("flow-1", 5L)

      page.records().isEmpty shouldBe true
      page.nextPosition() shouldEqual 5L
      page.hasMore() shouldBe false
    }

    "ask for position 0 in the forms without a position, and for a limit of 100 in every form" in {
      val spiClient =
        new FlowReadingSpiLedgerClient(Future.successful(new SpiLedger.FlowPage(Seq.empty, 0L, hasMore = false)))
      val client = new LedgerClientImpl(spiClient, ExecutionContext.parasitic)

      client.getFlowRecords("flow-1")
      client.getFlowRecords("flow-2", 7L)
      client.getFlowRecordsAsync("flow-3").toCompletableFuture.get()
      client.getFlowRecordsAsync("flow-4", 9L).toCompletableFuture.get()

      spiClient.flowReads.toSeq shouldEqual Seq(
        ("flow-1", 0L, 100),
        ("flow-2", 7L, 100),
        ("flow-3", 0L, 100),
        ("flow-4", 9L, 100))
    }

    "throw the failure of the read in the blocking forms and fail the stage in the async forms" in {
      val failure = new IllegalArgumentException("The flow id must not be empty")
      val client =
        new LedgerClientImpl(new FlowReadingSpiLedgerClient(Future.failed(failure)), ExecutionContext.parasitic)

      (the[IllegalArgumentException] thrownBy client.getFlowRecords("")) shouldBe failure
      (the[IllegalArgumentException] thrownBy client.getFlowRecords("", 3L)) shouldBe failure
      val async = the[CompletionException] thrownBy client.getFlowRecordsAsync("").toCompletableFuture.join()
      async.getCause shouldBe failure
    }
  }
}
