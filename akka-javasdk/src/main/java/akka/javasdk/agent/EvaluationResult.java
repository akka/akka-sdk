/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * When an {@link Agent} returns an {@code EvaluationResult} it is tracked in metrics and traces.
 *
 * @deprecated Evaluate interactions with an {@link akka.javasdk.evaluation.Evaluator} component,
 *     which records its verdict in the ledger, metrics and traces.
 */
@Deprecated(since = "3.7.1")
public interface EvaluationResult {
  /**
   * @return reason for the decision, especially when it didn't pass
   */
  @JsonProperty
  String explanation();

  /**
   * @return true if the input passed the guardrail evaluation
   */
  @JsonProperty
  boolean passed();
}
