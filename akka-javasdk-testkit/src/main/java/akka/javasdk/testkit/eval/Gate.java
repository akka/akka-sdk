/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.annotations.Component;
import akka.javasdk.ledger.GuardrailDecisionRecord;
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
 * <p>An attempt passes when at least one result passed and no result failed or is inconclusive. A
 * not applicable result does not count against the attempt. An attempt with an inconclusive result,
 * or without a passed result, is inconclusive. It never passes: it fails {@link
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
   * Every case must pass in every run, with every result passed. An inconclusive attempt fails the
   * gate. The gate that applies when none is given.
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
   * {@code minimumRate}. An inconclusive or not applicable result is left out. Fails when the
   * evaluator judged no attempt.
   *
   * @param evaluatorClass the evaluator component class, annotated with {@link Component}
   */
  public static Gate passRateShouldBeAtLeast(Class<?> evaluatorClass, double minimumRate) {
    var evaluator = componentId(evaluatorClass);
    return new Gate(
        (results, runs) -> {
          var evalResults =
              results.stream()
                  .flatMap(result -> result.evalResults().stream())
                  .filter(evalResult -> evalResult.evaluator().equals(evaluator))
                  .filter(
                      evalResult ->
                          evalResult.verdict() == EvalResult.Verdict.PASS
                              || evalResult.verdict() == EvalResult.Verdict.FAIL)
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
                  "%s rate %.2f over %d judged attempts, required %.2f",
                  evaluator,
                  actual,
                  evalResults.size(),
                  minimumRate);
          return actual >= minimumRate ? Verdict.pass(summary) : Verdict.fail(summary);
        });
  }

  private static String componentId(Class<?> componentClass) {
    if (componentClass == null) throw new IllegalArgumentException("evaluator class required");
    var component = componentClass.getAnnotation(Component.class);
    if (component == null)
      throw new IllegalArgumentException(
          componentClass.getName() + " has no @Component annotation");
    return component.id();
  }

  /** No agent call may fail. */
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
                                      && evalResult.evaluator().equals(ExperimentRunner.TARGET)));
          return failed.isEmpty()
              ? Verdict.pass("no target failures")
              : Verdict.fail("target failed on " + failed);
        });
  }

  /**
   * The named guardrail must deny nothing in any attempt. For a guardrail that must stay silent on
   * legitimate traffic.
   *
   * @param name the configured name of the guardrail
   */
  public static Gate guardrailShouldNotDeny(String name) {
    requireGuardrailName(name);
    return new Gate(
        (results, runs) -> {
          var denied = failedCases(results, runs, result -> denied(result, name));
          return denied.isEmpty()
              ? Verdict.pass("guardrail " + name + " denied nothing")
              : Verdict.fail("guardrail " + name + " denied on " + denied);
        });
  }

  /**
   * The named guardrail must deny in every attempt. For a guardrail that must catch an attack every
   * case carries.
   *
   * @param name the configured name of the guardrail
   */
  public static Gate guardrailShouldDeny(String name) {
    requireGuardrailName(name);
    return new Gate(
        (results, runs) -> {
          var allowed = failedCases(results, runs, result -> !denied(result, name));
          return allowed.isEmpty()
              ? Verdict.pass("guardrail " + name + " denied every attempt")
              : Verdict.fail("guardrail " + name + " did not deny on " + allowed);
        });
  }

  private static void requireGuardrailName(String name) {
    if (name == null || name.isBlank())
      throw new IllegalArgumentException("guardrail name required");
  }

  private static boolean denied(CaseResult result, String guardrail) {
    return result.evidence().guardrails().stream()
        .anyMatch(
            decision ->
                decision.name().equals(guardrail)
                    && decision.decision() == GuardrailDecisionRecord.Decision.DENY);
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
