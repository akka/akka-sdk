/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import akka.javasdk.testkit.eval.ExperimentRunner.CaseResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * What a batch run must clear to pass, over the aggregated findings.
 *
 * <p>Exists because a real model is stochastic: one wrong case in thirty is variance, not a
 * regression, and per-case assertions would turn it into a flaky build. The gate asserts on rates
 * instead. For the deterministic mode (mocked model), skip the gate and run per case.
 */
public final class Gate {

  /** Whether the run cleared the gate, and what to print when it did not. */
  record Verdict(boolean passed, String detail) {

    static Verdict pass(String detail) {
      return new Verdict(true, detail);
    }

    static Verdict fail(String detail) {
      return new Verdict(false, detail);
    }
  }

  private final Function<List<CaseResult>, Verdict> condition;

  private Gate(Function<List<CaseResult>, Verdict> condition) {
    this.condition = condition;
  }

  /** The share of cases with no failed finding must be at least this. */
  public static Gate passRateAtLeast(double rate) {
    return new Gate(
        results -> {
          var passed = results.stream().filter(CaseResult::passed).count();
          var actual = (double) passed / results.size();
          var summary =
              String.format(
                  Locale.ROOT,
                  "pass rate %.2f over %d cases, required %.2f",
                  actual,
                  results.size(),
                  rate);
          return actual >= rate ? Verdict.pass(summary) : Verdict.fail(summary);
        });
  }

  /** One evaluator's own pass rate must be at least this, e.g. tool-arguments at 0.95. */
  public static Gate evaluatorRateAtLeast(String evaluator, double rate) {
    if (evaluator == null || evaluator.isBlank())
      throw new IllegalArgumentException("evaluator name required");
    return new Gate(
        results -> {
          var findings =
              results.stream()
                  .flatMap(result -> result.evalResults().stream())
                  .filter(finding -> finding.evaluator().equals(evaluator))
                  .filter(finding -> finding.verdict() != EvalResult.Verdict.ABSTAIN)
                  .toList();
          if (findings.isEmpty()) {
            return Verdict.fail(evaluator + " judged no case, so its rate cannot be read");
          }
          var passed =
              findings.stream().filter(f -> f.verdict() == EvalResult.Verdict.PASS).count();
          var actual = (double) passed / findings.size();
          var summary =
              String.format(
                  Locale.ROOT,
                  "%s rate %.2f over %d judged cases, required %.2f",
                  evaluator,
                  actual,
                  findings.size(),
                  rate);
          return actual >= rate ? Verdict.pass(summary) : Verdict.fail(summary);
        });
  }

  /** No case may end in a target failure (thrown, never answered). */
  public static Gate noTargetFailures() {
    return new Gate(
        results -> {
          var failed =
              results.stream()
                  .filter(
                      result ->
                          result.evalResults().stream()
                              .anyMatch(
                                  finding ->
                                      finding.verdict() == EvalResult.Verdict.FAIL
                                          && (finding.evaluator().equals(Evaluators.TARGET)
                                              || finding.evaluator().equals(Evaluators.SETUP))))
                  .map(CaseResult::caseId)
                  .toList();
          return failed.isEmpty()
              ? Verdict.pass("no target failures")
              : Verdict.fail("target failed on " + failed);
        });
  }

  /** Both must hold. */
  public Gate and(Gate other) {
    if (other == null) throw new IllegalArgumentException("gate required");
    return new Gate(
        results -> {
          var verdicts = List.of(condition.apply(results), other.condition.apply(results));
          var details = new ArrayList<String>();
          var failed = false;
          for (var verdict : verdicts) {
            details.add(verdict.detail());
            failed |= !verdict.passed();
          }
          var detail = String.join("; ", details);
          return failed ? Verdict.fail(detail) : Verdict.pass(detail);
        });
  }

  Verdict check(List<CaseResult> results) {
    if (results.isEmpty()) return Verdict.fail("no cases ran");
    return condition.apply(results);
  }
}
