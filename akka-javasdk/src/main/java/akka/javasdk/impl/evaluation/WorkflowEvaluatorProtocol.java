/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation;

import akka.annotation.InternalApi;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.Subject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * INTERNAL API
 *
 * <p>SDK-internal serialized shapes of the workflow evaluator: the persisted state envelope
 * wrapping the user state, and the evaluation outcome carried as input to the built-in record step.
 * Both are written and read only by the SDK — the start of an evaluation and the recording of its
 * result cross the runtime boundary as structured types on the SPI ({@code SpiWorkflowEvaluator}).
 */
@InternalApi
public final class WorkflowEvaluatorProtocol {

  private WorkflowEvaluatorProtocol() {}

  /** What created the trigger the evaluation was started with. */
  public enum TriggerSource {
    MANUAL,
    ON_INTERACTION
  }

  /** Which {@link Subject} variant a persisted {@link StateEnvelope} carries. */
  public enum SubjectKind {
    INTERACTION,
    FLOW,
    SESSION,
    EVALUATED_EVALUATION,
    EXPERIMENT
  }

  public record StateEnvelope(
      TriggerSource triggerSource,
      SubjectKind subjectKind,
      String subjectId,
      String agentComponentId,
      String flowId,
      byte[] userState,
      String userStateContentType) {

    public Subject getSubject() {
      return switch (subjectKind) {
        case INTERACTION ->
            new Subject.Interaction(
                subjectId, Optional.ofNullable(agentComponentId), Optional.ofNullable(flowId));
        case FLOW -> new Subject.Flow(subjectId);
        case SESSION -> new Subject.Session(subjectId);
        case EVALUATED_EVALUATION -> new Subject.EvaluatedEvaluation(subjectId);
        case EXPERIMENT -> new Subject.Experiment(subjectId);
      };
    }

    public static StateEnvelope of(
        TriggerSource triggerSource,
        Subject subject,
        byte[] userState,
        String userStateContentType) {
      return switch (subject) {
        case Subject.Interaction i ->
            new StateEnvelope(
                triggerSource,
                SubjectKind.INTERACTION,
                i.interactionId(),
                i.agentComponentId().orElse(null),
                i.flowId().orElse(null),
                userState,
                userStateContentType);
        case Subject.Flow f ->
            new StateEnvelope(
                triggerSource,
                SubjectKind.FLOW,
                f.flowId(),
                null,
                null,
                userState,
                userStateContentType);
        case Subject.Session s ->
            new StateEnvelope(
                triggerSource,
                SubjectKind.SESSION,
                s.sessionId(),
                null,
                null,
                userState,
                userStateContentType);
        case Subject.EvaluatedEvaluation e ->
            new StateEnvelope(
                triggerSource,
                SubjectKind.EVALUATED_EVALUATION,
                e.evaluationId(),
                null,
                null,
                userState,
                userStateContentType);
        case Subject.Experiment x ->
            new StateEnvelope(
                triggerSource,
                SubjectKind.EXPERIMENT,
                x.experimentId(),
                null,
                null,
                userState,
                userStateContentType);
      };
    }
  }

  /** The terminal outcome of an evaluation, input to the built-in record step. */
  public record Outcome(Kind kind, List<EvaluationData> evaluations, String reason) {

    public enum Kind {
      COMPLETED,
      INCONCLUSIVE,
      FAILED
    }

    public static Outcome completed(List<Evaluation> evaluations) {
      return new Outcome(
          Kind.COMPLETED, evaluations.stream().map(EvaluationData::from).toList(), null);
    }

    public static Outcome inconclusive(String reason) {
      return new Outcome(Kind.INCONCLUSIVE, List.of(), reason);
    }

    public static Outcome failed(String reason) {
      return new Outcome(Kind.FAILED, List.of(), reason);
    }
  }

  /** Serializable shape of {@link Evaluation}. */
  public record EvaluationData(
      boolean passed,
      String explanation,
      Double score,
      String label,
      Map<String, String> attributes) {

    public static EvaluationData from(Evaluation evaluation) {
      return new EvaluationData(
          evaluation.passed(),
          evaluation.explanation(),
          evaluation.score().orElse(null),
          evaluation.label().orElse(null),
          evaluation.attributes());
    }
  }
}
