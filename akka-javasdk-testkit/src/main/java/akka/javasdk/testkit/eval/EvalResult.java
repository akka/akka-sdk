/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

/**
 * One result on one attempt: an evaluator's verdict, read from the evaluation the evaluator
 * recorded in the ledger, or a result of the runner. This is what a {@link
 * ExperimentRunner.CaseResult} holds.
 *
 * @param evaluator the component id of the evaluator, {@link ExperimentRunner#TARGET} or {@link
 *     ExperimentRunner#GUARDRAIL} for a result the runner reports itself, or {@link
 *     ExperimentRunner#EXPECTED} for the comparison of the reply with the expected reply of the
 *     case
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

  /**
   * Creates a passing evaluation result.
   *
   * @param evaluator the evaluator component id
   * @param detail the explanation and optional score
   * @return the passing result
   */
  public static EvalResult pass(String evaluator, String detail) {
    return new EvalResult(evaluator, Verdict.PASS, detail);
  }

  /**
   * Creates a failing evaluation result.
   *
   * @param evaluator the evaluator component id
   * @param detail the explanation of the failure
   * @return the failing result
   */
  public static EvalResult fail(String evaluator, String detail) {
    return new EvalResult(evaluator, Verdict.FAIL, detail);
  }

  /**
   * Creates an inconclusive evaluation result.
   *
   * @param evaluator the evaluator component id
   * @param detail why the evaluator reached no verdict
   * @return the inconclusive result
   */
  public static EvalResult inconclusive(String evaluator, String detail) {
    return new EvalResult(evaluator, Verdict.INCONCLUSIVE, detail);
  }

  /**
   * Creates a not applicable evaluation result.
   *
   * @param evaluator the evaluator component id
   * @param detail why the evaluator found nothing to evaluate
   * @return the not applicable result
   */
  public static EvalResult notApplicable(String evaluator, String detail) {
    return new EvalResult(evaluator, Verdict.NOT_APPLICABLE, detail);
  }
}
