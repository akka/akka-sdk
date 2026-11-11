/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation;

import akka.annotation.InternalApi;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.Subject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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

  /**
   * The persisted state of one evaluation: the trigger it was started with and the user state. An
   * envelope persisted by SDK 3.7 holds the interaction subject as the flat fields {@code
   * interactionId}, {@code agentComponentId} and {@code flowId} and no attributes.
   */
  public record StateEnvelope(
      TriggerSource triggerSource,
      SubjectData subject,
      Map<String, String> attributes,
      byte[] userState,
      String userStateContentType) {

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    static StateEnvelope fromJson(
        @JsonProperty("triggerSource") TriggerSource triggerSource,
        @JsonProperty("subject") SubjectData subject,
        @JsonProperty("attributes") Map<String, String> attributes,
        @JsonProperty("interactionId") String interactionId,
        @JsonProperty("agentComponentId") String agentComponentId,
        @JsonProperty("flowId") String flowId,
        @JsonProperty("userState") byte[] userState,
        @JsonProperty("userStateContentType") String userStateContentType) {
      var resolvedSubject =
          subject != null
              ? subject
              : new SubjectData.Interaction(interactionId, agentComponentId, flowId);
      return new StateEnvelope(
          triggerSource,
          resolvedSubject,
          attributes != null ? attributes : Map.of(),
          userState,
          userStateContentType);
    }

    public static StateEnvelope of(
        TriggerSource triggerSource,
        Subject subject,
        Map<String, String> attributes,
        byte[] userState,
        String userStateContentType) {
      return new StateEnvelope(
          triggerSource, SubjectData.of(subject), attributes, userState, userStateContentType);
    }
  }

  /**
   * Serializable shape of {@link Subject}, one record per subject kind. A subject without a type
   * tag, as SDK 3.7 persisted it, is an interaction.
   */
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      property = "type",
      defaultImpl = SubjectData.Interaction.class)
  @JsonSubTypes({
    @JsonSubTypes.Type(value = SubjectData.Interaction.class, name = "I"),
    @JsonSubTypes.Type(value = SubjectData.Workflow.class, name = "W")
  })
  public sealed interface SubjectData {

    Subject toSubject();

    static SubjectData of(Subject subject) {
      // Exhaustive over Subject: a new subject kind does not compile until it is mapped here.
      return switch (subject) {
        case Subject.Interaction interaction ->
            new Interaction(
                interaction.interactionId(),
                interaction.agentComponentId(),
                interaction.flowId().orElse(null));
        case Subject.Workflow workflow ->
            new Workflow(workflow.workflowId(), workflow.workflowComponentId());
      };
    }

    record Interaction(String interactionId, String agentComponentId, String flowId)
        implements SubjectData {
      @Override
      public Subject toSubject() {
        return new Subject.Interaction(
            interactionId, agentComponentId, Optional.ofNullable(flowId));
      }
    }

    record Workflow(String workflowId, String workflowComponentId) implements SubjectData {
      @Override
      public Subject toSubject() {
        return new Subject.Workflow(workflowId, workflowComponentId);
      }
    }
  }

  /** The terminal outcome of an evaluation, input to the built-in record step. */
  public record Outcome(Kind kind, EvaluationData evaluation, String reason) {

    public enum Kind {
      COMPLETED,
      INCONCLUSIVE,
      NOT_APPLICABLE,
      FAILED
    }

    public static Outcome completed(Evaluation evaluation) {
      return new Outcome(Kind.COMPLETED, EvaluationData.from(evaluation), null);
    }

    public static Outcome inconclusive(String reason) {
      return new Outcome(Kind.INCONCLUSIVE, null, reason);
    }

    public static Outcome notApplicable(String reason) {
      return new Outcome(Kind.NOT_APPLICABLE, null, reason);
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
