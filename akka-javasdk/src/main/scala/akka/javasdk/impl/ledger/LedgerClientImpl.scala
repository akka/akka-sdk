/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.ledger

import java.net.URI
import java.util
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.jdk.OptionConverters._

import akka.annotation.InternalApi
import akka.javasdk.agent.Agent
import akka.javasdk.agent.MessageContent
import akka.javasdk.evaluation.Evaluation
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.ForwardCompat.knownOr
import akka.javasdk.ledger.EvaluationRecord
import akka.javasdk.ledger.Failure
import akka.javasdk.ledger.GuardrailDecisionRecord
import akka.javasdk.ledger.InteractionMetadata
import akka.javasdk.ledger.InteractionRecord
import akka.javasdk.ledger.LedgerClient
import akka.javasdk.ledger.ModelConfig
import akka.javasdk.ledger.ModelResponse
import akka.javasdk.ledger.TaskContext
import akka.javasdk.ledger.ToolCall
import akka.javasdk.ledger.ToolCallResponse
import akka.runtime.sdk.spi.SpiLedger
import akka.runtime.sdk.spi.{ LedgerClient => SpiLedgerClient }

/**
 * INTERNAL API
 *
 * The public SDK [[LedgerClient]] backed by the runtime's SPI [[SpiLedgerClient]]. Adapts the SPI record types into the
 * SDK's public record types and the SPI `Future` into a `CompletionStage`.
 */
@InternalApi
private[javasdk] final class LedgerClientImpl(spiLedgerClient: SpiLedgerClient, sdkExecutionContext: ExecutionContext)
    extends LedgerClient {

  private implicit val ec: ExecutionContext = sdkExecutionContext

  override def getInteraction(interactionId: String): InteractionRecord =
    try getInteractionAsync(interactionId).toCompletableFuture.join()
    catch {
      case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
    }

  override def getInteractionAsync(interactionId: String): CompletionStage[InteractionRecord] =
    spiLedgerClient.getInteraction(interactionId).map(LedgerClientImpl.toInteractionRecord).asJava

  override def getEvaluation(evaluationId: String): EvaluationRecord =
    try getEvaluationAsync(evaluationId).toCompletableFuture.join()
    catch {
      case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
    }

  override def getEvaluationAsync(evaluationId: String): CompletionStage[EvaluationRecord] =
    spiLedgerClient.getEvaluation(evaluationId).map(LedgerClientImpl.toEvaluationRecord).asJava

  override def getEvaluations(interactionId: String): util.List[EvaluationRecord] =
    try getEvaluationsAsync(interactionId).toCompletableFuture.join()
    catch {
      case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
    }

  override def getEvaluationsAsync(interactionId: String): CompletionStage[util.List[EvaluationRecord]] =
    spiLedgerClient
      .getEvaluations(interactionId, from = None, limit = LedgerClientImpl.ListLimit)
      .map(_.map(LedgerClientImpl.toEvaluationRecord).asJava)
      .asJava

  override def getInteractions(agentComponentId: String, sessionId: String): util.List[InteractionRecord] =
    try getInteractionsAsync(agentComponentId, sessionId).toCompletableFuture.join()
    catch {
      case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
    }

  override def getInteractionsAsync(
      agentComponentId: String,
      sessionId: String): CompletionStage[util.List[InteractionRecord]] =
    spiLedgerClient
      .getInteractions(agentComponentId, sessionId, from = None, limit = LedgerClientImpl.ListLimit)
      .map(_.map(LedgerClientImpl.toInteractionRecord).asJava)
      .asJava

  override def getGuardrailDecisions(interactionId: String): util.List[GuardrailDecisionRecord] =
    try getGuardrailDecisionsAsync(interactionId).toCompletableFuture.join()
    catch {
      case e: CompletionException => throw ErrorHandling.unwrapCompletionException(e)
    }

  override def getGuardrailDecisionsAsync(interactionId: String): CompletionStage[util.List[GuardrailDecisionRecord]] =
    spiLedgerClient
      .getGuardrailDecisions(interactionId, from = None, limit = LedgerClientImpl.ListLimit)
      .map(_.map(LedgerClientImpl.toGuardrailDecisionRecord).asJava)
      .asJava
}

