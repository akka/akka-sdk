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
    assertThat(report.render()).contains("target failed on [c2]");
  }

  @Test
  void aRunWithNoGatePassesWhenEveryCaseDoes() {
    var report =
        ExperimentRunner.against(new ExperimentRunner().cases(expectingReply("c1", "c1")), target)
            .run();

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("1/1 cases passed (100%)");
  }
}
