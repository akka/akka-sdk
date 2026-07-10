/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import java.lang.reflect.Method

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters.JavaDurationOps
import scala.jdk.OptionConverters.RichOptional

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.Subject
import akka.javasdk.evaluation.WorkflowEvaluator
import akka.javasdk.impl.ErrorHandling
import akka.javasdk.impl.evaluation.WorkflowEvaluatorEffects.CompleteTransition
import akka.javasdk.impl.evaluation.WorkflowEvaluatorEffects.EffectImpl
import akka.javasdk.impl.evaluation.WorkflowEvaluatorEffects.InconclusiveTransition
import akka.javasdk.impl.evaluation.WorkflowEvaluatorEffects.NoPersistence
import akka.javasdk.impl.evaluation.WorkflowEvaluatorEffects.StepTransition
import akka.javasdk.impl.evaluation.WorkflowEvaluatorEffects.UpdateState
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.EvaluationData
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.Outcome
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.StateEnvelope
import akka.javasdk.impl.serialization.Serializer
import akka.javasdk.impl.workflow.WorkflowDescriptor
import akka.runtime.sdk.spi.BytesPayload
import akka.runtime.sdk.spi.SpiEntity
import akka.runtime.sdk.spi.SpiEvaluator
import akka.runtime.sdk.spi.SpiMetadata
import akka.runtime.sdk.spi.SpiWorkflow
import akka.runtime.sdk.spi.SpiWorkflowEvaluator
import akka.util.ByteString
import org.slf4j.LoggerFactory

/**
 * INTERNAL API
 */
@InternalApi
private[javasdk] object WorkflowEvaluatorImpl {

  /**
   * Built-in final step: records the evaluation outcome with the runtime and deletes the instance. Also, the failover
   * target when a step exhausts its retries or the evaluation times out, so every run terminates with a recorded
   * outcome.
   */
  val RecordStepName = "_record-evaluation"

  final class EvaluationContextImpl(id: String, evaluationSubject: Subject) extends EvaluationContext {
    override def subject(): Subject = evaluationSubject
    override def evaluationId(): String = id
  }

  private def toSdkSubject(subject: SpiEvaluator.Subject): Subject =
    subject match {
      case flow: SpiEvaluator.FlowInteraction =>
        new Subject.FlowInteraction(flow.flowId, flow.agentComponentId, flow.interactionId)
      case agent: SpiEvaluator.AgentInteraction =>
        new Subject.AgentInteraction(agent.agentComponentId, agent.interactionId)
    }
}

/**
 * INTERNAL API
 *
 * Adapts a user [[WorkflowEvaluator]] to the [[SpiWorkflowEvaluator]] expected by the runtime. The evaluator is hosted
 * as a workflow — one instance per evaluation, started by the runtime with the structured trigger — with a built-in
 * final step that records the outcome and deletes the instance.
 */
