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
 * What a batch run must satisfy, checked over all case results.
 *
 * <p>A real model is not deterministic, so a batch asserts on rates rather than on every case. With
 * a mocked model leave the gate out: without one every case must pass.
 */
public final class Gate {

  /** Whether the run passed the gate, and the detail to print. */
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

  /** Every case must pass. The gate that applies when none is given. */
  public static Gate allCasesPass() {
    return new Gate(
        results -> {
          var failed = results.stream().filter(r -> !r.passed()).map(CaseResult::caseId).toList();
          return failed.isEmpty()
              ? Verdict.pass("all " + results.size() + " cases passed")
              : Verdict.fail("failed cases " + failed);
        });
  }

  /** The share of cases with no failed result must be at least this. */
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

  /**
   * The pass rate of one evaluator, over the cases where it was conclusive, must be at least this.
   * Fails when the evaluator judged no case. Built-in names are in {@link Evaluators}; a custom
   * evaluator answers to its bare name as well as to the {@link Evaluators#CUSTOM_PREFIX} form.
   */
  public static Gate evaluatorRateAtLeast(String evaluator, double rate) {
    if (evaluator == null || evaluator.isBlank())
      throw new IllegalArgumentException("evaluator name required");
    return new Gate(
        results -> {
          var evalResults =
              results.stream()
                  .flatMap(result -> result.evalResults().stream())
                  .filter(evalResult -> Evaluators.sameName(evalResult.evaluator(), evaluator))
                  .filter(evalResult -> evalResult.verdict() != EvalResult.Verdict.INCONCLUSIVE)
                  .toList();
          if (evalResults.isEmpty()) {
            return Verdict.fail(evaluator + " judged no case, so its rate cannot be read");
          }
          var passed =
              evalResults.stream().filter(f -> f.verdict() == EvalResult.Verdict.PASS).count();
          var actual = (double) passed / evalResults.size();
          var summary =
              String.format(
                  Locale.ROOT,
                  "%s rate %.2f over %d judged cases, required %.2f",
                  evaluator,
                  actual,
                  evalResults.size(),
                  rate);
          return actual >= rate ? Verdict.pass(summary) : Verdict.fail(summary);
        });
  }

  /** No case may fail while its recorded calls are loaded or in the agent call. */
  public static Gate noTargetFailures() {
    return new Gate(
        results -> {
          var failed =
              results.stream()
                  .filter(
                      result ->
                          result.evalResults().stream()
                              .anyMatch(
                                  evalResult ->
                                      evalResult.verdict() == EvalResult.Verdict.FAIL
                                          && (evalResult.evaluator().equals(Evaluators.TARGET)
                                              || evalResult.evaluator().equals(Evaluators.SETUP))))
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
