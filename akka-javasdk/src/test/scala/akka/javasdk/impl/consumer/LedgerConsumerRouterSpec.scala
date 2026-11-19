/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.consumer

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.Duration
import java.time.Instant
import java.util.Optional

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.Done
import akka.actor.testkit.typed.scaladsl.LogCapturing
import akka.actor.testkit.typed.scaladsl.LoggingTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.adapter._
import akka.javasdk.consumer.Consumer
import akka.javasdk.impl.AnySupport.BytesPrimitive
import akka.javasdk.impl.ComponentDescriptor
import akka.javasdk.impl.MetadataImpl
import akka.javasdk.impl.ledger.LedgerClientImpl
import akka.javasdk.impl.serialization.Serializer
import akka.javasdk.ledger.ActivityRecord
import akka.javasdk.ledger.EvaluationRecord
import akka.javasdk.ledger.GuardrailDecisionRecord
import akka.javasdk.ledger.InteractionRecord
import akka.javasdk.testmodels.subscriptions.LedgerTestModels.AllRecordsConsumer
import akka.javasdk.testmodels.subscriptions.LedgerTestModels.InteractionAndGuardrailConsumer
import akka.javasdk.testmodels.subscriptions.LedgerTestModels.MixedConsumer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.ConsumerSource
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.SpiConsumer
import akka.runtime.sdk.spi.SpiLedger
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiMetadataEntry
import akka.runtime.sdk.spi.TimerClient
import akka.util.ByteString
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.{ Context => OtelContext }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class LedgerConsumerRouterSpec extends ScalaTestWithActorTestKit with LogCapturing with AnyWordSpecLike with Matchers {

  private val classicSystem = system.toClassic

  private val timerClient = new TimerClient {
    override def startSingleTimer(
        name: String,
        delay: FiniteDuration,
        maxRetries: Int,
        deferredRequest: akka.runtime.sdk.spi.DeferredRequest): Future[Done] = ???
    override def removeTimer(name: String): Future[Done] = ???
    override def isTimerActive(name: String): Future[Boolean] = ???
  }

  private val recordTypes = Set[SpiLedger.RecordType](
    SpiLedger.RecordType.Interaction,
    SpiLedger.RecordType.Activity,
    SpiLedger.RecordType.GuardrailDecision,
    SpiLedger.RecordType.Evaluation,
    SpiLedger.RecordType.Classification)

  private def spiInteractionRecord(flowId: Option[String] = None): SpiLedger.InteractionRecord =
    new SpiLedger.InteractionRecord(
      interactionId = "interaction-1",
      sessionId = "session-1",
      agentComponentId = "support-agent",
      flowId = flowId,
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
        finishReason = SpiLedger.FinishReason.Stop),
      systemMessage = "",
      userMessage = Seq.empty,
      modelResponses = Seq.empty,
      toolCallResponses = Seq.empty,
      taskContext = None,
      failure = None,
      timestamp = Instant.EPOCH)

  private def spiGuardrailDecisionRecord: SpiLedger.GuardrailDecisionRecord = spiGuardrailDecisionRecord(None)

  private def spiGuardrailDecisionRecord(flowId: Option[String]): SpiLedger.GuardrailDecisionRecord =
    new SpiLedger.GuardrailDecisionRecord(
      recordId = "guardrail-1",
      agentComponentId = "support-agent",
      interactionId = "interaction-1",
      sessionId = "session-1",
      flowId = flowId,
      name = "no-refunds",
      controlId = None,
      category = "policy",
      boundary = SpiLedger.GuardrailBoundary.BeforeModelCall,
      decision = new SpiLedger.GuardrailDecision.Deny("refunds are not allowed"),
      reportOnly = false,
      failMode = SpiLedger.GuardrailFailMode.FailClosed,
      duration = Duration.ZERO,
      toolCall = None,
      timestamp = Instant.EPOCH)

  private def spiActivityRecord: SpiLedger.ActivityRecord =
    new SpiLedger.ActivityRecord(
      recordId = "activity-1",
      agentComponentId = "coordinator-agent",
      agentInstanceId = "coordinator-1",
      flowId = "flow-1",
      events = Seq(new SpiLedger.ActivityEvent.TaskAssigned("task-1")),
      timestamp = Instant.EPOCH)

  private def spiWorkflowEvaluationRecord: SpiLedger.EvaluationRecord =
    new SpiLedger.EvaluationRecord(
      evaluationId = "evaluation-1",
      evaluatorComponentId = "order-evaluator",
      controlId = None,
      trigger = SpiLedger.EvaluationTrigger.Manual,
      subject = new SpiLedger.WorkflowSubject("order-1", "order-workflow"),
      outcome = new SpiLedger.EvaluationInconclusive("no verdict"),
      timestamp = Instant.EPOCH,
      attributes = Map.empty)

  private def spiClassificationRecord: SpiLedger.ClassificationRecord =
    new SpiLedger.ClassificationRecord(
      classificationId = "classification-1",
      classifierComponentId = "sentiment-classifier",
      label = Some("positive"),
      score = None,
      confidence = None,
      attributes = Map.empty,
      flowId = Some("flow-3"),
      evaluationId = None,
      timestamp = Instant.EPOCH)

  private def interactionRecord: InteractionRecord = LedgerClientImpl.toInteractionRecord(spiInteractionRecord())

  private def guardrailDecisionRecord: GuardrailDecisionRecord =
    LedgerClientImpl.toGuardrailDecisionRecord(spiGuardrailDecisionRecord)

  private def activityRecord: ActivityRecord = LedgerClientImpl.toActivityRecord(spiActivityRecord)

  // Builds an SPI record of a class this SDK does not know.
  private def unknownSpiRecord: SpiLedger.FlowRecord = {
    val handler: InvocationHandler = (proxy: AnyRef, method: Method, args: Array[AnyRef]) =>
      method.getName match {
        case "equals"   => Boolean.box(proxy eq args(0))
        case "hashCode" => Int.box(System.identityHashCode(proxy))
        case "toString" => "unknown FlowRecord"
        case other      => throw new UnsupportedOperationException(other)
      }
    Proxy
      .newProxyInstance(classOf[SpiLedger.FlowRecord].getClassLoader, Array(classOf[SpiLedger.FlowRecord]), handler)
      .asInstanceOf[SpiLedger.FlowRecord]
  }

  private def messageContext = new MessageContextImpl(MetadataImpl.Empty, timerClient, null, None, "", Optional.empty())

  private def ledgerMessage(record: SpiLedger.FlowRecord): SpiConsumer.Message =
    new SpiConsumer.Message(
      None,
      None,
      new SpiMetadata(Seq(new SpiMetadataEntry("ce-subject", "writer-1"))),
      OtelContext.root(),
      Some(record))

  private def router[C <: Consumer](consumer: C): LedgerConsumerRouter[C] =
    new LedgerConsumerRouter(consumer, LedgerConsumerRouter.handlers(consumer.getClass))

  private def consumerImpl[C <: Consumer](consumerClass: Class[C], consumer: C): ConsumerImpl[C] = {
    val serializer = new Serializer
    new ConsumerImpl(
      "ledger-consumer",
      _ => consumer,
      consumerClass,
      new ConsumerSource.LedgerSource(recordTypes),
      None,
      classicSystem,
      timerClient,
      classicSystem.dispatcher,
      () => OpenTelemetry.noop().getTracer("test"),
      serializer,
      ignoreUnknown = false,
      ComponentDescriptor.descriptorFor(consumerClass, serializer),
      new RegionInfo(""))
  }

  "The ledger consumer router" should {

    "route each record to the handler of its class" in {
      val consumer = new InteractionAndGuardrailConsumer
      val consumerRouter = router(consumer)

      consumerRouter.handleRecord(interactionRecord, messageContext) shouldBe ConsumerEffectImpl.ConsumedEffect
      consumerRouter.handleRecord(guardrailDecisionRecord, messageContext) shouldBe ConsumerEffectImpl.ConsumedEffect

      consumer.interaction shouldBe interactionRecord
      consumer.guardrailDecision shouldBe guardrailDecisionRecord
    }

    "route to the most specific handler" in {
      val consumer = new MixedConsumer
      val consumerRouter = router(consumer)

      consumerRouter.handleRecord(interactionRecord, messageContext)
      consumer.interaction shouldBe interactionRecord
      consumer.record shouldBe null

      consumerRouter.handleRecord(activityRecord, messageContext)
      consumer.record shouldBe activityRecord
    }

    "ignore a record with no handler" in {
      val consumer = new InteractionAndGuardrailConsumer

      router(consumer)
        .handleRecord(activityRecord, messageContext) shouldBe ConsumerEffectImpl.Builder.ignore()

      consumer.interaction shouldBe null
      consumer.guardrailDecision shouldBe null
    }
  }

  "The consumer of a ledger source" should {

    "deliver an evaluation of a workflow run" in {
      val consumer = new AllRecordsConsumer

      consumerImpl(classOf[AllRecordsConsumer], consumer)
        .handleMessage(ledgerMessage(spiWorkflowEvaluationRecord))
        .futureValue shouldBe SpiConsumer.ConsumedEffect

      val evaluation = consumer.record.asInstanceOf[EvaluationRecord]
      evaluation.evaluationId shouldBe "evaluation-1"
      evaluation.subject shouldBe new EvaluationRecord.Subject.Workflow("order-1", "order-workflow")
    }

    "set the event subject to the record id" in {
      val consumer = new AllRecordsConsumer

      consumerImpl(classOf[AllRecordsConsumer], consumer)
        .handleMessage(ledgerMessage(spiInteractionRecord(flowId = Some("flow-1"))))
        .futureValue shouldBe SpiConsumer.ConsumedEffect

      consumer.context.eventSubject() shouldBe Optional.of("interaction-1")
      val metadata = consumer.context.metadata()
      metadata.get("ce-id") shouldBe Optional.of("interaction-1")
      metadata.get("ce-type") shouldBe Optional.of("interaction")
      metadata.get("ce-source") shouldBe Optional.of("support-agent")
      metadata.get("flow-id") shouldBe Optional.of("flow-1")
    }

    "set the type, source and flow id of each record kind" in {
      val cases = Seq(
        (spiInteractionRecord(), "interaction-1", "interaction", "support-agent", None),
        (spiActivityRecord, "activity-1", "activity", "coordinator-agent", Some("flow-1")),
        (spiGuardrailDecisionRecord(Some("flow-2")), "guardrail-1", "guardrail-decision", "no-refunds", Some("flow-2")),
        (spiWorkflowEvaluationRecord, "evaluation-1", "evaluation", "order-evaluator", None),
        (spiClassificationRecord, "classification-1", "classification", "sentiment-classifier", Some("flow-3")))

      cases.foreach { case (spiRecord, recordId, recordType, source, flowId) =>
        withClue(s"$recordType: ") {
          val consumer = new AllRecordsConsumer

          consumerImpl(classOf[AllRecordsConsumer], consumer)
            .handleMessage(ledgerMessage(spiRecord))
            .futureValue shouldBe SpiConsumer.ConsumedEffect

          val metadata = consumer.context.metadata()
          metadata.get("ce-id") shouldBe Optional.of(recordId)
          metadata.get("ce-type") shouldBe Optional.of(recordType)
          metadata.get("ce-source") shouldBe Optional.of(source)
          metadata.get("flow-id") shouldBe Optional.ofNullable(flowId.orNull)
        }
      }
    }

    "ignore a record of an unknown class" in {
      val consumer = new AllRecordsConsumer

      consumerImpl(classOf[AllRecordsConsumer], consumer)
        .handleMessage(ledgerMessage(unknownSpiRecord))
        .futureValue shouldBe SpiConsumer.ConsumedEffect

      consumer.record shouldBe null
    }

    "fail a message without record" in {
      val consumer = new AllRecordsConsumer
      val message = new SpiConsumer.Message(
        Some(new BytesPayload(ByteString("some bytes"), BytesPrimitive.fullName)),
        None,
        SpiMetadata.empty,
        OtelContext.root())

      val effect =
        LoggingTestKit
          .custom { event =>
            event.message.startsWith("Failure during handling message") &&
            event.throwable.exists(_.getMessage.contains(classOf[AllRecordsConsumer].getName))
          }
          .expect {
            consumerImpl(classOf[AllRecordsConsumer], consumer).handleMessage(message).futureValue
          }

      effect shouldBe a[SpiConsumer.ErrorEffect]
      consumer.record shouldBe null
    }
  }
}
