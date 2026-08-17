/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.Done
import akka.javasdk.evaluation.TranscriptQualityEvaluator
import akka.javasdk.impl.evaluation.WorkflowEvaluatorImpl.RecordStepName
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.Outcome
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.StateEnvelope
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiEvaluator
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiWorkflow
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorkflowEvaluatorImplSpec extends AnyWordSpec with Matchers with OptionValues {

  private val serializer = new Serializer
  private val evaluationId = "evaluation-1"

  private final class RecorderProbe extends spi.SpiEvaluator.EvaluationRecorder {
    var recorded: Option[(SpiEvaluator.Trigger, SpiEvaluator.Result)] = None

    override def recordResult(trigger: SpiEvaluator.Trigger, result: SpiEvaluator.Result): Future[Done] = {
      recorded = Some((trigger, result))
      Future.successful(Done)
    }
  }

  private def newImpl(recorder: SpiEvaluator.EvaluationRecorder = new RecorderProbe) =
    new WorkflowEvaluatorImpl[TranscriptQualityEvaluator.State, TranscriptQualityEvaluator](
      evaluationId,
      classOf[TranscriptQualityEvaluator],
      classOf[TranscriptQualityEvaluator.State],
      () => new TranscriptQualityEvaluator,
      recorder,
      serializer,
      ExecutionContext.global)

  private def trigger(interactionId: String = "interaction-1") =
    new SpiEvaluator.Trigger(
      evaluationId,
      SpiEvaluator.TriggerSource.OnInteraction,
      new SpiEvaluator.Interaction(interactionId, Some("my-agent"), None))

  private def stepCommand(stepName: String, input: Option[BytesPayload] = None) =
    new SpiWorkflow.StepCommand(stepName, input, SpiMetadata.empty, null)

  private def await[T](future: Future[T]): T = Await.result(future, 3.seconds)

  private def decodeEnvelope(persistence: SpiWorkflow.Persistence): StateEnvelope = {
    persistence shouldBe an[SpiWorkflow.UpdateState]
    serializer.fromBytes(classOf[StateEnvelope], persistence.asInstanceOf[SpiWorkflow.UpdateState].newState)
  }

  "WorkflowEvaluatorImpl" should {

    "start the evaluation with the structured trigger and persist the subject envelope" in {
      val effect = await(newImpl().handleEvaluationStart(None, trigger()))

      effect shouldBe a[SpiWorkflow.CommandTransitionalEffect]
      val transitional = effect.asInstanceOf[SpiWorkflow.CommandTransitionalEffect]

      val envelope = decodeEnvelope(transitional.persistence)
      envelope.agentComponentId() shouldBe "my-agent"
      envelope.subjectId() shouldBe "interaction-1"
      envelope.userState() shouldBe null // no state update in onEvaluation, subject still persisted

      transitional.transition shouldBe a[SpiWorkflow.StepTransition]
      transitional.transition.asInstanceOf[SpiWorkflow.StepTransition].stepName shouldBe "fetchTranscript"
    }

    "ack a duplicate start without re-running the evaluation" in {
      val impl = newImpl()
      val started = await(impl.handleEvaluationStart(None, trigger()))
      val persistedState = started
        .asInstanceOf[SpiWorkflow.CommandTransitionalEffect]
        .persistence
        .asInstanceOf[SpiWorkflow.UpdateState]
        .newState

      val effect = await(impl.handleEvaluationStart(Some(persistedState), trigger()))
      effect shouldBe a[SpiWorkflow.ReadOnlyEffect]
    }

    "run a step that updates state and transitions with input" in {
      val impl = newImpl()
      val started = await(impl.handleEvaluationStart(None, trigger()))
      val persistedState = started
        .asInstanceOf[SpiWorkflow.CommandTransitionalEffect]
        .persistence
        .asInstanceOf[SpiWorkflow.UpdateState]
        .newState

      val result = await(impl.invokeStep(Some(persistedState), stepCommand("fetchTranscript")))

      result shouldBe a[SpiWorkflow.StepTransitionalEffect]
      val stepEffect = result.asInstanceOf[SpiWorkflow.StepTransitionalEffect]

      val envelope = decodeEnvelope(stepEffect.persistence)
      envelope.subjectId() shouldBe "interaction-1"
      envelope.userState() should not be null

      val transition = stepEffect.transition.asInstanceOf[SpiWorkflow.StepTransition]
      transition.stepName shouldBe "judge"
      val instruction =
        serializer.fromBytes(classOf[TranscriptQualityEvaluator.JudgeInstruction], transition.input.get)
      instruction.minWords() shouldBe 5
    }

    "complete the evaluation by transitioning to the built-in record step" in {
      val impl = newImpl()
      val started = await(impl.handleEvaluationStart(None, trigger()))
      val startState = started
        .asInstanceOf[SpiWorkflow.CommandTransitionalEffect]
        .persistence
        .asInstanceOf[SpiWorkflow.UpdateState]
        .newState
      val fetched = await(impl.invokeStep(Some(startState), stepCommand("fetchTranscript")))
        .asInstanceOf[SpiWorkflow.StepTransitionalEffect]
      val fetchedState = fetched.persistence.asInstanceOf[SpiWorkflow.UpdateState].newState
      val judgeInput = fetched.transition.asInstanceOf[SpiWorkflow.StepTransition].input

      val result = await(impl.invokeStep(Some(fetchedState), stepCommand("judge", judgeInput)))
        .asInstanceOf[SpiWorkflow.StepTransitionalEffect]

      val transition = result.transition.asInstanceOf[SpiWorkflow.StepTransition]
      transition.stepName shouldBe RecordStepName

      val outcome = serializer.fromBytes(classOf[Outcome], transition.input.get)
      outcome.kind() shouldBe Outcome.Kind.COMPLETED
      outcome.evaluations().size() shouldBe 1
      outcome.evaluations().get(0).passed() shouldBe true
    }

    "report inconclusive by transitioning to the built-in record step" in {
      val impl = newImpl()
      val started =
        await(impl.handleEvaluationStart(None, trigger(TranscriptQualityEvaluator.EMPTY_INTERACTION_ID)))
      val startState = started
        .asInstanceOf[SpiWorkflow.CommandTransitionalEffect]
        .persistence
        .asInstanceOf[SpiWorkflow.UpdateState]
        .newState

      val result = await(impl.invokeStep(Some(startState), stepCommand("fetchTranscript")))
        .asInstanceOf[SpiWorkflow.StepTransitionalEffect]

      val transition = result.transition.asInstanceOf[SpiWorkflow.StepTransition]
      transition.stepName shouldBe RecordStepName

      val outcome = serializer.fromBytes(classOf[Outcome], transition.input.get)
      outcome.kind() shouldBe Outcome.Kind.INCONCLUSIVE
      outcome.reason() should include(TranscriptQualityEvaluator.EMPTY_INTERACTION_ID)
    }

    "record the outcome via the SPI recorder and delete the instance in the built-in record step" in {
      val recorder = new RecorderProbe
      val impl = newImpl(recorder)
      // the record step rebuilds the trigger from the envelope persisted on start
      val started = await(impl.handleEvaluationStart(None, trigger()))
      val startState = started
        .asInstanceOf[SpiWorkflow.CommandTransitionalEffect]
        .persistence
        .asInstanceOf[SpiWorkflow.UpdateState]
        .newState
      val outcomePayload = serializer.toBytes(Outcome.inconclusive("no verdict"))

      val result = await(impl.invokeStep(Some(startState), stepCommand(RecordStepName, Some(outcomePayload))))
        .asInstanceOf[SpiWorkflow.StepTransitionalEffect]

      result.persistence shouldBe SpiWorkflow.NoPersistence
      result.transition shouldBe SpiWorkflow.Delete

      val (recordedTrigger, recordedResult) = recorder.recorded.value
      recordedTrigger.id shouldBe evaluationId
      recordedTrigger.source shouldBe SpiEvaluator.TriggerSource.OnInteraction
      recordedTrigger.subject.asInstanceOf[SpiEvaluator.Interaction].interactionId shouldBe "interaction-1"
      recordedResult shouldBe a[spi.SpiEvaluator.InconclusiveResult]
      recordedResult.asInstanceOf[spi.SpiEvaluator.InconclusiveResult].reason shouldBe "no verdict"
    }

    "record a completed outcome with its evaluations" in {
      val recorder = new RecorderProbe
      val impl = newImpl(recorder)
      val started = await(impl.handleEvaluationStart(None, trigger()))
      val startState = started
        .asInstanceOf[SpiWorkflow.CommandTransitionalEffect]
        .persistence
        .asInstanceOf[SpiWorkflow.UpdateState]
        .newState
      val fetched = await(impl.invokeStep(Some(startState), stepCommand("fetchTranscript")))
        .asInstanceOf[SpiWorkflow.StepTransitionalEffect]
      val fetchedState = fetched.persistence.asInstanceOf[SpiWorkflow.UpdateState].newState
      val judgeInput = fetched.transition.asInstanceOf[SpiWorkflow.StepTransition].input
      val judged = await(impl.invokeStep(Some(fetchedState), stepCommand("judge", judgeInput)))
        .asInstanceOf[SpiWorkflow.StepTransitionalEffect]
      val recordInput = judged.transition.asInstanceOf[SpiWorkflow.StepTransition].input

      await(impl.invokeStep(Some(fetchedState), stepCommand(RecordStepName, recordInput)))

      val (recordedTrigger, recordedResult) = recorder.recorded.value
      recordedTrigger.id shouldBe evaluationId
      val completed = recordedResult.asInstanceOf[spi.SpiEvaluator.CompletedResult]
      completed.evaluations should have size 1
      completed.evaluations.head.passed shouldBe true
    }

    "reject all commands, command handling is built in" in {
      val command = new SpiEntity.Command(
        "someUserCommand",
        None,
        sequenceNumber = 0L,
        isDeleted = false,
        metadata = SpiMetadata.empty,
        telemetryContext = null)
      val failure = newImpl().handleCommand(None, command).failed
      await(failure) shouldBe an[IllegalArgumentException]
    }

    "derive the workflow configuration from the evaluator settings" in {
      val config = newImpl().configuration

      config.workflowTimeout shouldBe Some(10.minutes)
      config.defaultStepRecoverStrategy.get.maxRetries shouldBe 2
      // retries exhausted or evaluation timeout: fail over to the record step, so every
      // evaluation terminates with a recorded outcome
      config.defaultStepRecoverStrategy.get.failoverTo.stepName shouldBe RecordStepName
      config.failoverRecoverStrategy.get.failoverTo.stepName shouldBe RecordStepName
      val failoverOutcome =
        serializer.fromBytes(classOf[Outcome], config.failoverRecoverStrategy.get.failoverTo.input.get)
      failoverOutcome.kind() shouldBe Outcome.Kind.FAILED
    }

    "retry the record step forever" in {
      val config = newImpl().configuration

      // recording is idempotent, a transient failure must never fail over (which would overwrite
      // the actual outcome) nor give up and leave the evaluation unrecorded
      val recordStepStrategy = config.stepConfigs(RecordStepName).recoveryStrategy.get
      recordStepStrategy.maxRetries shouldBe Int.MaxValue

      // same for the failover record step running after an evaluation timeout
      config.failoverRecoverStrategy.get.maxRetries shouldBe Int.MaxValue
    }
  }
}
