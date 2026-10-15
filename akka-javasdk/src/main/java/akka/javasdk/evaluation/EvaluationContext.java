/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import com.typesafe.config.Config;

/**
 * Context passed to an {@link Evaluator} when an evaluation is triggered.
 *
 * <p>Exposes the {@link Subject} being evaluated, the id of this evaluation and the settings
 * configured for the evaluator.
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

  /**
   * The settings configured for this evaluation. These are the {@code settings} of the binding that
   * the runtime used for the evaluated agent, with the {@code settings} of the evaluator entry
   * under {@code akka.javasdk.evaluation.evaluators} as fallback. Empty when neither defines
   * settings.
   *
   * @return the settings of the binding over the settings of the evaluator
   */
  Config config();
}
