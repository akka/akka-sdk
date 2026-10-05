/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.ToolCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Gates over case results arranged by a scripted target. */
class GateTest {

  /** Answers with the case id and calls getCustomer with it, so a case can be made to fail. */
  private final EvalTarget<String> target =
      turn ->
          EvalTarget.Outcome.answered(
              new Interaction(
                  turn.command(),
                  turn.caseId(),
                  List.of(new ToolCall("getCustomer", Map.of("customerId", turn.caseId())))));

  private static EvalCase<String> expectingReply(String id, String expected) {
    return EvalCase.of(id, "a question", Evaluators.replyShouldContain(expected));
  }

  private ExperimentRunner.EvalReport run(Gate gate, List<EvalCase<String>> cases) {
    return ExperimentRunner.against(new ExperimentRunner().cases(cases), target).gate(gate).run();
  }

  @Test
  void aPassRateToleratesOneWrongCaseInFour() {
    var cases =
        List.of(
            expectingReply("c1", "c1"),
            expectingReply("c2", "c2"),
            expectingReply("c3", "c3"),
            expectingReply("c4", "something else"));

    assertThat(run(Gate.passRateShouldBeAtLeast(0.75), cases).passed()).isTrue();
    assertThat(run(Gate.passRateShouldBeAtLeast(0.8), cases).passed()).isFalse();
  }

  @Test
  void anEvaluatorIsRatedOverTheCasesWhereItWasConclusive() {
    var cases =
        List.of(
            EvalCase.of(
                "c1", "q", Evaluators.shouldCallToolWith("getCustomer", "customerId", "c1")),
            EvalCase.of(
                "c2", "q", Evaluators.shouldCallToolWith("getCustomer", "customerId", "wrong")),
            EvalCase.of("c3", "q", Evaluators.shouldCallToolWith("neverCalled", "id", "x")));

    var report = run(Gate.passRateShouldBeAtLeast(Evaluators.ToolArgument.class, 0.5), cases);

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("tool-arguments 1/2 (1 inconclusive)");
    assertThat(
            run(Gate.passRateShouldBeAtLeast(Evaluators.ToolArgument.class, 0.9), cases).passed())
        .isFalse();
  }

  @Test
  void anEvaluatorThatJudgedNothingCannotBeRated() {
    var report =
        run(
            Gate.passRateShouldBeAtLeast(Evaluators.ReplyMatches.class, 1.0),
            List.of(expectingReply("c1", "c1")));

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("judged no case");
  }

  @Test
  void aThrownTargetFailsTheRunWhateverTheRateIs() {
    EvalTarget<String> throwing =
        turn -> {
          if (turn.caseId().equals("c2")) throw new IllegalStateException("model unavailable");
          return EvalTarget.Outcome.answered(Interaction.of(turn.command(), turn.caseId()));
        };
    var cases = List.of(expectingReply("c1", "c1"), expectingReply("c2", "c2"));

    var report =
        ExperimentRunner.against(new ExperimentRunner().cases(cases), throwing)
            .gate(Gate.passRateShouldBeAtLeast(0.5).and(Gate.targetShouldNotFail()))
            .run();

    assertThat(report.passRate()).isEqualTo(0.5);
    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("target failed on [c2 (run 1)]");
  }

  @Test
  void aRunWithNoGatePassesWhenEveryCaseDoes() {
    var report =
        ExperimentRunner.against(new ExperimentRunner().cases(expectingReply("c1", "c1")), target)
            .run();

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("1 case, 1 run: 1/1 turns passed (100%)");
  }

  /** Answers with the case id, except "wrong" on the given calls to it, counting over all cases. */
  private static EvalTarget<String> wrongOnCalls(Integer... wrongCalls) {
    var wrong = List.of(wrongCalls);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    return turn ->
        EvalTarget.Outcome.answered(
            Interaction.of(
                turn.command(), wrong.contains(calls.incrementAndGet()) ? "wrong" : turn.caseId()));
  }

  private ExperimentRunner.EvalReport runRepeated(
      Gate gate, EvalTarget<String> target, int runs, List<EvalCase<String>> cases) {
    return ExperimentRunner.against(new ExperimentRunner().cases(cases), target)
        .runs(runs)
        .gate(gate)
        .run();
  }

  @Test
  void aPassRateCountsEveryTurnOfARepeatedRun() {
    var cases = List.of(expectingReply("c1", "c1"));

    var passing = runRepeated(Gate.passRateShouldBeAtLeast(0.75), wrongOnCalls(4), 4, cases);
    assertThat(passing.passed()).isTrue();
    assertThat(passing.render())
        .contains("pass rate 0.75 over 4 turns (1 case, 4 runs), required 0.75");

    var failing = runRepeated(Gate.passRateShouldBeAtLeast(0.8), wrongOnCalls(4), 4, cases);
    assertThat(failing.passed()).isFalse();
  }

  @Test
  void allCasesShouldPassNamesTheRunsACaseFailedIn() {
    var cases = List.of(expectingReply("c1", "c1"), expectingReply("c2", "c2"));

    // Calls 2 and 6 are c2 in runs 1 and 3.
    var report = runRepeated(Gate.allCasesShouldPass(), wrongOnCalls(2, 6), 3, cases);

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("failed cases [c2 (runs 1, 3)]");
  }

  @Test
  void anEvaluatorIsRatedOverEveryTurnOfARepeatedRun() {
    var cases = List.of(expectingReply("c1", "c1"));

    var report =
        runRepeated(
            Gate.passRateShouldBeAtLeast(Evaluators.ReplyContains.class, 0.5),
            wrongOnCalls(1),
            2,
            cases);

    assertThat(report.passed()).isTrue();
    assertThat(report.render())
        .contains("reply-contains rate 0.50 over 2 judged turns, required 0.50");
  }

  @Test
  void aTargetFailureInOneRunFailsTheTargetGate() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    EvalTarget<String> throwing =
        turn -> {
          if (calls.incrementAndGet() == 2) throw new IllegalStateException("model unavailable");
          return EvalTarget.Outcome.answered(Interaction.of(turn.command(), turn.caseId()));
        };

    var report =
        runRepeated(Gate.targetShouldNotFail(), throwing, 2, List.of(expectingReply("c1", "c1")));

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("target failed on [c1 (run 2)]");
  }
}
