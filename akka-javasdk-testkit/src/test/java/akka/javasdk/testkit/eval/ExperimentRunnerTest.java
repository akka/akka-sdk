/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The runner over a scripted target that needs no runtime. */
class ExperimentRunnerTest {

  /** Answers with the given text and tool calls. */
  private static EvalTarget targetThat(String answer, ToolCall... calls) {
    return turn -> EvalTarget.Outcome.answered(new Interaction(answer, List.of(calls)));
  }

  private static ToolCall call(String name, String argument, Object value) {
    return new ToolCall(name, Map.of(argument, value));
  }

  private CaseResultOf run(EvalTarget target, Expectations expectations) {
    var evalCase = EvalCase.of("c", "a question", expectations);
    return new CaseResultOf(ExperimentRunner.forTarget(target).runSingle(evalCase));
  }

  /** Reads one evaluator's finding out of a result. */
  private record CaseResultOf(ExperimentRunner.CaseResult result) {
    EvalResult finding(String evaluator) {
      return result.evalResults().stream()
          .filter(f -> f.evaluator().equals(evaluator))
          .findFirst()
          .orElseThrow(() -> new AssertionError(evaluator + " did not report"));
    }
  }

  @Test
  void holdsTheReplyAndTheToolCallsAgainstTheExpectations() {
    var target =
        targetThat("Ada Lovelace is a gold customer.", call("getCustomer", "customerId", "cust_1"));

    var run =
        run(
            target,
            Expectations.expect()
                .tools("getCustomer")
                .toolArgument("getCustomer", "customerId", "cust_1")
                .forbiddenTools("openTickets")
                .answerContains("ada lovelace")
                .answerMatches("gold"));

    assertThat(run.result().passed()).isTrue();
    assertThat(run.result().evalResults())
        .extracting(EvalResult::verdict)
        .containsOnly(EvalResult.Verdict.PASS);
    assertThat(run.result().interaction().toolCalls()).hasSize(1);
  }

