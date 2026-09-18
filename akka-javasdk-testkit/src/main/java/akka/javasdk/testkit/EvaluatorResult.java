/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.evaluation.Evaluation;
import java.util.List;

/**
 * Represents the result of an Evaluator handling an evaluation when run through the testkit.
 *
 * <p>Not for user extension, returned by the testkit.
 */
public interface EvaluatorResult {

  /**
   * @return true if the evaluation completed with one or more verdicts
   */
  boolean isComplete();

  /**
   * @return true if the evaluation was inconclusive
   */
  boolean isInconclusive();

  /**
   * @return the evaluations the evaluation completed with, or throws if it was not complete
   */
  List<Evaluation> getEvaluations();

  /**
   * @return the reason the evaluation was inconclusive, or throws if it was not inconclusive
   */
  String getInconclusiveReason();
}