/**
 * INTERNAL API
 */
@InternalApi
private[ledger] object LedgerClientImpl {

  /** Cap on the records returned by one listing, matching the ledger page size. */
  private val ListLimit = 100

  def toInteractionRecord(record: SpiLedger.InteractionRecord): InteractionRecord =
    new InteractionRecord(
      record.interactionId,
      record.sessionId,
      record.agentComponentId,
      record.flowId.toJava,
      toMetadata(record.metadata),
      record.systemMessage,
      record.userMessage.map(toMessageContent).asJava,
      record.modelResponses.map(toModelResponse).asJava,
      record.toolCallResponses.map(toToolCallResponse).asJava,
      record.taskContext.map(toTaskContext).toJava,
      record.failure.map(toFailure).toJava,
      record.timestamp)

  def toEvaluationRecord(record: SpiLedger.EvaluationRecord): EvaluationRecord = {
    val subject = record.subject match {
      case interaction: SpiLedger.InteractionSubject => interaction
    }
    new EvaluationRecord(
      record.evaluationId,
      record.evaluatorComponentId,
      record.controlId.toJava,
      toEvaluationTrigger(record.trigger),
      subject.interactionId,
      subject.agentComponentId,
      toEvaluationOutcome(record.outcome),
      record.timestamp)
  }

  def toGuardrailDecisionRecord(record: SpiLedger.GuardrailDecisionRecord): GuardrailDecisionRecord =
    new GuardrailDecisionRecord(
      record.recordId,
      record.agentComponentId,
      record.interactionId,
      record.sessionId,
      record.flowId.toJava,
      record.name,
      record.controlId.toJava,
      record.category,
      toGuardrailBoundary(record.boundary),
      toGuardrailDecision(record.decision),
      record.decision.explanation,
      record.reportOnly,
      toFailMode(record.failMode),
      record.duration,
      record.toolCall.map(toToolCall).toJava,
      record.timestamp)

  private def toGuardrailBoundary(boundary: SpiLedger.GuardrailBoundary): GuardrailDecisionRecord.Boundary =
    knownOr(GuardrailDecisionRecord.Boundary.UNSPECIFIED) {
      boundary match {
        case SpiLedger.GuardrailBoundary.Unspecified         => GuardrailDecisionRecord.Boundary.UNSPECIFIED
        case SpiLedger.GuardrailBoundary.BeforeModelCall     => GuardrailDecisionRecord.Boundary.BEFORE_MODEL_CALL
        case SpiLedger.GuardrailBoundary.BeforeAgentResponse => GuardrailDecisionRecord.Boundary.BEFORE_AGENT_RESPONSE
        case SpiLedger.GuardrailBoundary.BeforeToolCall      => GuardrailDecisionRecord.Boundary.BEFORE_TOOL_CALL
      }
    }

  private def toGuardrailDecision(decision: SpiLedger.GuardrailDecision): GuardrailDecisionRecord.Decision =
    knownOr(GuardrailDecisionRecord.Decision.UNSPECIFIED) {
      decision match {
        case _: SpiLedger.GuardrailDecision.Unspecified => GuardrailDecisionRecord.Decision.UNSPECIFIED
        case _: SpiLedger.GuardrailDecision.Allow       => GuardrailDecisionRecord.Decision.ALLOW
        case _: SpiLedger.GuardrailDecision.Deny        => GuardrailDecisionRecord.Decision.DENY
        case _: SpiLedger.GuardrailDecision.Fail        => GuardrailDecisionRecord.Decision.FAIL
      }
    }

  private def toFailMode(failMode: SpiLedger.GuardrailFailMode): GuardrailDecisionRecord.FailMode =
    knownOr(GuardrailDecisionRecord.FailMode.UNSPECIFIED) {
      failMode match {
        case SpiLedger.GuardrailFailMode.Unspecified => GuardrailDecisionRecord.FailMode.UNSPECIFIED
        case SpiLedger.GuardrailFailMode.FailClosed  => GuardrailDecisionRecord.FailMode.FAIL_CLOSED
        case SpiLedger.GuardrailFailMode.FailOpen    => GuardrailDecisionRecord.FailMode.FAIL_OPEN
      }
    }

  private def toToolCall(toolCall: SpiLedger.GuardrailToolCall): GuardrailDecisionRecord.ToolCall =
    new GuardrailDecisionRecord.ToolCall(toolCall.toolCallId.toJava, toolCall.toolName, toToolOrigin(toolCall.origin))

  private def toToolOrigin(origin: SpiLedger.ToolOrigin): GuardrailDecisionRecord.ToolOrigin =
    knownOr[GuardrailDecisionRecord.ToolOrigin](new GuardrailDecisionRecord.ToolOrigin.Unspecified()) {
      origin match {
        case SpiLedger.ToolOrigin.Unspecified       => new GuardrailDecisionRecord.ToolOrigin.Unspecified()
        case SpiLedger.ToolOrigin.FunctionTool      => new GuardrailDecisionRecord.ToolOrigin.FunctionTool()
        case remote: SpiLedger.ToolOrigin.RemoteMcp => new GuardrailDecisionRecord.ToolOrigin.RemoteMcp(remote.endpoint)
      }
    }

  private def toEvaluationTrigger(trigger: SpiLedger.EvaluationTrigger): EvaluationRecord.Trigger =
    knownOr(EvaluationRecord.Trigger.UNSPECIFIED) {
      trigger match {
        case SpiLedger.EvaluationTrigger.Unspecified   => EvaluationRecord.Trigger.UNSPECIFIED
        case SpiLedger.EvaluationTrigger.Manual        => EvaluationRecord.Trigger.MANUAL
        case SpiLedger.EvaluationTrigger.OnInteraction => EvaluationRecord.Trigger.ON_INTERACTION
      }
    }

  private def toEvaluationOutcome(outcome: SpiLedger.EvaluationOutcome): EvaluationRecord.Outcome =
    outcome match {
      case verdict: SpiLedger.EvaluationVerdict =>
        new EvaluationRecord.Outcome.Verdict(toEvaluation(verdict.evaluation))
      case inconclusive: SpiLedger.EvaluationInconclusive =>
        new EvaluationRecord.Outcome.Inconclusive(inconclusive.reason)
      case notApplicable: SpiLedger.EvaluationNotApplicable =>
        new EvaluationRecord.Outcome.NotApplicable(notApplicable.reason)
      case failure: SpiLedger.EvaluationFailure =>
        new EvaluationRecord.Outcome.Failed(failure.reason)
    }

  private def toEvaluation(evaluation: SpiLedger.Evaluation): Evaluation = {
    val withScore =
      evaluation.score.foldLeft(Evaluation.of(evaluation.passed, evaluation.explanation))(_.withScore(_))
    val withLabel = evaluation.label.foldLeft(withScore)(_.withLabel(_))
    withLabel.withAttributes(evaluation.attributes.asJava)
  }

  private def toMetadata(metadata: SpiLedger.InteractionMetadata): InteractionMetadata =
    new InteractionMetadata(
      toModelConfig(metadata.modelConfig),
      metadata.modelConfigMap.asJava,
      metadata.callStartedAt,
      metadata.callFinishedAt,
      toFinishReason(metadata.finishReason))

  private def toModelConfig(config: SpiLedger.ModelConfig): ModelConfig =
    new ModelConfig(
      config.providerName,
      config.modelName,
      config.baseUrl,
      config.temperature,
      config.topP,
      config.topK,
      config.maxTokens)

  private def toFinishReason(reason: SpiLedger.FinishReason): InteractionMetadata.FinishReason =
    knownOr(InteractionMetadata.FinishReason.UNSPECIFIED) {
      reason match {
        case SpiLedger.FinishReason.Unspecified => InteractionMetadata.FinishReason.UNSPECIFIED
        case SpiLedger.FinishReason.Stop        => InteractionMetadata.FinishReason.STOP
        case SpiLedger.FinishReason.Length      => InteractionMetadata.FinishReason.LENGTH
      }
    }

  private def toModelResponse(response: SpiLedger.ModelResponse): ModelResponse =
    new ModelResponse(
      response.id,
      response.content,
      new Agent.TokenUsage(
        response.tokenUsage.inputTokenCount,
        response.tokenUsage.outputTokenCount,
        response.tokenUsage.cacheReadInputTokenCount,
        response.tokenUsage.cacheWriteInputTokenCount,
        response.tokenUsage.effectiveInputTokenCount),
      response.thinking,
      response.toolCalls.map(toToolCall).asJava)

  private def toToolCall(toolCall: SpiLedger.ToolCall): ToolCall =
    new ToolCall(toolCall.id, toolCall.name, toolCall.arguments, toolCall.response)

  private def toToolCallResponse(response: SpiLedger.ToolCallResponse): ToolCallResponse =
    new ToolCallResponse(response.id, response.name, response.contents.map(toMessageContent).asJava)

  private def toMessageContent(content: SpiLedger.MessageContent): MessageContent =
    content match {
      case text: SpiLedger.TextContent =>
        new MessageContent.TextMessageContent(text.text)
      case image: SpiLedger.ImageUriContent =>
        new MessageContent.ImageUrlMessageContent(
          URI.create(image.uri),
          toDetailLevel(image.detailLevel),
          image.mimeType.toJava)
      case pdf: SpiLedger.PdfUriContent =>
        new MessageContent.PdfUrlMessageContent(URI.create(pdf.uri))
    }

  // Mirrors SpiAgent.ImageMessageContent.DetailLevel.render, the form stored in the ledger.
  private def toDetailLevel(detailLevel: String): MessageContent.ImageMessageContent.DetailLevel =
    detailLevel match {
      case "low"        => MessageContent.ImageMessageContent.DetailLevel.LOW
      case "medium"     => MessageContent.ImageMessageContent.DetailLevel.MEDIUM
      case "high"       => MessageContent.ImageMessageContent.DetailLevel.HIGH
      case "ultra high" => MessageContent.ImageMessageContent.DetailLevel.ULTRA_HIGH
      case _            => MessageContent.ImageMessageContent.DetailLevel.AUTO
    }

  private def toTaskContext(taskContext: SpiLedger.TaskContext): TaskContext =
    new TaskContext(
      taskContext.agentInstanceId,
      taskContext.taskId,
      taskContext.taskName,
      taskContext.taskDescription,
      taskContext.taskResultType,
      taskContext.iterationNumber,
      taskContext.maxIterations)

  private def toFailure(failure: SpiLedger.Failure): Failure =
    new Failure(toFailureReason(failure.reason), failure.description)

  private def toFailureReason(reason: SpiLedger.FailureReason): Failure.FailureReason =
    knownOr(Failure.FailureReason.UNSPECIFIED) {
      reason match {
        case SpiLedger.FailureReason.Unspecified        => Failure.FailureReason.UNSPECIFIED
        case SpiLedger.FailureReason.Model              => Failure.FailureReason.MODEL
        case SpiLedger.FailureReason.RateLimit          => Failure.FailureReason.RATE_LIMIT
        case SpiLedger.FailureReason.Timeout            => Failure.FailureReason.TIMEOUT
        case SpiLedger.FailureReason.UnsupportedFeature => Failure.FailureReason.UNSUPPORTED_FEATURE
        case SpiLedger.FailureReason.Internal           => Failure.FailureReason.INTERNAL
        case SpiLedger.FailureReason.OutputParsing      => Failure.FailureReason.OUTPUT_PARSING
        case SpiLedger.FailureReason.ToolCall           => Failure.FailureReason.TOOL_CALL
        case SpiLedger.FailureReason.McpToolCall        => Failure.FailureReason.MCP_TOOL_CALL
        case SpiLedger.FailureReason.Guardrail          => Failure.FailureReason.GUARDRAIL
        case SpiLedger.FailureReason.ContentLoading     => Failure.FailureReason.CONTENT_LOADING
        case SpiLedger.FailureReason.Cancelled          => Failure.FailureReason.CANCELLED
        case SpiLedger.FailureReason.Sanitizer          => Failure.FailureReason.SANITIZER
      }
    }
}
