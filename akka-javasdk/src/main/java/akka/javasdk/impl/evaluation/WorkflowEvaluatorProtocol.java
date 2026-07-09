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
 * <p>Serialized messages of the workflow evaluator: the start command, the state envelope wrapping
 * the user state, and the evaluation outcome carried to the built-in record step.
 *
 * <p>PROTOTYPE NOTE: JSON shapes for now; the actual protocol between the runtime trigger
 * projection and the evaluator workflow is to be defined on the SPI (protobuf), see
 * https://github.com/lightbend/akka-runtime/issues/5332.
 */
@InternalApi
public final class WorkflowEvaluatorProtocol {

  private WorkflowEvaluatorProtocol() {}

  /**
   * The command the runtime sends to start the evaluation. The evaluation id is the workflow id, so
   * only the subject is carried. {@code flowId} is null for a direct agent interaction.
   */
  public record StartEvaluation(String flowId, String agentComponentId, String interactionId) {

    public Subject toSubject() {
      if (flowId != null)
        return new Subject.FlowInteraction(flowId, agentComponentId, interactionId);
      else return new Subject.AgentInteraction(agentComponentId, interactionId);
    }

    public static StartEvaluation fromSubject(Subject subject) {
      return switch (subject) {
        case Subject.FlowInteraction flow ->
            new StartEvaluation(flow.flowId(), flow.agentComponentId(), flow.interactionId());
        case Subject.AgentInteraction agent ->
            new StartEvaluation(null, agent.agentComponentId(), agent.interactionId());
      };
    }
  }

  /**
   * The persisted state of an evaluation: the subject (so the {@code EvaluationContext} survives
   * recovery without the user copying it into their own state) and the user-defined state as nested
   * serialized bytes. {@code userState} is null until the first {@code updateState}.
   */
  public record StateEnvelope(
      String flowId,
      String agentComponentId,
      String interactionId,
      byte[] userState,
      String userStateContentType) {

    public Subject toSubject() {
      return new StartEvaluation(flowId, agentComponentId, interactionId).toSubject();
    }

    public static StateEnvelope of(Subject subject, byte[] userState, String userStateContentType) {
      var start = StartEvaluation.fromSubject(subject);
      return new StateEnvelope(
          start.flowId(),
          start.agentComponentId(),
          start.interactionId(),
          userState,
          userStateContentType);
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
