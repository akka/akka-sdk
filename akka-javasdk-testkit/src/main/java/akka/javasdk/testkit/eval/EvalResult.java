/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

/**
 * One evaluator's result on one attempt, read from the evaluation the evaluator recorded in the
 * ledger. This is what a {@link ExperimentRunner.CaseResult} holds.
 *
 * @param evaluator the component id of the evaluator, or {@link ExperimentRunner#TARGET} for the
 *     result the runner reports when the agent call failed
 * @param verdict the verdict
 * @param detail the explanation and the score the evaluator gave, or why there is no verdict
 */
public record EvalResult(String evaluator, Verdict verdict, String detail) {

  public enum Verdict {
    PASS,
    FAIL,
    /**
     * The evaluator reached no verdict, or recorded no evaluation within the evaluation timeout;
     * neither a pass nor a fail. An attempt with an inconclusive result does not pass.
     */
    INCONCLUSIVE,
    /**
     * The evaluator found nothing to evaluate in the interaction; neither a pass nor a fail. The
     * result does not count against the attempt.
     */
    NOT_APPLICABLE
  }

  public EvalResult {
    if (evaluator == null) throw new IllegalArgumentException("evaluator required");
    if (verdict == null) throw new IllegalArgumentException("verdict required");
    if (detail == null) throw new IllegalArgumentException("detail required");
  }

  public static EvalResult pass(String evaluator, String detail) {
    return new EvalResult(evaluator, Verdict.PASS, detail);
  }

  public static EvalResult fail(String evaluator, String detail) {
    return new EvalResult(evaluator, Verdict.FAIL, detail);
  }

  public static EvalResult inconclusive(String evaluator, String detail) {
    return new EvalResult(evaluator, Verdict.INCONCLUSIVE, detail);
  }

  public static EvalResult notApplicable(String evaluator, String detail) {
    return new EvalResult(evaluator, Verdict.NOT_APPLICABLE, detail);
  }
}
