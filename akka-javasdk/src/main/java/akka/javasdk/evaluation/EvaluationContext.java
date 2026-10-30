/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import com.typesafe.config.Config;
import java.util.Map;

/**
 * Context passed to an {@link Evaluator} when an evaluation is triggered.
 *
 * <p>Exposes the {@link Subject} being evaluated, the id of this evaluation, the attributes the
 * evaluation was started with and the configuration of the evaluator.
 *
 * <p>Not for user extension.
 */
public interface EvaluationContext {

  /**
   * What is being evaluated.
   *
   * @return the subject of this evaluation
   */
  Subject subject();

  /**
   * The attributes passed when the evaluation was started through {@link
   * akka.javasdk.client.EvaluatorClient}
   *
   * @return the attributes of this evaluation
   */
  Map<String, String> attributes();

  /**
   * The unique id of this evaluation.
   *
   * @return the evaluation id
   */
  String evaluationId();

  /**
   * The configuration of the evaluator for this evaluation, from its entry under {@code
   * akka.javasdk.evaluation.evaluators}: the binding that the runtime used for the evaluated agent,
   * with the evaluator entry as fallback, key by key. For an agent without an enabled binding, and
   * for a subject other than an interaction, the evaluator entry alone. The keys {@code agents} and
   * {@code agent-roles} are left out. The other keys the SDK reads, such as {@code trigger} and
   * {@code enabled}, are present when the entries set them. The defaults under {@code
   * akka.javasdk.evaluation.defaults} are not included. Empty when the evaluator has no entry.
   *
   * @return the configuration of the binding with the evaluator entry as fallback
   */
  Config config();
}
