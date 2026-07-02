/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

/**
 * Context passed to an {@link Evaluator} when an evaluation is triggered.
 *
 * <p>Exposes the {@link Subject} being evaluated and the id of this evaluation.
 *
 * <p>Not for user extension.
 */
public interface EvaluationContext {

  /**
   * The interaction being evaluated.
   *
   * @return the subject of this evaluation
   */
  Subject subject();

  /**
   * The unique id of this evaluation.
   *
   * @return the evaluation id
   */
  String evaluationId();
}
