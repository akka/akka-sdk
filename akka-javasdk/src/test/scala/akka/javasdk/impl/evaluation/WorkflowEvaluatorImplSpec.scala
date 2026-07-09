/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.javasdk.evaluation.TranscriptQualityEvaluator
import akka.javasdk.impl.evaluation.WorkflowEvaluatorImpl.OnEvaluationCommandName
import akka.javasdk.impl.evaluation.WorkflowEvaluatorImpl.RecordStepName
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.Outcome
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.StartEvaluation
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.StateEnvelope
import akka.javasdk.impl.serialization.Serializer
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiWorkflow
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorkflowEvaluatorImplSpec extends AnyWordSpec with Matchers {

  private val serializer = new Serializer
  private val evaluationId = "evaluation-1"

  private def newImpl() =
    new WorkflowEvaluatorImpl[TranscriptQualityEvaluator.State, TranscriptQualityEvaluator](
      evaluationId,
      classOf[TranscriptQualityEvaluator],
      classOf[TranscriptQualityEvaluator.State],
      () => new TranscriptQualityEvaluator,
      serializer,
      ExecutionContext.global)

  private def startCommand(interactionId: String = "interaction-1") =
    new SpiEntity.Command(
      OnEvaluationCommandName,
      Some(serializer.toBytes(new StartEvaluation(null, "my-agent", interactionId))),
      sequenceNumber = 0L,
      isDeleted = false,
      metadata = SpiMetadata.empty,
      telemetryContext = null)

  private def stepCommand(stepName: String, input: Option[BytesPayload] = None) =
    new SpiWorkflow.StepCommand(stepName, input, SpiMetadata.empty, null)

  private def await[T](future: Future[T]): T = Await.result(future, 3.seconds)

  private def decodeEnvelope(persistence: SpiWorkflow.Persistence): StateEnvelope = {
    persistence shouldBe an[SpiWorkflow.UpdateState]
    serializer.fromBytes(classOf[StateEnvelope], persistence.asInstanceOf[SpiWorkflow.UpdateState].newState)
  }

  "WorkflowEvaluatorImpl" should {

    "start the evaluation with the built-in command and persist the subject envelope" in {
      val effect = await(newImpl().handleCommand(None, startCommand()))

      effect shouldBe a[SpiWorkflow.CommandTransitionalEffect]
      val transitional = effect.asInstanceOf[SpiWorkflow.CommandTransitionalEffect]

      val envelope = decodeEnvelope(transitional.persistence)
      envelope.agentComponentId() shouldBe "my-agent"
      envelope.interactionId() shouldBe "interaction-1"
      envelope.userState() shouldBe null // no state update in onEvaluation, subject still persisted

      transitional.transition shouldBe a[SpiWorkflow.StepTransition]
      transitional.transition.asInstanceOf[SpiWorkflow.StepTransition].stepName shouldBe "fetchTranscript"
    }

    "ack a duplicate start without re-running the evaluation" in {
      val impl = newImpl()
      val started = await(impl.handleCommand(None, startCommand()))
      val persistedState = started
        .asInstanceOf[SpiWorkflow.CommandTransitionalEffect]
        .persistence
        .asInstanceOf[SpiWorkflow.UpdateState]
        .newState

      val effect = await(impl.handleCommand(Some(persistedState), startCommand()))
      effect shouldBe a[SpiWorkflow.ReadOnlyEffect]
    }

    "run a step that updates state and transitions with input" in {
      val impl = newImpl()
      val started = await(impl.handleCommand(None, startCommand()))
      val persistedState = started
        .asInstanceOf[SpiWorkflow.CommandTransitionalEffect]
        .persistence
        .asInstanceOf[SpiWorkflow.UpdateState]
        .newState

      val result = await(impl.invokeStep(Some(persistedState), stepCommand("fetchTranscript")))

      result shouldBe a[SpiWorkflow.StepTransitionalEffect]
      val stepEffect = result.asInstanceOf[SpiWorkflow.StepTransitionalEffect]

      val envelope = decodeEnvelope(stepEffect.persistence)
      envelope.interactionId() shouldBe "interaction-1"
      envelope.userState() should not be null

      val transition = stepEffect.transition.asInstanceOf[SpiWorkflow.StepTransition]
      transition.stepName shouldBe "judge"
      val instruction =
        serializer.fromBytes(classOf[TranscriptQualityEvaluator.JudgeInstruction], transition.input.get)
      instruction.minWords() shouldBe 5
    }

    "complete the evaluation by transitioning to the built-in record step" in {
      val impl = newImpl()
      val started = await(impl.handleCommand(None, startCommand()))
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
        await(impl.handleCommand(None, startCommand(TranscriptQualityEvaluator.EMPTY_INTERACTION_ID)))
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

    "record the outcome and delete the instance in the built-in record step" in {
      val outcomePayload = serializer.toBytes(Outcome.inconclusive("no verdict"))

      val result = await(newImpl().invokeStep(None, stepCommand(RecordStepName, Some(outcomePayload))))
        .asInstanceOf[SpiWorkflow.StepTransitionalEffect]

      result.persistence shouldBe SpiWorkflow.NoPersistence
      result.transition shouldBe SpiWorkflow.Delete
    }

    "reject unknown commands, command handling is built in" in {
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
  }
}
