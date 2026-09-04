/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Gates over case results arranged by a scripted target. */
class GateTest {

  /** Answers with the case id and calls getCustomer with it, so a case can be made to fail. */
  private final EvalTarget target =
      turn ->
          EvalTarget.Outcome.answered(
              new Interaction(
                  turn.caseId(),
                  List.of(new ToolCall("getCustomer", Map.of("customerId", turn.caseId())))));

  private static EvalCase expectingAnswer(String id, String expected) {
    return EvalCase.of(id, "a question", Expectations.expect().answerContains(expected));
  }

  private ExperimentRunner.EvalReport run(Gate gate, List<EvalCase> cases) {
    return ExperimentRunner.forTarget(target).cases(cases).gate(gate).run();
  }

  @Test
  void aPassRateToleratesOneWrongCaseInFour() {
    var cases =
        List.of(
            expectingAnswer("c1", "c1"),
            expectingAnswer("c2", "c2"),
            expectingAnswer("c3", "c3"),
            expectingAnswer("c4", "something else"));

    assertThat(run(Gate.passRateAtLeast(0.75), cases).passed()).isTrue();
    assertThat(run(Gate.passRateAtLeast(0.8), cases).passed()).isFalse();
  }

  @Test
  void anEvaluatorIsRatedOverTheCasesThatDidNotAbstain() {
    var cases =
        List.of(
            EvalCase.of(
                "c1", "q", Expectations.expect().toolArgument("getCustomer", "customerId", "c1")),
            EvalCase.of(
                "c2",
                "q",
                Expectations.expect().toolArgument("getCustomer", "customerId", "wrong")),
            EvalCase.of("c3", "q", Expectations.expect().toolArgument("neverCalled", "id", "x")));

    var report = run(Gate.evaluatorRateAtLeast(Evaluators.TOOL_ARGUMENTS, 0.5), cases);

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("tool-arguments 1/2 (1 abstained)");
    assertThat(run(Gate.evaluatorRateAtLeast(Evaluators.TOOL_ARGUMENTS, 0.9), cases).passed())
        .isFalse();
  }

  @Test
  void anEvaluatorThatJudgedNothingCannotBeRated() {
    var report =
        run(
            Gate.evaluatorRateAtLeast(Evaluators.ANSWER_MATCHES, 1.0),
            List.of(expectingAnswer("c1", "c1")));

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("judged no case");
  }

  @Test
  void aThrownTargetFailsTheRunWhateverTheRateIs() {
    EvalTarget throwing =
        turn -> {
          if (turn.caseId().equals("c2")) throw new IllegalStateException("model unavailable");
          return EvalTarget.Outcome.answered(Interaction.of(turn.caseId()));
        };
    var cases = List.of(expectingAnswer("c1", "c1"), expectingAnswer("c2", "c2"));

    var report =
        new ExperimentRunner()
            .target(throwing)
            .cases(cases)
            .gate(Gate.passRateAtLeast(0.5).and(Gate.noTargetFailures()))
            .run();

    assertThat(report.passRate()).isEqualTo(0.5);
    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("target failed on [c2]");
  }

  @Test
  void aRunWithNoGatePassesWhenEveryCaseDoes() {
    var report =
        ExperimentRunner.forTarget(target).cases(List.of(expectingAnswer("c1", "c1"))).run();

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("1/1 cases passed (100%)");
  }
}
