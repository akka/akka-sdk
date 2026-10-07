/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.ledger

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Instant

import scala.reflect.ClassTag

import akka.javasdk.ledger.EvaluationRecord
import akka.javasdk.ledger.Failure
import akka.javasdk.ledger.InteractionMetadata
import akka.runtime.sdk.spi.SpiLedger
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
    (SpiLedger.FailureReason.ContentLoading, Failure.FailureReason.CONTENT_LOADING))

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

  // The SPI traits are sealed, so the spec cannot extend them. Each one compiles to an interface without methods.
  private def unknownValue[T <: AnyRef](implicit tag: ClassTag[T]): T = {
    val name = s"unknown ${tag.runtimeClass.getSimpleName}"
    val handler: InvocationHandler = (proxy: AnyRef, method: Method, args: Array[AnyRef]) =>
      method.getName match {
        case "equals"   => Boolean.box(proxy eq args(0))
        case "hashCode" => Int.box(System.identityHashCode(proxy))
        case "toString" => name
        case other      => throw new UnsupportedOperationException(other)
      }
    Proxy.newProxyInstance(tag.runtimeClass.getClassLoader, Array(tag.runtimeClass), handler).asInstanceOf[T]
  }

  private def spiInteractionRecord(
      finishReason: SpiLedger.FinishReason = SpiLedger.FinishReason.Stop,
      failure: Option[SpiLedger.Failure] = None): SpiLedger.InteractionRecord =
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
      userMessage = Seq.empty,
      modelResponses = Seq.empty,
      toolCallResponses = Seq.empty,
      taskContext = None,
      failure = failure,
      timestamp = Instant.EPOCH)

  private def spiEvaluationRecord(trigger: SpiLedger.EvaluationTrigger): SpiLedger.EvaluationRecord =
    new SpiLedger.EvaluationRecord(
      evaluationId = "evaluation-1",
      evaluatorComponentId = "quality-evaluator",
      controlId = None,
      trigger = trigger,
      subject = new SpiLedger.InteractionSubject("interaction-1", "support-agent"),
      outcome = new SpiLedger.EvaluationInconclusive("no verdict"),
      timestamp = Instant.EPOCH)

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

    "read a failure reason that the SDK does not know as unspecified" in {
      val unknown = unknownValue[SpiLedger.FailureReason]
      forAll(failureReasons) { (known, _) => unknown should not be known }

      val record = LedgerClientImpl.toInteractionRecord(
        spiInteractionRecord(failure = Some(new SpiLedger.Failure(unknown, "the caller cancelled the stream"))))

      val failure = record.failure().get()
      failure.reason() shouldBe Failure.FailureReason.UNSPECIFIED
      failure.description() shouldBe "the caller cancelled the stream"
    }

    "read a finish reason that the SDK does not know as unspecified" in {
      val unknown = unknownValue[SpiLedger.FinishReason]
      forAll(finishReasons) { (known, _) => unknown should not be known }

      val record = LedgerClientImpl.toInteractionRecord(spiInteractionRecord(finishReason = unknown))

      record.metadata().finishReason() shouldBe InteractionMetadata.FinishReason.UNSPECIFIED
      record.metadata().modelConfig().modelName() shouldBe "gpt-4o"
    }

    "read a trigger that the SDK does not know as unspecified" in {
      val unknown = unknownValue[SpiLedger.EvaluationTrigger]
      forAll(triggers) { (known, _) => unknown should not be known }

      val record = LedgerClientImpl.toEvaluationRecord(spiEvaluationRecord(unknown))

      record.trigger() shouldBe EvaluationRecord.Trigger.UNSPECIFIED
      record.evaluatorComponentId() shouldBe "quality-evaluator"
    }
  }
}