@InternalApi
private[javasdk] final class WorkflowEvaluatorImpl[S, E <: WorkflowEvaluator[S]](
    workflowId: String,
    evaluatorClass: Class[E],
    stateClass: Class[S],
    factory: () => E,
    recorder: SpiWorkflowEvaluator.EvaluationRecorder,
    serializer: Serializer,
    sdkExecutionContext: ExecutionContext)
    extends SpiWorkflowEvaluator {

  import WorkflowEvaluatorImpl._

  private val log = LoggerFactory.getLogger(evaluatorClass)
  private implicit val executionContext: ExecutionContext = sdkExecutionContext

  // step methods: zero or one arg methods returning WorkflowEvaluator.Effect, defined in the
  // evaluator class or inherited (onEvaluation is the built-in entry point, not a step)
  private val stepMethods: Map[String, Method] = {
    def allMethods(c: Class[_]): Seq[Method] =
      if (c == null || c == classOf[Object]) Seq.empty
      else c.getDeclaredMethods.toSeq ++ allMethods(c.getSuperclass)

    allMethods(evaluatorClass)
      .filter { m =>
        m.getReturnType == classOf[WorkflowEvaluator.Effect] &&
        m.getParameterCount <= 1 &&
        !m.isSynthetic &&
        m.getName != "onEvaluation"
      }
      .map(m => WorkflowDescriptor.stepMethodName(m) -> m)
      .toMap
  }

  private val failedOutcomePayload =
    serializer.toBytes(Outcome.failed("Evaluation failed"))

  override def configuration: SpiWorkflow.WorkflowConfig = {
    val settings = factory().settings()

    // a step that exhausts its retries fails over to the record step, so the failure is recorded
    // and the instance cleaned up rather than the workflow staying failed forever
    def recordFailure(maxRetries: Int) =
      new SpiWorkflow.RecoverStrategy(
        maxRetries,
        failoverTo = new SpiWorkflow.StepTransition(RecordStepName, Some(failedOutcomePayload)))

    new SpiWorkflow.WorkflowConfig(
      workflowTimeout = settings.evaluationTimeout().toScala.map(_.toScala),
      failoverRecoverStrategy = Some(recordFailure(0)),
      defaultStepTimeout = settings.defaultStepTimeout().toScala.map(_.toScala),
      defaultStepRecoverStrategy = Some(
        recordFailure(settings.maxStepRetries().toScala.map(_.intValue()).getOrElse(0))),
      stepConfigs = Map.empty,
      passivationDelay = None)
  }

  override def handleEvaluationStart(
      state: Option[SpiWorkflow.State],
      trigger: SpiEvaluator.Trigger): Future[SpiWorkflow.CommandEffect] = {
    val ack = serializer.toBytes(Done.getInstance())
    if (state.exists(_.nonEmpty)) {
      // at-least-once delivery from the trigger projection: ack the duplicate start so it can advance
      Future.successful(new SpiWorkflow.ReadOnlyEffect(ack, SpiMetadata.empty))
    } else {
      val subject = toSdkSubject(trigger.subject)
      Future {
        val evaluator = factory()
        val context = new EvaluationContextImpl(workflowId, subject)
        evaluator._internalSetup(evaluator.emptyState(), context)
        val effect = evaluator.onEvaluation(context)
        effect match {
          case EffectImpl(persistence, transition) =>
            // the envelope is always persisted on start, even without a state update, so the
            // subject survives recovery and a duplicate start is detected
            val newUserState = persistence match {
              case UpdateState(newState) => Some(newState)
              case NoPersistence         => None
            }
            new SpiWorkflow.CommandTransitionalEffect(
              envelopePersistence(subject, newUserState),
              toSpiTransition(transition),
              ack,
              SpiMetadata.empty)
        }
      }
    }
  }

  override def handleCommand(
      userState: Option[SpiWorkflow.State],
      command: SpiEntity.Command): Future[SpiWorkflow.CommandEffect] =
    // command handling is built in: the runtime starts the evaluation via handleEvaluationStart,
    // there are no user-defined command handlers
    Future.failed(
      new IllegalArgumentException(
        s"Unexpected command [${command.name}] for WorkflowEvaluator [${evaluatorClass.getName}], " +
        "a workflow evaluator does not accept commands"))

  override def invokeStep(
      userState: Option[BytesPayload],
      stepCommand: SpiWorkflow.StepCommand): Future[SpiWorkflow.StepResult] =
    if (stepCommand.stepName == RecordStepName) {
      recordOutcome(stepCommand.input)
    } else {
      val envelope = decodeEnvelope(userState).getOrElse {
        throw new IllegalStateException(
          s"Evaluation [$workflowId] has no state for step [${stepCommand.stepName}], was the evaluation started by the runtime?")
      }
      val stepMethod = stepMethods.getOrElse(
        stepCommand.stepName,
        throw new IllegalArgumentException(
          s"Step [${stepCommand.stepName}] not found in WorkflowEvaluator [${evaluatorClass.getName}], " +
          s"known steps: [${stepMethods.keys.mkString(", ")}]"))

      Future {
        val evaluator = factory()
        val subject = envelope.getSubject
        evaluator._internalSetup(decodeUserState(envelope), new EvaluationContextImpl(workflowId, subject))
        val effect =
          if (stepMethod.getParameterCount == 1) {
            val input = stepCommand.input match {
              case Some(payload) => serializer.fromBytes(stepMethod.getParameterTypes()(0), payload)
              case None          => null
            }
            invokeStepMethod(stepMethod, evaluator, Some(input))
          } else {
            invokeStepMethod(stepMethod, evaluator, None)
          }
        effect match {
          case EffectImpl(persistence, transition) =>
            new SpiWorkflow.StepTransitionalEffect(toSpiPersistence(persistence, subject), toSpiTransition(transition))
        }
      }
    }

  private def recordOutcome(input: Option[BytesPayload]): Future[SpiWorkflow.StepResult] = {
    val outcome = input match {
      case Some(payload) => serializer.fromBytes(classOf[Outcome], payload)
      case None          => throw new IllegalStateException(s"Missing outcome input for [$RecordStepName] step")
    }
    // recording is idempotent on the evaluation id: this step is retried until it succeeds
    recorder.recordResult(workflowId, toSpiResult(outcome)).map { _ =>
      log.debug("Evaluation [{}] finished with [{}]", workflowId, outcome.kind())
      //TODO end or delete or a setting for that?
      new SpiWorkflow.StepTransitionalEffect(SpiWorkflow.NoPersistence, SpiWorkflow.Delete)
    }
  }

  private def toSpiResult(outcome: Outcome): SpiWorkflowEvaluator.Result =
    outcome.kind() match {
      case Outcome.Kind.COMPLETED =>
        new SpiWorkflowEvaluator.CompletedResult(outcome.evaluations().asScala.toSeq.map(toSpiEvaluation))
      case Outcome.Kind.INCONCLUSIVE => new SpiWorkflowEvaluator.InconclusiveResult(outcome.reason())
      case Outcome.Kind.FAILED       => new SpiWorkflowEvaluator.FailedResult(outcome.reason())
    }

  private def toSpiEvaluation(data: EvaluationData): SpiEvaluator.Evaluation =
    new SpiEvaluator.Evaluation(
      data.passed(),
      data.explanation(),
      Option(data.score()).map(_.doubleValue()),
      Option(data.label()),
      data.attributes().asScala.toMap)

  private def invokeStepMethod(method: Method, evaluator: E, input: Option[Any]): WorkflowEvaluator.Effect =
    try {
      method.setAccessible(true)
      input match {
        case Some(value) => method.invoke(evaluator, value.asInstanceOf[AnyRef]).asInstanceOf[WorkflowEvaluator.Effect]
        case None        => method.invoke(evaluator).asInstanceOf[WorkflowEvaluator.Effect]
      }
    } catch {
      case e: java.lang.reflect.InvocationTargetException =>
        throw ErrorHandling.unwrapInvocationTargetException(e)
    }

  private def toSpiPersistence(
      persistence: WorkflowEvaluatorEffects.Persistence[Any],
      subject: Subject): SpiWorkflow.Persistence =
    persistence match {
      case UpdateState(newState) => envelopePersistence(subject, Some(newState))
      case NoPersistence         => SpiWorkflow.NoPersistence
    }

  private def envelopePersistence(subject: Subject, userState: Option[Any]): SpiWorkflow.UpdateState = {
    val envelope = userState match {
      case Some(state) =>
        val payload = serializer.toBytes(state)
        StateEnvelope.of(subject, payload.bytes.toArrayUnsafe(), payload.contentType)
      case None =>
        StateEnvelope.of(subject, null, null)
    }
    new SpiWorkflow.UpdateState(serializer.toBytes(envelope))
  }

  private def toSpiTransition(transition: WorkflowEvaluatorEffects.Transition): SpiWorkflow.Transition =
    transition match {
      case StepTransition(stepName, input, declaringClass) =>
        if (!declaringClass.isAssignableFrom(evaluatorClass)) {
          throw new IllegalArgumentException(
            s"WorkflowEvaluator [${evaluatorClass.getName}] transitions to step [$stepName] from another class " +
            s"[${declaringClass.getName}], which is not allowed.")
        }
        new SpiWorkflow.StepTransition(stepName, input.map(serializer.toBytes))
      case CompleteTransition(evaluations) =>
        new SpiWorkflow.StepTransition(RecordStepName, Some(serializer.toBytes(Outcome.completed(evaluations.asJava))))
      case InconclusiveTransition(reason) =>
        new SpiWorkflow.StepTransition(RecordStepName, Some(serializer.toBytes(Outcome.inconclusive(reason))))
    }

  private def decodeEnvelope(userState: Option[BytesPayload]): Option[StateEnvelope] =
    userState.collect {
      case payload if payload.nonEmpty => serializer.fromBytes(classOf[StateEnvelope], payload)
    }

  private def decodeUserState(envelope: StateEnvelope): S =
    if (envelope.userState() == null) factory().emptyState()
    else
      serializer.fromBytes(
        stateClass,
        new BytesPayload(ByteString.fromArrayUnsafe(envelope.userState()), envelope.userStateContentType()))

  // deprecated SPI methods, not used by the current runtime protocol
  override def executeStep(
      stepName: String,
      input: Option[BytesPayload],
      userState: Option[BytesPayload]): Future[BytesPayload] =
    Future.failed(new UnsupportedOperationException(s"executeStep is not supported for WorkflowEvaluator"))

  override def transition(
      stepName: String,
      result: Option[BytesPayload],
      userState: Option[BytesPayload]): Future[SpiWorkflow.TransitionalOnlyEffect] =
    Future.failed(new UnsupportedOperationException(s"transition is not supported for WorkflowEvaluator"))
}
