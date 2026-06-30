/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

/**
 * Context passed to an {@link Evaluator} when an evaluation is triggered.
 *
 * <p>Exposes the {@link Subject} being evaluated, the id of this evaluation, and a session id to be
 * used for any LLM-as-judge calls made while evaluating.
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
   * A session id scoped to this evaluation, for use when invoking a judge agent (LLM-as-judge).
   *
   * <p>Judge interactions are run in their own session, isolated from the subject's session, so
   * that they do not pollute the conversation being evaluated. The returned id is derived from the
   * evaluation id but is never the bare evaluation id itself.
   *
   * <p>Use {@link #evaluationSession(String)} when an evaluator invokes more than one judge, so
   * each judge gets its own isolated session.
   *
   * @return a session id for judge calls in this evaluation
   */
  String evaluationSession();

  /**
   * A session id scoped to this evaluation and the given judge key, for use when an evaluator
   * invokes more than one judge.
   *
   * <p>Each distinct {@code judgeKey} yields a distinct, stable session id, so judges are isolated
   * from each other while remaining correlated to this evaluation.
   *
   * @param judgeKey a stable key identifying the judge (for example, the judge agent's component
   *     id)
   * @return a session id for that judge's calls in this evaluation
   */
  String evaluationSession(String judgeKey);
}
