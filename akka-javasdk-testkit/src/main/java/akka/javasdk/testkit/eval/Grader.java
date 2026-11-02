/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.evaluation.Evaluation;

/**
 * Compares the reply of an attempt with the expected reply of its case, see {@link
 * Experiment#grade}. Runs in the test JVM, so it can query a database or call any test code.
 *
 * <pre>{@code
 * Grader<String> holdsTheTruth =
 *     (expectedSql, reply) -> {
 *       var truth = database.scalar(expectedSql);
 *       return reply.contains(truth)
 *           ? Evaluation.passed("reply holds " + truth)
 *           : Evaluation.failed("reply lacks " + truth);
 *     };
 * }</pre>
 *
 * @param <V> the reply type of the agent's command handler
 */
@FunctionalInterface
public interface Grader<V> {

  /**
   * Compares the reply with the expected reply.
   *
   * @param expected the expected reply of the case
   * @param reply the value the agent's command handler returned
   * @return the verdict, with the explanation the report prints and optionally a score and a label.
   *     A thrown exception is recorded as a failed result with the exception as the detail.
   */
  Evaluation grade(V expected, V reply);
}
