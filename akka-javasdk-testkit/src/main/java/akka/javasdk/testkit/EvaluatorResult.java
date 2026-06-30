/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.evaluation.Evaluation;
import java.util.List;

/**
 * Represents the result of an Evaluator handling an evaluation when run through the testkit.
 *
 * <p>An asynchronous effect is resolved before the result is returned, so {@link #isRecord()} and
 * {@link #isError()} reflect the terminal outcome; {@link #isAsync()} additionally reports whether
 * the effect was produced asynchronously.
 *
 * <p>Not for user extension, returned by the testkit.
 */
public interface EvaluatorResult {

  /**
   * @return true if the evaluation recorded one or more results
   */
  boolean isRecord();

  /**
   * @return true if the evaluation reported a deliberate error
   */
  boolean isError();

  /**
   * @return true if the effect was produced asynchronously
   */
  boolean isAsync();

  /**
   * @return the recorded evaluations, or throws if the effect was not a record
   */
  List<Evaluation> getEvaluations();

  /**
   * @return the error reason, or throws if the effect was not an error
   */
  String getError();
}
