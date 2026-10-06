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
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * What a batch run must satisfy, checked over all attempts. An attempt is one case in one run, so
 * with {@link Experiment#repeat} every gate counts each case once per run.
 *
 * <p>A real model is not deterministic, so a batch asserts on rates rather than on every case. With
 * a mocked model leave the gate out: without one every case must pass.
 *
 * <p>An attempt with no conclusive result is inconclusive. It never passes: it fails {@link
 * #allCasesShouldPass} and counts against the rate of {@link #passRateShouldBeAtLeast(double)}.
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

  private final BiFunction<List<CaseResult>, Integer, Verdict> condition;

  private Gate(BiFunction<List<CaseResult>, Integer, Verdict> condition) {
    this.condition = condition;
  }

  /**
   * Every case must pass, in every run. An inconclusive attempt fails the gate. The gate that
   * applies when none is given.
   */
  public static Gate allCasesShouldPass() {
    return new Gate(
        (results, runs) -> {
          var failed = failedCases(results, runs, CaseResult::failed);
          var inconclusive = failedCases(results, runs, CaseResult::inconclusive);
          if (failed.isEmpty() && inconclusive.isEmpty()) {
            return Verdict.pass("all " + attempts(results, runs) + " passed");
          }
          var details = new ArrayList<String>();
          if (!failed.isEmpty()) details.add("failed cases " + failed);
          if (!inconclusive.isEmpty()) details.add("inconclusive cases " + inconclusive);
          return Verdict.fail(String.join(", ", details));
        });
  }

  /**
   * The share of attempts that passed must be at least {@code minimumRate}. An inconclusive attempt
   * counts against the rate.
   */
  public static Gate passRateShouldBeAtLeast(double minimumRate) {
    return new Gate(
        (results, runs) -> {
          var passed = results.stream().filter(CaseResult::passed).count();
          var inconclusive = results.stream().filter(CaseResult::inconclusive).count();
          var actual = (double) passed / results.size();
          var summary =
              String.format(
                  Locale.ROOT,
                  "pass rate %.2f over %s%s, required %.2f",
                  actual,
                  attempts(results, runs),
                  inconclusive == 0 ? "" : " with " + inconclusive + " inconclusive",
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
        (results, runs) -> {
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
        (results, runs) -> {
          var failed =
              failedCases(
                  results,
                  runs,
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
        (results, runs) -> {
          var verdicts =
              List.of(condition.apply(results, runs), other.condition.apply(results, runs));
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

  /**
   * @param results one per attempt
   * @param runs how many times every case ran
   */
  Verdict check(List<CaseResult> results, int runs) {
    if (results.isEmpty()) return Verdict.fail("no cases ran");
    return condition.apply(results, runs);
  }

  /** {@code 9 attempts (3 cases, 3 runs)}. */
  private static String attempts(List<CaseResult> results, int runs) {
    return results.size()
        + " attempts ("
        + ExperimentRunner.plural(results.size() / runs, "case")
        + ", "
        + ExperimentRunner.plural(runs, "run")
        + ")";
  }

  /**
   * The ids of the cases with an attempt that matches, in order, each with the runs that matched:
   * {@code refund (runs 1, 3)}.
   */
  private static List<String> failedCases(
      List<CaseResult> results, int runs, Predicate<CaseResult> failed) {
    var runsByCase = new LinkedHashMap<String, List<Integer>>();
    for (var result : results) {
      if (failed.test(result)) {
        runsByCase.computeIfAbsent(result.caseId(), id -> new ArrayList<>()).add(result.run());
      }
    }
    return runsByCase.entrySet().stream()
        .map(e -> e.getKey() + " (" + ExperimentRunner.describeRuns(e.getValue()) + ")")
        .toList();
  }
}