  @Test
  void aMissingToolFailsToolsAndOnlyAbstainsTheChecksThatNeededIt() {
    var run =
        run(
            targetThat("I do not know."),
            Expectations.expect()
                .tools("getCustomer")
                .toolOrder("getCustomer")
                .toolArgument("getCustomer", "customerId", "cust_1"));

    assertThat(run.finding(Evaluators.TOOLS).verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(run.finding(Evaluators.TOOLS).detail()).contains("no tools");
    assertThat(run.finding(Evaluators.TOOL_ORDER).verdict()).isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(run.finding(Evaluators.TOOL_ARGUMENTS).verdict())
        .isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(run.result().passed()).isFalse();
  }

  @Test
  void ordersToolsRelativelySoOtherCallsMayComeBetween() {
    var inOrder =
        run(
            targetThat(
                "done",
                ToolCall.of("getCustomer"),
                ToolCall.of("somethingElse"),
                ToolCall.of("openTickets")),
            Expectations.expect().toolOrder("getCustomer", "openTickets"));
    assertThat(inOrder.finding(Evaluators.TOOL_ORDER).verdict()).isEqualTo(EvalResult.Verdict.PASS);

    var reversed =
        run(
            targetThat("done", ToolCall.of("openTickets"), ToolCall.of("getCustomer")),
            Expectations.expect().toolOrder("getCustomer", "openTickets"));
    assertThat(reversed.finding(Evaluators.TOOL_ORDER).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(reversed.finding(Evaluators.TOOL_ORDER).detail())
        .contains("[openTickets, getCustomer]");
  }

  @Test
  void aWrongArgumentNamesWhatArrived() {
    var run =
        run(
            targetThat("done", call("getCustomer", "customerId", "cust_2")),
            Expectations.expect().toolArgument("getCustomer", "customerId", "cust_1"));

    assertThat(run.finding(Evaluators.TOOL_ARGUMENTS).detail())
        .contains("expected cust_1")
        .contains("was [cust_2]");
  }

  @Test
  void aRecordedNumberComparesAgainstTheValueTheToolReceived() {
    var run =
        run(
            targetThat("done", call("charge", "amount", 12L)),
            Expectations.expect().toolArgument("charge", "amount", 12));

    assertThat(run.finding(Evaluators.TOOL_ARGUMENTS).verdict()).isEqualTo(EvalResult.Verdict.PASS);
  }

  @Test
  void aForbiddenToolFailsTheCaseEvenWhenTheAnswerIsRight() {
    var run =
        run(
            targetThat("Ada Lovelace", ToolCall.of("openTickets")),
            Expectations.expect().answerContains("Ada").forbiddenTools("openTickets"));

    assertThat(run.result().passed()).isFalse();
    assertThat(run.finding(Evaluators.FORBIDDEN_TOOLS).detail()).contains("openTickets");
  }

  @Test
  void aThrownTargetIsAFailedCaseNotAWrongAnswer() {
    EvalTarget throwing =
        turn -> {
          throw new IllegalStateException("model unavailable");
        };

    var result =
        ExperimentRunner.forTarget(throwing)
            .runSingle(EvalCase.of("c", "a question", Expectations.expect().answerContains("x")));

    assertThat(result.passed()).isFalse();
    assertThat(result.evalResults()).hasSize(1);
    assertThat(result.evalResults().get(0).evaluator()).isEqualTo(Evaluators.TARGET);
    assertThat(result.evalResults().get(0).detail()).contains("model unavailable");
  }

  @Test
  void aToolResultExpectationAbstainsWhereTheEvidenceCarriesNoResults() {
    var run =
        run(
            targetThat("Ada Lovelace", call("getCustomer", "customerId", "cust_1")),
            Expectations.expect().toolResult("getCustomer", "Ada Lovelace"));

    assertThat(run.result().passed()).isTrue();
    assertThat(run.finding(Evaluators.TOOL_RESULTS).verdict())
        .isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(run.finding(Evaluators.TOOL_RESULTS).detail()).contains("no recorded result");
  }

  /** A target whose evidence carries model calls, tokens and timing. */
  private static EvalTarget tracedThat(
      String answer, int modelCalls, long tokensPerCall, Duration took) {
    var calls =
        java.util.stream.IntStream.range(0, modelCalls)
            .mapToObj(
                i ->
                    new ModelCall(
                        "test", "custom", List.of("STOP"), tokensPerCall, 0, took, "", ""))
            .toList();
    return turn ->
        EvalTarget.Outcome.answered(
            new Interaction(answer, List.of(), calls, List.of(), took, answer));
  }

  @Test
  void aToolCallBudgetCountsEveryCall() {
    var twoCalls = targetThat("done", ToolCall.of("getCustomer"), ToolCall.of("getCustomer"));

    var within = run(twoCalls, Expectations.expect().toolCallsAtMost(2));
    assertThat(within.finding(Evaluators.TOOL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(twoCalls, Expectations.expect().toolCallsAtMost(1));
    assertThat(over.finding(Evaluators.TOOL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.finding(Evaluators.TOOL_CALL_BUDGET).detail())
        .contains("made 2 tool calls, allowed 1");
  }

  @Test
  void aModelCallBudgetReadsTheTracedCallsAndAbstainsWithoutThem() {
    var threeCalls = tracedThat("done", 3, 100, Duration.ofMillis(40));

    var within = run(threeCalls, Expectations.expect().modelCallsAtMost(3));
    assertThat(within.finding(Evaluators.MODEL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(threeCalls, Expectations.expect().modelCallsAtMost(2));
    assertThat(over.finding(Evaluators.MODEL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.finding(Evaluators.MODEL_CALL_BUDGET).detail())
        .contains("made 3 model calls, allowed 2");

    var untraced = run(targetThat("done"), Expectations.expect().modelCallsAtMost(1));
    assertThat(untraced.finding(Evaluators.MODEL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(untraced.result().passed()).isTrue();
  }

  @Test
  void aTokenBudgetSumsInputAndOutputAndAbstainsWhenNoneWereReported() {
    var threeHundred = tracedThat("done", 3, 100, Duration.ofMillis(40));

    var within = run(threeHundred, Expectations.expect().tokensAtMost(300));
    assertThat(within.finding(Evaluators.TOKEN_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(threeHundred, Expectations.expect().tokensAtMost(299));
    assertThat(over.finding(Evaluators.TOKEN_BUDGET).verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.finding(Evaluators.TOKEN_BUDGET).detail()).contains("used 300 tokens");

    var unreported =
        run(
            tracedThat("done", 2, 0, Duration.ofMillis(40)),
            Expectations.expect().tokensAtMost(10));
    assertThat(unreported.finding(Evaluators.TOKEN_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.ABSTAIN);
  }

  @Test
  void aLatencyBudgetReadsTheCommandsDurationAndAbstainsWithoutTiming() {
    var forty = tracedThat("done", 1, 10, Duration.ofMillis(40));

    var within = run(forty, Expectations.expect().latencyAtMost(Duration.ofMillis(40)));
    assertThat(within.finding(Evaluators.LATENCY_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(forty, Expectations.expect().latencyAtMost(Duration.ofMillis(39)));
    assertThat(over.finding(Evaluators.LATENCY_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.finding(Evaluators.LATENCY_BUDGET).detail())
        .contains("took 40 ms, allowed 39 ms");

    var untimed =
        run(targetThat("done"), Expectations.expect().latencyAtMost(Duration.ofSeconds(1)));
    assertThat(untimed.finding(Evaluators.LATENCY_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.ABSTAIN);
  }

  @Test
  void theReportSumsWhatTheRunSpentOverTheCasesWithEvidence() {
    var report =
        ExperimentRunner.cases(
                List.of(
                    EvalCase.of("quick", "q", Expectations.none()),
                    EvalCase.of("slow", "q", Expectations.none()),
                    EvalCase.of("untraced", "q", Expectations.none())))
            .target(
                turn ->
                    switch (turn.caseId()) {
                      case "quick" -> tracedThat("done", 1, 100, Duration.ofMillis(40)).call(turn);
                      case "slow" -> tracedThat("done", 3, 200, Duration.ofMillis(900)).call(turn);
                      default -> targetThat("done").call(turn);
                    })
            .run();

    assertThat(report.render())
        .contains(
            "spend: 4 model calls, 700 tokens in, 0 out, 940 ms in total, slowest slow at 900 ms,"
                + " over 2/3 cases with evidence");
  }

  @Test
  void theReportHasNoSpendLineWithoutEvidence() {
    var report =
        ExperimentRunner.cases(List.of(EvalCase.of("c", "q", Expectations.none())))
            .target(targetThat("done"))
            .run();

    assertThat(report.render()).doesNotContain("spend:");
  }

  @Test
  void aFailedCaseShowsTheModelsOwnTextWhenTheReplyWasMappedFromIt() {
    EvalTarget mapped =
        turn ->
            EvalTarget.Outcome.answered(
                new Interaction(
                    "{\"tier\":\"gold\"}",
                    List.of(),
                    List.of(),
                    List.of(),
                    Duration.ofMillis(5),
                    "```json\n{\"tier\":\"gold\"}\n```"));

    var result =
        ExperimentRunner.forTarget(mapped)
            .runSingle(EvalCase.of("c", "q", Expectations.expect().answerContains("silver")));

    assertThat(result.describe())
        .contains("reply: {\"tier\":\"gold\"}")
        .contains("model text: ```json {\"tier\":\"gold\"} ```");

    var same = run(targetThat("plain"), Expectations.expect().answerContains("other"));
    assertThat(same.result().describe()).doesNotContain("model text:");
  }

  @Test
  void aBudgetRefusesAValueThatCannotBeMet() {
    assertThatThrownBy(() -> Expectations.expect().modelCallsAtMost(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Expectations.expect().tokensAtMost(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Expectations.expect().latencyAtMost(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Expectations.expect().toolCallsAtMost(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aFailedTurnKeepsTheToolCallsItsEvidenceSourceSaw() {
    var seen = call("getCustomer", "customerId", "cust_404");
    EvalTarget failing = turn -> EvalTarget.Outcome.failed("no customer cust_404", List.of(seen));

    var result =
        ExperimentRunner.forTarget(failing)
            .runSingle(EvalCase.of("c", "a question", Expectations.expect().tools("getCustomer")));

    assertThat(result.passed()).isFalse();
    assertThat(result.evalResults())
        .singleElement()
        .satisfies(finding -> assertThat(finding.evaluator()).isEqualTo(Evaluators.TARGET));
    assertThat(result.interaction().toolCalls()).containsExactly(seen);
    assertThat(result.describe()).contains("getCustomer{customerId=cust_404}");
  }

  @Test
  void aThrownSetupIsReportedBeforeTheTurnIsTaken() {
    var target = targetThat("never asked");
    var evalCase =
        new EvalCase(
            "c",
            "a question",
            () -> {
              throw new IllegalStateException("fixture missing");
            },
            Expectations.expect().tools("getCustomer"));

    var result = ExperimentRunner.forTarget(target).runSingle(evalCase);

    assertThat(result.evalResults())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.evaluator()).isEqualTo(Evaluators.SETUP);
              assertThat(finding.detail()).contains("fixture missing");
            });
  }

  @Test
  void aRunLevelEvaluatorRunsOnEveryCase() {
    Evaluator noApology =
        (evalCase, reply, calls) ->
            reply.text().contains("sorry")
                ? EvalResult.fail("no-apology", "the reply apologises")
                : EvalResult.pass("no-apology");

    var report =
        ExperimentRunner.cases(
                List.of(
                    EvalCase.of("polite", "a question", Expectations.none()),
                    EvalCase.of("apologetic", "another question", Expectations.none())))
            .target(
                turn ->
                    EvalTarget.Outcome.answered(
                        Interaction.of(turn.caseId().equals("apologetic") ? "sorry" : "sure")))
            .evaluator(noApology)
            .run();

    assertThat(report.passRate()).isEqualTo(0.5);
    assertThat(report.render()).contains("no-apology 1/2").contains("case apologetic FAILED");
  }

  @Test
  void aTargetsOwnToolEvidenceIsUsedWhenItSuppliesSome() {
    EvalTarget withEvidence =
        turn ->
            EvalTarget.Outcome.answered(
                new Interaction("done", List.of(call("getCustomer", "customerId", "cust_1"))));

    var result =
        ExperimentRunner.forTarget(withEvidence)
            .runSingle(
                EvalCase.of(
                    "c",
                    "a question",
                    Expectations.expect().toolArgument("getCustomer", "customerId", "cust_1")));

    assertThat(result.passed()).isTrue();
  }

  @Test
  void aRunNeedsAnAgentAndAtLeastOneCase() {
    assertThatThrownBy(() -> ExperimentRunner.cases(List.of()).target(targetThat("x")).run())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no cases");

    assertThatThrownBy(
            () -> ExperimentRunner.cases(List.of(EvalCase.of("c", "q", Expectations.none()))).run())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no agent");
  }

  @Test
  void aFailedCaseDescribesTheEvidenceItWasJudgedOn() {
    var run =
        run(
            targetThat("I could not find them.", call("getCustomer", "customerId", "cust_9")),
            Expectations.expect().answerContains("Ada Lovelace"));

    assertThat(run.result().describe())
        .contains("case c FAILED")
        .contains("reply: I could not find them.")
        .contains("getCustomer{customerId=cust_9}")
        .contains("FAIL answer-contains");
  }
}
