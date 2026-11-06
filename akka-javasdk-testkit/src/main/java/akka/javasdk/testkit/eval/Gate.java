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
   * Requires every case to pass in every run, with every result passed. An inconclusive attempt
   * fails the gate. The gate that applies when none is given.
   *
   * @return a gate that requires all cases to pass
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
   * Requires the share of attempts that passed to be at least {@code minimumRate}. An inconclusive
   * attempt counts against the rate.
   *
   * @param minimumRate the minimum pass rate, between 0.0 and 1.0
   * @return a gate that checks the overall attempt pass rate
   * @throws IllegalArgumentException when {@code minimumRate} is not between 0.0 and 1.0
   */
  public static Gate passRateShouldBeAtLeast(double minimumRate) {
    requireRate(minimumRate);
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
   * Requires the pass rate of one evaluator, over the attempts where it was conclusive, to be at
   * least {@code minimumRate}. An inconclusive or not applicable result is left out. Fails when the
   * evaluator judged no attempt.
   *
   * @param evaluatorClass the evaluator component class, annotated with {@link Component}
   * @param minimumRate the minimum pass rate, between 0.0 and 1.0
   * @return a gate that checks the evaluator's pass rate
   * @throws IllegalArgumentException when {@code evaluatorClass} is null or lacks {@link
   *     Component}, or when {@code minimumRate} is not between 0.0 and 1.0
   */
  public static Gate passRateShouldBeAtLeast(Class<?> evaluatorClass, double minimumRate) {
    return passRateShouldBeAtLeast(componentId(evaluatorClass), minimumRate);
  }

  /**
   * Requires the reply of every attempt to match the expected reply of its case. Every {@link
   * ExperimentRunner#EXPECTED} result must pass. An attempt without a reply to compare, because the
   * agent call failed or returned no value, fails the gate. Fails when no case has an expected
   * reply.
   *
   * @return a gate that requires all expected replies to match
   */
  public static Gate expectedShouldPass() {
    return new Gate(
        (results, runs) -> {
          if (results.stream().noneMatch(result -> hasResultOf(result, ExperimentRunner.EXPECTED)))
            return Verdict.fail("no case has an expected reply, so the replies cannot be compared");
          var mismatched =
              failedCases(
                  results, runs, result -> expectedVerdict(result, EvalResult.Verdict.FAIL));
          var withoutReply =
              failedCases(
                  results,
                  runs,
                  result -> expectedVerdict(result, EvalResult.Verdict.INCONCLUSIVE));
          if (mismatched.isEmpty() && withoutReply.isEmpty()) {
            return Verdict.pass("every reply matches the expected reply");
          }
          var details = new ArrayList<String>();
          if (!mismatched.isEmpty())
            details.add("the reply does not match the expected reply on " + mismatched);
          if (!withoutReply.isEmpty())
            details.add("no reply to compare with the expected reply on " + withoutReply);
          return Verdict.fail(String.join(", ", details));
        });
  }

  private static boolean expectedVerdict(CaseResult result, EvalResult.Verdict verdict) {
    return result.evalResults().stream()
        .anyMatch(
            evalResult ->
                evalResult.evaluator().equals(ExperimentRunner.EXPECTED)
                    && evalResult.verdict() == verdict);
  }

  /**
   * Requires the share of the attempts whose reply matches the expected reply of its case to be at
   * least {@code minimumRate}, over the attempts where the comparison ran. An attempt without a
   * reply to compare, because the agent call failed or returned no value, is excluded. Fails when
   * no case has an expected reply.
   *
   * @param minimumRate the minimum pass rate, between 0.0 and 1.0
   * @return a gate that checks the expected reply pass rate
   * @throws IllegalArgumentException when {@code minimumRate} is not between 0.0 and 1.0
   */
  public static Gate expectedPassRateShouldBeAtLeast(double minimumRate) {
    return passRateShouldBeAtLeast(ExperimentRunner.EXPECTED, minimumRate);
  }

  private static Gate passRateShouldBeAtLeast(String evaluator, double minimumRate) {
    requireRate(minimumRate);
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

  private static boolean hasResultOf(CaseResult result, String evaluator) {
    return result.evalResults().stream().anyMatch(r -> r.evaluator().equals(evaluator));
  }

  private static String componentId(Class<?> componentClass) {
    if (componentClass == null) throw new IllegalArgumentException("evaluator class required");
    var component = componentClass.getAnnotation(Component.class);
    if (component == null)
      throw new IllegalArgumentException(
          componentClass.getName() + " has no @Component annotation");
    return component.id();
  }

  /**
   * Requires that no agent call failed: no attempt may have a failed {@link
   * ExperimentRunner#TARGET} or {@link ExperimentRunner#GUARDRAIL} result. In an evaluation, an
   * enforcing denial that stopped the interaction fails the gate. In an attack, it does not.
   *
   * @return a gate that fails when any attempt has a failed target or guardrail result
   */
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
                                      && (evalResult.evaluator().equals(ExperimentRunner.TARGET)
                                          || evalResult
                                              .evaluator()
                                              .equals(ExperimentRunner.GUARDRAIL))));
          return failed.isEmpty()
              ? Verdict.pass("no target failures")
              : Verdict.fail("target failed on " + failed);
        });
  }

  /**
   * Requires the named guardrail to deny nothing in any attempt. For a guardrail that must stay
   * silent on legitimate traffic.
   *
   * @param name the configured name of the guardrail
   * @return a gate that fails if the named guardrail denies any attempt
   * @throws IllegalArgumentException when {@code name} is null or blank
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
   * Requires the named guardrail to deny in every attempt. For a guardrail that must catch an
   * attack every case carries.
   *
   * @param name the configured name of the guardrail
   * @return a gate that fails if the named guardrail allows any attempt
   * @throws IllegalArgumentException when {@code name} is null or blank
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

  /**
   * Passes when an enforcing denial from any guardrail stopped the interaction in every attempt.
   * Report-only denials do not count.
   */
  public static Gate guardrailShouldStop() {
    return new Gate(
        (results, runs) -> {
          var notStopped =
              failedCases(
                  results,
                  runs,
                  result ->
                      result.evalResults().stream()
                          .noneMatch(r -> r.evaluator().equals(ExperimentRunner.GUARDRAIL)));
          return notStopped.isEmpty()
              ? Verdict.pass("a guardrail stopped every attempt")
              : Verdict.fail("no guardrail stopped " + notStopped);
        });
  }

  /**
   * Passes when an enforcing denial from the named guardrail stopped the interaction in every
   * attempt. Report-only denials do not count.
   *
   * @param name the configured name of the guardrail
   */
  public static Gate guardrailShouldStop(String name) {
    requireGuardrailName(name);
    return new Gate(
        (results, runs) -> {
          var notStopped =
              failedCases(
                  results,
                  runs,
                  result ->
                      result
                          .evidence()
                          .stoppingDenial()
                          .filter(denial -> denial.name().equals(name))
                          .isEmpty());
          return notStopped.isEmpty()
              ? Verdict.pass("guardrail " + name + " stopped every attempt")
              : Verdict.fail("guardrail " + name + " did not stop " + notStopped);
        });
  }

  private static void requireRate(double minimumRate) {
    if (!(minimumRate >= 0.0 && minimumRate <= 1.0))
      throw new IllegalArgumentException(
          "minimum rate must be between 0.0 and 1.0, was " + minimumRate);
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

  /**
   * Combines this gate with another gate, requiring both to pass.
   *
   * @param other the gate to combine with this one
   * @return a compound gate requiring both gates to pass
   * @throws IllegalArgumentException when {@code other} is null
   */
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
