/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import akka.javasdk.testkit.eval.ExperimentRunner.CaseResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What a batch run must satisfy, checked over all attempts. An attempt is one case in one run, so
 * with {@link Experiment#repeat} every gate counts each case once per run.
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

  /** Every case must pass, in every run. The gate that applies when none is given. */
  public static Gate allCasesShouldPass() {
    return new Gate(
        results -> {
          var failed = failedCases(results, r -> !r.passed());
          return failed.isEmpty()
              ? Verdict.pass("all " + attempts(results) + " passed")
              : Verdict.fail("failed cases " + failed);
        });
  }

  /** The share of attempts with no failed result must be at least {@code minimumRate}. */
  public static Gate passRateShouldBeAtLeast(double minimumRate) {
    return new Gate(
        results -> {
          var passed = results.stream().filter(CaseResult::passed).count();
          var actual = (double) passed / results.size();
          var summary =
              String.format(
                  Locale.ROOT,
                  "pass rate %.2f over %s, required %.2f",
                  actual,
                  attempts(results),
                  minimumRate);
          return actual >= minimumRate ? Verdict.pass(summary) : Verdict.fail(summary);
        });
  }

  /**
   * The pass rate of one evaluator, over the attempts where it was conclusive, must be at least
   * {@code minimumRate}. Fails when the evaluator judged no attempt.
   *
   * @param evaluator the evaluator's class. The built-ins are nested in {@link Evaluators}, for
   *     example {@code Evaluators.ToolArgument.class}
   * @throws IllegalArgumentException when the class does not carry a valid {@link EvalLabel}
   */
  public static Gate passRateShouldBeAtLeast(
      Class<? extends Evaluator> evaluator, double minimumRate) {
    var label = Evaluators.label(evaluator);

    return new Gate(
        results -> {
          var evalResults =
              results.stream()
                  .flatMap(result -> result.evalResults().stream())
                  .filter(evalResult -> evalResult.evaluator().equals(label))
                  .filter(evalResult -> evalResult.verdict() != EvalResult.Verdict.INCONCLUSIVE)
                  .toList();
          if (evalResults.isEmpty()) {
            return Verdict.fail(label + " judged no case, so its rate cannot be read");
          }
          var passed =
              evalResults.stream().filter(f -> f.verdict() == EvalResult.Verdict.PASS).count();
          var actual = (double) passed / evalResults.size();
          var summary =
              String.format(
                  Locale.ROOT,
                  "%s rate %.2f over %d judged attempts, required %.2f",
                  label,
                  actual,
                  evalResults.size(),
                  minimumRate);
          return actual >= minimumRate ? Verdict.pass(summary) : Verdict.fail(summary);
        });
  }

  /** No attempt may fail while its recorded calls are loaded or in the agent call. */
  public static Gate targetShouldNotFail() {
    return new Gate(
        results -> {
          var failed =
              failedCases(
                  results,
                  result ->
                      result.evalResults().stream()
                          .anyMatch(
                              evalResult ->
                                  evalResult.verdict() == EvalResult.Verdict.FAIL
                                      && (evalResult.evaluator().equals(Evaluators.TARGET)
                                          || evalResult.evaluator().equals(Evaluators.SETUP))));
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

  private static int runs(List<CaseResult> results) {
    return results.stream().mapToInt(CaseResult::run).max().orElse(1);
  }

  /** {@code 9 attempts (3 cases, 3 runs)}. */
  private static String attempts(List<CaseResult> results) {
    var runs = runs(results);
    return results.size()
        + " attempts ("
        + ExperimentRunner.count(results.size() / runs, "case")
        + ", "
        + ExperimentRunner.count(runs, "run")
        + ")";
  }

  /**
   * The ids of the cases with an attempt that matches, in order, each with the runs that matched:
   * {@code refund (runs 1, 3)}.
   */
  private static List<String> failedCases(List<CaseResult> results, Predicate<CaseResult> failed) {
    var runsByCase = new LinkedHashMap<String, List<Integer>>();
    for (var result : results) {
      if (failed.test(result)) {
        runsByCase.computeIfAbsent(result.caseId(), id -> new ArrayList<>()).add(result.run());
      }
    }
    return runsByCase.entrySet().stream()
        .map(e -> e.getKey() + " (" + ExperimentRunner.runs(e.getValue()) + ")")
        .toList();
  }
}
