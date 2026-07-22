/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation;

import akka.annotation.InternalApi;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.Subject;
import java.util.List;
import java.util.Map;

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

  public record StateEnvelope(
      TriggerSource triggerSource,
      String flowId,
      String agentComponentId,
      String interactionId,
      byte[] userState,
      String userStateContentType) {

    public Subject getSubject() {
      if (flowId != null)
        return new Subject.FlowInteraction(flowId, agentComponentId, interactionId);
      else return new Subject.AgentInteraction(agentComponentId, interactionId);
    }

    public static StateEnvelope of(
        TriggerSource triggerSource,
        Subject subject,
        byte[] userState,
        String userStateContentType) {
      return switch (subject) {
        case Subject.FlowInteraction flow ->
            new StateEnvelope(
                triggerSource,
                flow.flowId(),
                flow.agentComponentId(),
                flow.interactionId(),
                userState,
                userStateContentType);
        case Subject.AgentInteraction agent ->
            new StateEnvelope(
                triggerSource,
                null,
                agent.agentComponentId(),
                agent.interactionId(),
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
