/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import akka.javasdk.evaluation.Evaluation;
import java.time.Instant;
import java.util.List;

/**
 * The record of a single evaluation, as fetched from the ledger.
 *
 * <p>An evaluation is recorded when it terminates, so a fetched record always carries a terminal
 * {@link Outcome}: the verdict it reached, or why it did not reach one.
 *
 * @param evaluationId the globally unique id of the evaluation
 * @param evaluatorComponentId the component id of the evaluator that ran the evaluation
 * @param trigger what caused the evaluation to run
 * @param interactionId the id of the interaction that was evaluated
 * @param agentComponentId the component id of the agent whose interaction was evaluated
 * @param outcome the terminal outcome of the evaluation
 * @param timestamp when the evaluation was recorded
 */
public record EvaluationRecord(
    String evaluationId,
    String evaluatorComponentId,
    Trigger trigger,
    String interactionId,
    String agentComponentId,
    Outcome outcome,
    Instant timestamp) {

  /** What caused an evaluation to run. */
  public enum Trigger {
    UNSPECIFIED,
    /** Created manually, for example via a client or the console. */
    MANUAL,
    /** Created automatically from an interaction of a bound agent. */
    ON_INTERACTION
  }

  /** The terminal outcome of an evaluation. */
  public sealed interface Outcome {

    /** The evaluation reached a verdict: one or more evaluations. */
    record Verdict(List<Evaluation> evaluations) implements Outcome {}

    /** The evaluation ran but could not reach a verdict — a deliberate, expected outcome. */
    record Inconclusive(String reason) implements Outcome {}

    /** The evaluation failed, as opposed to reporting an inconclusive outcome. */
    record Failed(String reason) implements Outcome {}
  }

  /**
   * The evaluations of the verdict this evaluation reached, or an empty list for an inconclusive or
   * failed evaluation.
   */
  public List<Evaluation> evaluations() {
    return outcome instanceof Outcome.Verdict verdict ? verdict.evaluations() : List.of();
  }
}
