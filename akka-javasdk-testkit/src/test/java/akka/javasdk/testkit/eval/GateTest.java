/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static akka.javasdk.testkit.eval.Targets.QUALITY;
import static akka.javasdk.testkit.eval.Targets.TONE;
import static akka.javasdk.testkit.eval.Targets.bindings;
import static akka.javasdk.testkit.eval.Targets.interaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.annotations.Component;
import akka.javasdk.evaluation.Evaluation;
import akka.javasdk.evaluation.EvaluationContext;
import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.testkit.TestLedgerClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Gates over case results arranged by a scripted target and an in-memory ledger. */
class GateTest {

  private final TestLedgerClient ledger = TestLedgerClient.create();

  private static EvalCase<String> evalCase(String id) {
    return EvalCase.of(id, "a question");
  }

  /** The quality evaluator passes every case but the given ones. */
  private EvalTarget<String> failingOn(String... caseIds) {
    var failing = List.of(caseIds);
    return Targets.passingWhen(ledger, QUALITY, (call, caseId) -> !failing.contains(caseId));
  }

  /**
   * The quality evaluator passes every attempt but the given calls, counted over cases and runs.
   */
  private EvalTarget<String> wrongOnCalls(Integer... wrongCalls) {
    var wrong = List.of(wrongCalls);
    return Targets.passingWhen(ledger, QUALITY, (call, caseId) -> !wrong.contains(call));
  }

  private ExperimentRunner.EvalReport run(
      Gate gate,
      EvalTarget<String> target,
      EvaluatorBindings agent,
      int runs,
      List<EvalCase<String>> cases) {
    return ExperimentRunner.against(
            new ExperimentRunner().cases(cases), target, ledger, agent, Duration.ofMillis(50))
        .repeat(runs)
        .gate(gate)
        .run();
  }

  private ExperimentRunner.EvalReport run(
      Gate gate, EvalTarget<String> target, int runs, List<EvalCase<String>> cases) {
    return run(gate, target, bindings(QUALITY), runs, cases);
  }

  private ExperimentRunner.EvalReport run(
      Gate gate, EvalTarget<String> target, List<EvalCase<String>> cases) {
    return run(gate, target, 1, cases);
  }

  @Test
  void aPassRateToleratesOneWrongCaseInFour() {
    var cases = List.of(evalCase("c1"), evalCase("c2"), evalCase("c3"), evalCase("c4"));

    assertThat(run(Gate.passRateShouldBeAtLeast(0.75), failingOn("c4"), cases).passed()).isTrue();
    assertThat(run(Gate.passRateShouldBeAtLeast(0.8), failingOn("c4"), cases).passed()).isFalse();
  }

