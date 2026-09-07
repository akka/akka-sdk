/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.ToolCall;
import java.util.List;

/**
 * One check over one case's evidence. The built-ins are in {@link Evaluators}; a custom one
 * implements this interface. Give it to an {@link EvalCase}, or to {@link
 * ExperimentCases#evaluator} to run on every case.
 *
 * <p>An evaluator reads only the interaction and the tool calls. It never calls the service.
 */
public interface Evaluator {

  /**
   * The name the report prints this evaluator's results under, and what {@link
   * Gate#evaluatorRateAtLeast} refers to.
   */
  String name();

  /** Checks one turn. Abstain when the evidence this check needs is absent. */
  EvalResult evaluate(EvalCase evalCase, Interaction interaction, List<ToolCall> toolCalls);

  /**
   * What an evaluator found on one turn. This is what a {@link ExperimentRunner.CaseResult} holds.
   *
   * @param evaluator the name of the check that produced this, printed in the report. The runner
   *     fills it in from {@link Evaluator#name}, so an evaluator leaves it empty.
   * @param detail the reason, printed under a failed case
   */
  record EvalResult(String evaluator, Verdict verdict, String detail) {

    public enum Verdict {
      PASS,
      FAIL,
      /** The evidence this check needs is absent; neither a pass nor a fail. */
      ABSTAIN
    }

    public EvalResult {
      if (verdict == null) throw new IllegalArgumentException("verdict required");
      evaluator = evaluator == null ? "" : evaluator;
      detail = detail == null ? "" : detail;
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

    /** The evidence this check needs is absent; neither a pass nor a fail. */
    public static EvalResult abstain(String detail) {
      return new EvalResult("", Verdict.ABSTAIN, detail);
    }

    /** The same result, attributed to the named evaluator. */
    EvalResult attributedTo(String evaluator) {
      return new EvalResult(evaluator, verdict, detail);
    }
  }
}
