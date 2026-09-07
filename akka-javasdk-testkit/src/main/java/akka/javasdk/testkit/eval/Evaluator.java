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
   * The name the report prints this evaluator's findings under, and what {@link
   * Gate#evaluatorRateAtLeast} refers to.
   */
  String name();

  /** Checks one turn. Abstain when the evidence this check needs is absent. */
  Finding evaluate(EvalCase evalCase, Interaction interaction, List<ToolCall> toolCalls);

  /**
   * What an evaluator found on one turn.
   *
   * @param detail the reason, printed under a failed case
   */
  record Finding(EvalResult.Verdict verdict, String detail) {

    public Finding {
      if (verdict == null) throw new IllegalArgumentException("verdict required");
      detail = detail == null ? "" : detail;
    }

    public static Finding pass() {
      return new Finding(EvalResult.Verdict.PASS, "");
    }

    /** A pass with something to print, such as a score. */
    public static Finding pass(String detail) {
      return new Finding(EvalResult.Verdict.PASS, detail);
    }

    public static Finding fail(String detail) {
      return new Finding(EvalResult.Verdict.FAIL, detail);
    }

    /** The evidence this check needs is absent; neither a pass nor a fail. */
    public static Finding abstain(String detail) {
      return new Finding(EvalResult.Verdict.ABSTAIN, detail);
    }
  }

  /**
   * A finding attributed to the evaluator that produced it. This is what a {@link
   * ExperimentRunner.CaseResult} holds.
   *
   * @param evaluator the name of the check that produced this, printed in the report
   * @param detail the reason, printed under a failed case
   */
  record EvalResult(String evaluator, Verdict verdict, String detail) {

    public enum Verdict {
      PASS,
      FAIL,
      /** The evidence this check needs is absent; neither a pass nor a fail. */
      ABSTAIN
    }

    public static EvalResult of(String evaluator, Finding finding) {
      return new EvalResult(evaluator, finding.verdict(), finding.detail());
    }

    public static EvalResult pass(String evaluator) {
      return new EvalResult(evaluator, Verdict.PASS, "");
    }

    public static EvalResult fail(String evaluator, String detail) {
      return new EvalResult(evaluator, Verdict.FAIL, detail);
    }

    public static EvalResult abstain(String evaluator, String detail) {
      return new EvalResult(evaluator, Verdict.ABSTAIN, detail);
    }
  }
}
