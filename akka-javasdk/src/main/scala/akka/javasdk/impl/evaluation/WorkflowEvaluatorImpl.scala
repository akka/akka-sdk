/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation

import java.lang.reflect.Method

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters.JavaDurationOps
import scala.jdk.OptionConverters._

import akka.Done
import akka.annotation.InternalApi
import akka.javasdk.evaluation.EvaluationContext
import akka.javasdk.evaluation.ExperimentContext
import akka.javasdk.evaluation.Interaction
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
import akka.javasdk.impl.evaluation.WorkflowEvaluatorProtocol.TriggerSource
import akka.javasdk.impl.serialization.Serializer
import akka.javasdk.impl.workflow.WorkflowDescriptor
import akka.javasdk.ledger.LedgerClient
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

  final class EvaluationContextImpl(
      id: String,
      evaluationSubject: Subject,
      experimentMembership: Option[WorkflowEvaluatorProtocol.ExperimentMembershipData],
      ledgerClient: LedgerClient)
      extends EvaluationContext {
    override def subject(): Subject = evaluationSubject
    override def evaluationId(): String = id

    private lazy val resolvedInteraction: java.util.Optional[Interaction] =
      evaluationSubject match {
        case i: Subject.Interaction =>
          java.util.Optional.of(new InteractionRecordAdapter(ledgerClient.getInteraction(i.interactionId())))
        case _ => java.util.Optional.empty()
      }

    override def interaction(): java.util.Optional[Interaction] = resolvedInteraction

    override def experiment(): java.util.Optional[ExperimentContext] =
      experimentMembership.map(_.toExperimentContext()).toJava
  }

  private def toProtocolTriggerSource(source: SpiEvaluator.TriggerSource): TriggerSource =
    source match {
      case SpiEvaluator.TriggerSource.Manual                => TriggerSource.MANUAL
      case SpiEvaluator.TriggerSource.OnInteraction         => TriggerSource.ON_INTERACTION
      case SpiEvaluator.TriggerSource.OnExperimentItem      => TriggerSource.EXPERIMENT_ITEM
      case SpiEvaluator.TriggerSource.OnExperimentCompleted => TriggerSource.EXPERIMENT_COMPLETED
    }

  private def toSpiTriggerSource(source: TriggerSource): SpiEvaluator.TriggerSource =
    source match {
      case TriggerSource.MANUAL               => SpiEvaluator.TriggerSource.Manual
      case TriggerSource.ON_INTERACTION       => SpiEvaluator.TriggerSource.OnInteraction
      case TriggerSource.EXPERIMENT_ITEM      => SpiEvaluator.TriggerSource.OnExperimentItem
      case TriggerSource.EXPERIMENT_COMPLETED => SpiEvaluator.TriggerSource.OnExperimentCompleted
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
    recorder: SpiEvaluator.EvaluationRecorder,
    serializer: Serializer,
    sdkExecutionContext: ExecutionContext,
    ledgerClient: LedgerClient)
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

    // the record step retries forever
    val recordStepConfig = new SpiWorkflow.StepConfig(
      RecordStepName,
      stepTimeout = None,
      recoveryStrategy = Some(recordFailure(Int.MaxValue)))

    new SpiWorkflow.WorkflowConfig(
      workflowTimeout = settings.evaluationTimeout().toScala.map(_.toScala),
      // after an evaluation timeout the failover record step must also keep retrying forever
      failoverRecoverStrategy = Some(recordFailure(Int.MaxValue)),
      defaultStepTimeout = settings.defaultStepTimeout().toScala.map(_.toScala),
      defaultStepRecoverStrategy = Some(
        recordFailure(settings.maxStepRetries().toScala.map(_.intValue()).getOrElse(0))),
      stepConfigs = Map(RecordStepName -> recordStepConfig),
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
      val subject = SubjectConversions.toSdkSubject(trigger.subject)
      val triggerSource = toProtocolTriggerSource(trigger.source)
      val experimentMembership = toProtocolExperimentMembership(trigger.experimentMembership)
      Future {
        val evaluator = factory()
        val context = new EvaluationContextImpl(workflowId, subject, experimentMembership, ledgerClient)
        evaluator._internalSetup(evaluator.emptyState(), context)
        val effect = evaluator.onEvaluation(context)
        effect match {
          case EffectImpl(persistence, transition) =>
            // the envelope is always persisted on start, even without a state update, so the
            // trigger survives recovery and a duplicate start is detected
            val newUserState = persistence match {
              case UpdateState(newState) => Some(newState)
              case NoPersistence         => None
            }
            new SpiWorkflow.CommandTransitionalEffect(
              envelopePersistence(triggerSource, subject, experimentMembership, newUserState),
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
      stepCommand: SpiWorkflow.StepCommand): Future[SpiWorkflow.StepResult] = {
    // the envelope is persisted on start, before any step can run, so it is always there
    val envelope = decodeEnvelope(userState).getOrElse {
      throw new IllegalStateException(
        s"Evaluation [$workflowId] has no state for step [${stepCommand.stepName}], was the evaluation started by the runtime?")
    }
    if (stepCommand.stepName == RecordStepName) {
      recordOutcome(envelope, stepCommand.input)
    } else {
      val stepMethod = stepMethods.getOrElse(
        stepCommand.stepName,
        throw new IllegalArgumentException(
          s"Step [${stepCommand.stepName}] not found in WorkflowEvaluator [${evaluatorClass.getName}], " +
          s"known steps: [${stepMethods.keys.mkString(", ")}]"))

      Future {
        val evaluator = factory()
        val subject = envelope.getSubject
        evaluator._internalSetup(
          decodeUserState(envelope),
          new EvaluationContextImpl(workflowId, subject, Option(envelope.experimentMembership()), ledgerClient))
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
            new SpiWorkflow.StepTransitionalEffect(
              toSpiPersistence(persistence, envelope.triggerSource(), subject, Option(envelope.experimentMembership())),
              toSpiTransition(transition))
        }
      }
    }
  }

  private def recordOutcome(envelope: StateEnvelope, input: Option[BytesPayload]): Future[SpiWorkflow.StepResult] = {
    val outcome = input match {
      case Some(payload) => serializer.fromBytes(classOf[Outcome], payload)
      case None          => throw new IllegalStateException(s"Missing outcome input for [$RecordStepName] step")
    }
    // the trigger the evaluation was started with, rebuilt from the persisted envelope, so the
    // recorded result is linked to the evaluated interaction
    val trigger = new SpiEvaluator.Trigger(
      workflowId,
      toSpiTriggerSource(envelope.triggerSource()),
      SubjectConversions.toSpiSubject(envelope.getSubject),
      Option(envelope.experimentMembership()).map(toSpiExperimentMembership))
    // recording is idempotent on the evaluation id: this step is retried until it succeeds
    recorder.recordResult(trigger, toSpiResult(outcome)).map { _ =>
      log.debug("Evaluation [{}] finished with [{}]", workflowId, outcome.kind())
      //TODO end or delete or a setting for that?
      new SpiWorkflow.StepTransitionalEffect(SpiWorkflow.NoPersistence, SpiWorkflow.Delete)
    }
  }

  private def toSpiResult(outcome: Outcome): SpiEvaluator.Result =
    outcome.kind() match {
      case Outcome.Kind.COMPLETED =>
        new SpiEvaluator.CompletedResult(toSpiEvaluation(outcome.evaluation()))
      case Outcome.Kind.INCONCLUSIVE => new SpiEvaluator.InconclusiveResult(outcome.reason())
      case Outcome.Kind.FAILED       => new SpiEvaluator.FailedResult(outcome.reason())
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
      triggerSource: TriggerSource,
      subject: Subject,
      experimentMembership: Option[WorkflowEvaluatorProtocol.ExperimentMembershipData]): SpiWorkflow.Persistence =
    persistence match {
      case UpdateState(newState) =>
        envelopePersistence(triggerSource, subject, experimentMembership, Some(newState))
      case NoPersistence => SpiWorkflow.NoPersistence
    }

  private def envelopePersistence(
      triggerSource: TriggerSource,
      subject: Subject,
      experimentMembership: Option[WorkflowEvaluatorProtocol.ExperimentMembershipData],
      userState: Option[Any]): SpiWorkflow.UpdateState = {
    val envelope = userState match {
      case Some(state) =>
        val payload = serializer.toBytes(state)
        StateEnvelope.of(
          triggerSource,
          subject,
          experimentMembership.orNull,
          payload.bytes.toArrayUnsafe(),
          payload.contentType)
      case None =>
        StateEnvelope.of(triggerSource, subject, experimentMembership.orNull, null, null)
    }
    new SpiWorkflow.UpdateState(serializer.toBytes(envelope))
  }

  private def toProtocolExperimentMembership(membership: Option[SpiEvaluator.ExperimentMembership])
      : Option[WorkflowEvaluatorProtocol.ExperimentMembershipData] =
    membership.map(m =>
      new WorkflowEvaluatorProtocol.ExperimentMembershipData(
        m.experimentId,
        m.datasetId,
        m.datasetItemId,
        m.agentRepetition,
        m.judgeRepetition))

  private def toSpiExperimentMembership(
      data: WorkflowEvaluatorProtocol.ExperimentMembershipData): SpiEvaluator.ExperimentMembership =
    new SpiEvaluator.ExperimentMembership(
      data.experimentId(),
      data.datasetId(),
      data.datasetItemId(),
      data.agentRepetition(),
      data.judgeRepetition())

  private def toSpiTransition(transition: WorkflowEvaluatorEffects.Transition): SpiWorkflow.Transition =
    transition match {
      case StepTransition(stepName, input, declaringClass) =>
        if (!declaringClass.isAssignableFrom(evaluatorClass)) {
          throw new IllegalArgumentException(
            s"WorkflowEvaluator [${evaluatorClass.getName}] transitions to step [$stepName] from another class " +
            s"[${declaringClass.getName}], which is not allowed.")
        }
        new SpiWorkflow.StepTransition(stepName, input.map(serializer.toBytes))
      case CompleteTransition(evaluation) =>
        new SpiWorkflow.StepTransition(RecordStepName, Some(serializer.toBytes(Outcome.completed(evaluation))))
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
