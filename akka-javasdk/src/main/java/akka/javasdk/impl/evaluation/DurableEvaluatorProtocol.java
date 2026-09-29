/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation;

import akka.annotation.InternalApi;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.Subject;
import java.util.Map;
import java.util.Optional;

/**
 * INTERNAL API
 *
 * <p>SDK-internal serialized shapes of the durable evaluator: the persisted state envelope wrapping
 * the user state, and the evaluation outcome carried as input to the built-in record step. Both are
 * written and read only by the SDK — the start of an evaluation and the recording of its result
 * cross the runtime boundary as structured types on the SPI ({@code SpiWorkflowEvaluator}).
 */
@InternalApi
public final class DurableEvaluatorProtocol {

  private DurableEvaluatorProtocol() {}

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
      return new Subject.Interaction(interactionId, agentComponentId, Optional.ofNullable(flowId));
    }

    public static StateEnvelope of(
        TriggerSource triggerSource,
        Subject subject,
        byte[] userState,
        String userStateContentType) {
      // Exhaustive over Subject: a new subject kind does not compile until it is mapped here.
      return switch (subject) {
        case Subject.Interaction interaction ->
            new StateEnvelope(
                triggerSource,
                interaction.flowId().orElse(null),
                interaction.agentComponentId(),
                interaction.interactionId(),
                userState,
                userStateContentType);
      };
    }
  }

  /** The terminal outcome of an evaluation, input to the built-in record step. */
  public record Outcome(Kind kind, EvaluationData evaluation, String reason) {

    public enum Kind {
      COMPLETED,
      INCONCLUSIVE,
      FAILED
    }

    public static Outcome completed(Evaluation evaluation) {
      return new Outcome(Kind.COMPLETED, EvaluationData.from(evaluation), null);
    }

    public static Outcome inconclusive(String reason) {
      return new Outcome(Kind.INCONCLUSIVE, null, reason);
    }

    public static Outcome failed(String reason) {
      return new Outcome(Kind.FAILED, null, reason);
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
