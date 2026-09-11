/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

/**
 * One check over one case's evidence. The built-ins are in {@link Evaluators}; a custom one is a
 * named class annotated with {@link EvalLabel} that implements this interface. Give it to an {@link
 * EvalCase}, or to {@link ExperimentCases#evaluator} to run on every case.
 *
 * <p>An evaluator reads only the interaction. It never calls the service.
 */
public interface Evaluator {

  /**
   * Checks one turn. Inconclusive when the evidence this check needs is absent or does not support
   * a verdict.
   */
  EvalResult evaluate(EvalCase evalCase, Interaction interaction);

  /**
   * What an evaluator found on one turn. This is what a {@link ExperimentRunner.CaseResult} holds.
   *
   * @param evaluator the label of the check that produced this, as the report prints it. The runner
   *     fills it in from the evaluator's {@link EvalLabel}, so an evaluator leaves it empty.
   * @param detail the reason, printed under a failed case
   */
  record EvalResult(String evaluator, Verdict verdict, String detail) {

    public enum Verdict {
      PASS,
      FAIL,
      /**
       * The evidence this check needs is absent or does not support a verdict; neither a pass nor a
       * fail.
       */
      INCONCLUSIVE
    }

    public EvalResult {
      if (evaluator == null) throw new IllegalArgumentException("evaluator required");
      if (verdict == null) throw new IllegalArgumentException("verdict required");
      if (detail == null) throw new IllegalArgumentException("detail required");
    }

    public static EvalResult pass() {
      return new EvalResult("", Verdict.PASS, "");
    }

    /** A pass with something to print, such as a score. */
    public static EvalResult pass(String detail) {
      return new EvalResult("", Verdict.PASS, detail);
    }

    public static EvalResult fail(String detail) {
      return new EvalResult("", Verdict.FAIL, detail);
    }

    /**
     * The evidence this check needs is absent or does not support a verdict; neither a pass nor a
     * fail.
     */
    public static EvalResult inconclusive(String detail) {
      return new EvalResult("", Verdict.INCONCLUSIVE, detail);
    }

    /** The same result, attributed to the labelled evaluator. */
    EvalResult attributedTo(String evaluator) {
      return new EvalResult(evaluator, verdict, detail);
    }
  }
}