  @Test
  void anEvaluatorIsRatedOverTheCasesWhereItWasConclusive() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(interaction(interactionId, turn.sessionId(), "a reply", List.of()));
              ledger.seed(Targets.inconclusive(interactionId, QUALITY, "no reply"));
              ledger.seed(Targets.verdict(interactionId, TONE, Evaluation.passed("polite")));
            });
    var cases = List.of(evalCase("c1"), evalCase("c2"));

    var report =
        run(
            Gate.passRateShouldBeAtLeast(ToneEvaluator.class, 1.0),
            target,
            bindings(QUALITY, TONE),
            1,
            cases);

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("tone 2/2").contains("quality 0/0 (2 inconclusive)");
    var quality =
        run(
            Gate.passRateShouldBeAtLeast(QualityEvaluator.class, 0.5),
            target,
            bindings(QUALITY, TONE),
            1,
            cases);
    assertThat(quality.passed()).isFalse();
    assertThat(quality.render()).contains("quality judged no case");
  }

  @Test
  void anEvaluatorRateLeavesNotApplicableResultsOut() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(interaction(interactionId, turn.sessionId(), "a reply", List.of()));
              switch (turn.caseId()) {
                case "c1" ->
                    ledger.seed(Targets.notApplicable(interactionId, QUALITY, "no tool call"));
                default ->
                    ledger.seed(Targets.verdict(interactionId, QUALITY, Evaluation.passed("fine")));
              }
            });
    var cases = List.of(evalCase("c1"), evalCase("c2"));

    var report = run(Gate.passRateShouldBeAtLeast(QualityEvaluator.class, 1.0), target, cases);

    assertThat(report.passed()).isTrue();
    assertThat(report.render())
        .contains("quality rate 1.00 over 1 judged attempts")
        .contains("quality 1/1 (1 not applicable)");
  }

  @Test
  void anInconclusiveAttemptCountsAgainstThePassRate() {
    var cases = List.of(evalCase("c1"), evalCase("c2"), evalCase("c3"), evalCase("c4"));
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(interaction(interactionId, turn.sessionId(), "r", List.of()));
              switch (turn.caseId()) {
                case "c3" ->
                    ledger.seed(
                        Targets.verdict(interactionId, QUALITY, Evaluation.failed("wrong")));
                case "c4" -> ledger.seed(Targets.inconclusive(interactionId, QUALITY, "no reply"));
                default ->
                    ledger.seed(Targets.verdict(interactionId, QUALITY, Evaluation.passed("fine")));
              }
            });

    var report = run(Gate.passRateShouldBeAtLeast(0.5), target, cases);

    assertThat(report.passRate()).isEqualTo(0.5);
    assertThat(report.passed()).isTrue();
    assertThat(report.render())
        .contains(
            "pass rate 0.50 over 4 attempts (4 cases, 1 run) with 1 inconclusive, required 0.50");
    assertThat(run(Gate.passRateShouldBeAtLeast(0.75), target, cases).passed()).isFalse();
  }

  @Test
  void anInconclusiveAttemptFailsTheDefaultGate() {
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              ledger.seed(interaction(interactionId, turn.sessionId(), "r", List.of()));
              switch (turn.caseId()) {
                case "c2" ->
                    ledger.seed(
                        Targets.verdict(interactionId, QUALITY, Evaluation.failed("wrong")));
                case "c3" -> {}
                default ->
                    ledger.seed(Targets.verdict(interactionId, QUALITY, Evaluation.passed("fine")));
              }
            });

    var report =
        run(
            Gate.allCasesShouldPass(),
            target,
            List.of(evalCase("c1"), evalCase("c2"), evalCase("c3")));

    assertThat(report.passed()).isFalse();
    assertThat(report.render())
        .contains("gate: FAILED — failed cases [c2 (run 1)], inconclusive cases [c3 (run 1)]");
  }

  @Test
  void anEvaluatorThatJudgedNothingCannotBeRated() {
    var report =
        run(
            Gate.passRateShouldBeAtLeast(ToneEvaluator.class, 1.0),
            failingOn(),
            List.of(evalCase("c1")));

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("tone judged no case");
  }

  @Test
  void aThrownTargetFailsTheRunWhateverTheRateIs() {
    var answering = failingOn();
    EvalTarget<String> throwing =
        turn -> {
          if (turn.caseId().equals("c2")) throw new IllegalStateException("model unavailable");
          return answering.call(turn);
        };
    var cases = List.of(evalCase("c1"), evalCase("c2"));

    var report =
        run(Gate.passRateShouldBeAtLeast(0.5).and(Gate.targetShouldNotFail()), throwing, cases);

    assertThat(report.passRate()).isEqualTo(0.5);
    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("target failed on [c2 (run 1)]");
  }

  @Test
  void aRunWithNoGatePassesWhenEveryCaseDoes() {
    var report =
        ExperimentRunner.against(
                new ExperimentRunner().cases(evalCase("c1")),
                failingOn(),
                ledger,
                bindings(QUALITY),
                Duration.ofMillis(50))
            .run();

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("1 case, 1 run: 1/1 attempts passed (100%)");
  }

  @Test
  void aPassRateCountsEveryAttemptOfARepeatedRun() {
    var cases = List.of(evalCase("c1"));

    var passing = run(Gate.passRateShouldBeAtLeast(0.75), wrongOnCalls(4), 4, cases);
    assertThat(passing.passed()).isTrue();
    assertThat(passing.render())
        .contains("pass rate 0.75 over 4 attempts (1 case, 4 runs), required 0.75");

    var failing = run(Gate.passRateShouldBeAtLeast(0.8), wrongOnCalls(4), 4, cases);
    assertThat(failing.passed()).isFalse();
  }

  @Test
  void allCasesShouldPassNamesTheRunsACaseFailedIn() {
    var cases = List.of(evalCase("c1"), evalCase("c2"));

    // Calls 2 and 6 are c2 in runs 1 and 3.
    var report = run(Gate.allCasesShouldPass(), wrongOnCalls(2, 6), 3, cases);

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("failed cases [c2 (runs 1, 3)]");
  }

  @Test
  void anEvaluatorIsRatedOverEveryAttemptOfARepeatedRun() {
    var report =
        run(
            Gate.passRateShouldBeAtLeast(QualityEvaluator.class, 0.5),
            wrongOnCalls(1),
            2,
            List.of(evalCase("c1")));

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("quality rate 0.50 over 2 judged attempts, required 0.50");
  }

  @Test
  void anEvaluatorWithoutAComponentAnnotationIsRejected() {
    assertThatThrownBy(() -> Gate.passRateShouldBeAtLeast(Object.class, 0.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("@Component");
  }

  /** Stands for the evaluator bound as {@link Targets#QUALITY}; the ledger is seeded directly. */
  @Component(id = QUALITY)
  static class QualityEvaluator extends Evaluator {
    @Override
    public Effect evaluate(EvaluationContext context) {
      return effects().inconclusive("not run");
    }
  }

  /** Stands for the evaluator bound as {@link Targets#TONE}; the ledger is seeded directly. */
  @Component(id = TONE)
  static class ToneEvaluator extends Evaluator {
    @Override
    public Effect evaluate(EvaluationContext context) {
      return effects().inconclusive("not run");
    }
  }

  /** The quality evaluator passes; the no-refunds guardrail denies the given cases. */
  private EvalTarget<String> denyingOn(String... caseIds) {
    var denying = List.of(caseIds);
    return Targets.scripted(
        (turn, interactionId) -> {
          var interaction = interaction(interactionId, turn.sessionId(), "r", List.of());
          ledger.seed(interaction);
          ledger.seed(
              Targets.guardrail(
                  interaction,
                  "no-refunds",
                  denying.contains(turn.caseId())
                      ? GuardrailDecisionRecord.Decision.DENY
                      : GuardrailDecisionRecord.Decision.ALLOW,
                  "refund"));
          ledger.seed(Targets.verdict(interactionId, QUALITY, Evaluation.passed("fine")));
        });
  }

  @Test
  void aGuardrailThatMustNotDenyFailsTheRunWhenItDoes() {
    var cases = List.of(evalCase("c1"), evalCase("c2"), evalCase("c3"));

    var silent = run(Gate.guardrailShouldNotDeny("no-refunds"), denyingOn(), cases);
    assertThat(silent.passed()).isTrue();
    assertThat(silent.render()).contains("gate: passed — guardrail no-refunds denied nothing");

    var denied = run(Gate.guardrailShouldNotDeny("no-refunds"), denyingOn("c2"), cases);
    assertThat(denied.passed()).isFalse();
    assertThat(denied.render())
        .contains("gate: FAILED — guardrail no-refunds denied on [c2 (run 1)]")
        .contains("guardrail no-refunds: denied 1, allowed 2");
  }

  @Test
  void aGuardrailThatMustDenyFailsTheRunWhenItLetsAnAttemptThrough() {
    var cases = List.of(evalCase("c1"), evalCase("c2"));

    var caught = run(Gate.guardrailShouldDeny("no-refunds"), denyingOn("c1", "c2"), cases);
    assertThat(caught.passed()).isTrue();
    assertThat(caught.render())
        .contains("gate: passed — guardrail no-refunds denied every attempt");

    var missed = run(Gate.guardrailShouldDeny("no-refunds"), denyingOn("c1"), cases);
    assertThat(missed.passed()).isFalse();
    assertThat(missed.render()).contains("guardrail no-refunds did not deny on [c2 (run 1)]");
    var absent = run(Gate.guardrailShouldDeny("pii"), denyingOn("c1", "c2"), cases);
    assertThat(absent.render()).contains("guardrail pii did not deny on [c1 (run 1), c2 (run 1)]");
  }

  @Test
  void aGuardrailGateCountsEveryAttemptOfARepeatedRun() {
    var calls = new AtomicInteger();
    var target =
        Targets.scripted(
            (turn, interactionId) -> {
              var interaction = interaction(interactionId, turn.sessionId(), "r", List.of());
              ledger.seed(interaction);
              ledger.seed(
                  Targets.guardrail(
                      interaction,
                      "no-refunds",
                      calls.incrementAndGet() == 2
                          ? GuardrailDecisionRecord.Decision.DENY
                          : GuardrailDecisionRecord.Decision.ALLOW,
                      "refund"));
              ledger.seed(Targets.verdict(interactionId, QUALITY, Evaluation.passed("fine")));
            });

    var report = run(Gate.guardrailShouldNotDeny("no-refunds"), target, 2, List.of(evalCase("c1")));

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("guardrail no-refunds denied on [c1 (run 2)]");
  }

  @Test
  void aTargetFailureInOneRunFailsTheTargetGate() {
    var calls = new AtomicInteger();
    var answering = failingOn();
    EvalTarget<String> throwing =
        turn -> {
          if (calls.incrementAndGet() == 2) throw new IllegalStateException("model unavailable");
          return answering.call(turn);
        };

    var report = run(Gate.targetShouldNotFail(), throwing, 2, List.of(evalCase("c1")));

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("target failed on [c1 (run 2)]");
  }
}
