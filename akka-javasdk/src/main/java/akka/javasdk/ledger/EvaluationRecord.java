/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import akka.javasdk.evaluation.Evaluation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The record of a single evaluation, as fetched from the ledger.
 *
 * <p>An evaluation is recorded when it terminates, so a fetched record always carries a terminal
 * {@link Outcome}: the verdict it reached, or why it did not reach one.
 *
 * @param evaluationId the globally unique id of the evaluation
 * @param evaluatorComponentId the component id of the evaluator that ran the evaluation
 * @param controlId the control id that the evaluator declares, or empty when it declares none
 * @param trigger what caused the evaluation to run
 * @param subject what was evaluated
 * @param attributes the attributes the evaluation was started with, empty for an evaluation of an
 *     interaction of a bound agent
 * @param outcome the terminal outcome of the evaluation
 * @param timestamp when the evaluation was recorded
 */
public record EvaluationRecord(
    String evaluationId,
    String evaluatorComponentId,
    Optional<String> controlId,
    Trigger trigger,
    Subject subject,
    Map<String, String> attributes,
    Outcome outcome,
    Instant timestamp) {

  /**
   * @deprecated Use the constructor with a {@link Subject} and the attributes.
   */
  @Deprecated(since = "3.8.0")
  public EvaluationRecord(
      String evaluationId,
      String evaluatorComponentId,
      Optional<String> controlId,
      Trigger trigger,
      String interactionId,
      String agentComponentId,
      Outcome outcome,
      Instant timestamp) {
    this(
        evaluationId,
        evaluatorComponentId,
        controlId,
        trigger,
        new Subject.Interaction(interactionId, agentComponentId),
        Map.of(),
        outcome,
        timestamp);
  }

  /** What caused an evaluation to run. A later SDK version can add values. */
  public enum Trigger {
    /** The runtime did not report the trigger, or this version of the SDK does not know it. */
    UNSPECIFIED,
    /** Started on request through {@link akka.javasdk.client.EvaluatorClient}. */
    MANUAL,
    /** Created automatically from an interaction of a bound agent. */
    ON_INTERACTION
  }

  /** What an evaluation was about. */
  public sealed interface Subject {

    /**
     * One agent interaction.
     *
     * @param interactionId the id of the interaction
     * @param agentComponentId the component id of the agent that produced the interaction
     */
    record Interaction(String interactionId, String agentComponentId) implements Subject {}

    /**
     * One run of a workflow.
     *
     * @param workflowId the id of the workflow instance
     * @param workflowComponentId the component id of the workflow
     */
    record Workflow(String workflowId, String workflowComponentId) implements Subject {}
  }

  /** The terminal outcome of an evaluation. */
  public sealed interface Outcome {

    /** The evaluation reached a verdict. */
    record Verdict(Evaluation evaluation) implements Outcome {
      public Verdict {
        Objects.requireNonNull(evaluation, "evaluation must not be null");
      }
    }

    /** The evaluation ran but could not reach a verdict. This is a deliberate, expected outcome. */
    record Inconclusive(String reason) implements Outcome {}

    /**
     * The evaluation ran and found nothing to evaluate in the subject. This is a deliberate,
     * expected outcome.
     */
    record NotApplicable(String reason) implements Outcome {}

    /** The evaluation failed, as opposed to reporting an inconclusive outcome. */
    record Failed(String reason) implements Outcome {}
  }

  /**
   * The id of the evaluated interaction.
   *
   * @throws IllegalStateException if the subject is not an interaction
   * @deprecated Switch over {@link #subject()} instead.
   */
  @Deprecated(since = "3.8.0")
  public String interactionId() {
    return interactionSubject().interactionId();
  }

  /**
   * The component id of the agent whose interaction was evaluated.
   *
   * @throws IllegalStateException if the subject is not an interaction
   * @deprecated Switch over {@link #subject()} instead.
   */
  @Deprecated(since = "3.8.0")
  public String agentComponentId() {
    return interactionSubject().agentComponentId();
  }

  private Subject.Interaction interactionSubject() {
    if (subject instanceof Subject.Interaction interaction) {
      return interaction;
    }
    throw new IllegalStateException(
        "Evaluation [" + evaluationId + "] is not an evaluation of an interaction");
  }

  /**
   * The verdict this evaluation reached, or empty for an inconclusive, not applicable or failed
   * evaluation.
   */
  public Optional<Evaluation> evaluation() {
    return outcome instanceof Outcome.Verdict verdict
        ? Optional.of(verdict.evaluation())
        : Optional.empty();
  }
}
