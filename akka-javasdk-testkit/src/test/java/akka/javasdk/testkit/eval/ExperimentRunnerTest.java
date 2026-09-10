/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.ModelCall;
import akka.javasdk.testkit.ToolCall;
import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The runner over a scripted target that needs no runtime. */
class ExperimentRunnerTest {

  /** Answers with the given text and tool calls. */
  private static EvalTarget targetThat(String answer, ToolCall... calls) {
    return turn ->
        EvalTarget.Outcome.answered(new Interaction(turn.userMessage(), answer, List.of(calls)));
  }

  private static ToolCall call(String name, String argument, Object value) {
    return new ToolCall(name, Map.of(argument, value));
  }

  private CaseResultOf run(EvalTarget target, Evaluator... evaluators) {
    var evalCase = EvalCase.of("c", "a question", evaluators);
    return new CaseResultOf(single(target, evalCase));
  }

  /** The cases against a scripted target, ready to run. */
  private static Experiment experiment(EvalTarget target, EvalCase... cases) {
    return ExperimentRunner.against(new ExperimentRunner().cases(List.of(cases)), target);
  }

  /** Runs one case and reads its result out of the report. */
  private static ExperimentRunner.CaseResult single(EvalTarget target, EvalCase evalCase) {
    return experiment(target, evalCase).run().results().getFirst();
  }

  /** Reads one evaluator's result out of a case result. */
  private record CaseResultOf(ExperimentRunner.CaseResult result) {
    EvalResult evalResult(String evaluator) {
      return result.evalResults().stream()
          .filter(f -> Evaluators.sameName(f.evaluator(), evaluator))
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
            Evaluators.tools("getCustomer"),
            Evaluators.toolArgument("getCustomer", "customerId", "cust_1"),
            Evaluators.forbiddenTools("openTickets"),
            Evaluators.answerContains("ada lovelace"),
            Evaluators.answerMatches("gold"));

    assertThat(run.result().passed()).isTrue();
    assertThat(run.result().evalResults())
        .extracting(EvalResult::verdict)
        .containsOnly(EvalResult.Verdict.PASS);
    assertThat(run.result().interaction().toolCalls()).hasSize(1);
  }

  @Test
  void aMissingToolFailsToolsAndOnlyLeavesTheChecksThatNeededItInconclusive() {
    var run =
        run(
            targetThat("I do not know."),
            Evaluators.tools("getCustomer"),
            Evaluators.toolOrder("getCustomer"),
            Evaluators.toolArgument("getCustomer", "customerId", "cust_1"));

    assertThat(run.evalResult(Evaluators.TOOLS).verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(run.evalResult(Evaluators.TOOLS).detail()).contains("no tools");
    assertThat(run.evalResult(Evaluators.TOOL_ORDER).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
    assertThat(run.evalResult(Evaluators.TOOL_ARGUMENTS).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
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
            Evaluators.toolOrder("getCustomer", "openTickets"));
    assertThat(inOrder.evalResult(Evaluators.TOOL_ORDER).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var reversed =
        run(
            targetThat("done", ToolCall.of("openTickets"), ToolCall.of("getCustomer")),
            Evaluators.toolOrder("getCustomer", "openTickets"));
    assertThat(reversed.evalResult(Evaluators.TOOL_ORDER).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(reversed.evalResult(Evaluators.TOOL_ORDER).detail())
        .contains("[openTickets, getCustomer]");
  }

  @Test
  void aWrongArgumentNamesWhatArrived() {
    var run =
        run(
            targetThat("done", call("getCustomer", "customerId", "cust_2")),
            Evaluators.toolArgument("getCustomer", "customerId", "cust_1"));

    assertThat(run.evalResult(Evaluators.TOOL_ARGUMENTS).detail())
        .contains("expected cust_1")
        .contains("was [cust_2]");
  }

  @Test
  void aRecordedNumberComparesAgainstTheValueTheToolReceived() {
    var run =
        run(
            targetThat("done", call("charge", "amount", 12L)),
            Evaluators.toolArgument("charge", "amount", 12));

    assertThat(run.evalResult(Evaluators.TOOL_ARGUMENTS).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);
  }

  @Test
  void aForbiddenToolFailsTheCaseEvenWhenTheAnswerIsRight() {
    var run =
        run(
            targetThat("Ada Lovelace", ToolCall.of("openTickets")),
            Evaluators.answerContains("Ada"),
            Evaluators.forbiddenTools("openTickets"));

    assertThat(run.result().passed()).isFalse();
    assertThat(run.evalResult(Evaluators.FORBIDDEN_TOOLS).detail()).contains("openTickets");
  }

  @Test
  void aThrownTargetIsAFailedCaseNotAWrongAnswer() {
    EvalTarget throwing =
        turn -> {
          throw new IllegalStateException("model unavailable");
        };

    var result = single(throwing, EvalCase.of("c", "a question", Evaluators.answerContains("x")));

    assertThat(result.passed()).isFalse();
    assertThat(result.evalResults()).hasSize(1);
    assertThat(result.evalResults().get(0).evaluator()).isEqualTo(Evaluators.TARGET);
    assertThat(result.evalResults().get(0).detail()).contains("model unavailable");
  }

  @Test
  void aToolResultExpectationIsInconclusiveWhereTheEvidenceCarriesNoResults() {
    var run =
        run(
            targetThat("Ada Lovelace", call("getCustomer", "customerId", "cust_1")),
            Evaluators.toolResult("getCustomer", "Ada Lovelace"));

    assertThat(run.result().passed()).isTrue();
    assertThat(run.evalResult(Evaluators.TOOL_RESULTS).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
    assertThat(run.evalResult(Evaluators.TOOL_RESULTS).detail()).contains("no recorded result");
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
            new Interaction(turn.userMessage(), answer, List.of(), calls, List.of(), took, answer));
  }

  @Test
  void aToolCallBudgetCountsEveryCall() {
    var twoCalls = targetThat("done", ToolCall.of("getCustomer"), ToolCall.of("getCustomer"));

    var within = run(twoCalls, Evaluators.toolCallsAtMost(2));
    assertThat(within.evalResult(Evaluators.TOOL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(twoCalls, Evaluators.toolCallsAtMost(1));
    assertThat(over.evalResult(Evaluators.TOOL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.evalResult(Evaluators.TOOL_CALL_BUDGET).detail())
        .contains("made 2 tool calls, allowed 1");
  }

  @Test
  void aModelCallBudgetReadsTheTracedCallsAndIsInconclusiveWithoutThem() {
    var threeCalls = tracedThat("done", 3, 100, Duration.ofMillis(40));

    var within = run(threeCalls, Evaluators.modelCallsAtMost(3));
    assertThat(within.evalResult(Evaluators.MODEL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(threeCalls, Evaluators.modelCallsAtMost(2));
    assertThat(over.evalResult(Evaluators.MODEL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.evalResult(Evaluators.MODEL_CALL_BUDGET).detail())
        .contains("made 3 model calls, allowed 2");

    var untraced = run(targetThat("done"), Evaluators.modelCallsAtMost(1));
    assertThat(untraced.evalResult(Evaluators.MODEL_CALL_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
    assertThat(untraced.result().passed()).isTrue();
  }

  @Test
  void aTokenBudgetSumsInputAndOutputAndIsInconclusiveWhenNoneWereReported() {
    var threeHundred = tracedThat("done", 3, 100, Duration.ofMillis(40));

    var within = run(threeHundred, Evaluators.tokensAtMost(300));
    assertThat(within.evalResult(Evaluators.TOKEN_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(threeHundred, Evaluators.tokensAtMost(299));
    assertThat(over.evalResult(Evaluators.TOKEN_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.evalResult(Evaluators.TOKEN_BUDGET).detail()).contains("used 300 tokens");

    var unreported =
        run(tracedThat("done", 2, 0, Duration.ofMillis(40)), Evaluators.tokensAtMost(10));
    assertThat(unreported.evalResult(Evaluators.TOKEN_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
  }

  @Test
  void aLatencyBudgetReadsTheCommandsDurationAndIsInconclusiveWithoutTiming() {
    var forty = tracedThat("done", 1, 10, Duration.ofMillis(40));

    var within = run(forty, Evaluators.latencyAtMost(Duration.ofMillis(40)));
    assertThat(within.evalResult(Evaluators.LATENCY_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(forty, Evaluators.latencyAtMost(Duration.ofMillis(39)));
    assertThat(over.evalResult(Evaluators.LATENCY_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.evalResult(Evaluators.LATENCY_BUDGET).detail())
        .contains("took 40 ms, allowed 39 ms");

    var untimed = run(targetThat("done"), Evaluators.latencyAtMost(Duration.ofSeconds(1)));
    assertThat(untimed.evalResult(Evaluators.LATENCY_BUDGET).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
  }

  @Test
  void theReportSumsWhatTheRunSpentOverTheCasesWithEvidence() {
    EvalTarget target =
        turn ->
            switch (turn.caseId()) {
              case "quick" -> tracedThat("done", 1, 100, Duration.ofMillis(40)).call(turn);
              case "slow" -> tracedThat("done", 3, 200, Duration.ofMillis(900)).call(turn);
              default -> targetThat("done").call(turn);
            };

    var report =
        experiment(
                target,
                EvalCase.of("quick", "q"),
                EvalCase.of("slow", "q"),
                EvalCase.of("untraced", "q"))
            .run();

    assertThat(report.render())
        .contains(
            "spend: 4 model calls, 700 tokens in, 0 out, 940 ms in total, slowest slow at 900 ms,"
                + " over 2/3 cases with evidence");
  }

  @Test
  void theReportHasNoSpendLineWithoutEvidence() {
    var report = experiment(targetThat("done"), EvalCase.of("c", "q")).run();

    assertThat(report.render()).doesNotContain("spend:");
  }

  @Test
  void aFailedCaseShowsTheModelsOwnTextWhenTheReplyWasMappedFromIt() {
    EvalTarget mapped =
        turn ->
            EvalTarget.Outcome.answered(
                new Interaction(
                    turn.userMessage(),
                    "{\"tier\":\"gold\"}",
                    List.of(),
                    List.of(),
                    List.of(),
                    Duration.ofMillis(5),
                    "```json\n{\"tier\":\"gold\"}\n```"));

    var result = single(mapped, EvalCase.of("c", "q", Evaluators.answerContains("silver")));

    assertThat(result.describe())
        .contains("reply: {\"tier\":\"gold\"}")
        .contains("model text: ```json {\"tier\":\"gold\"} ```");

    var same = run(targetThat("plain"), Evaluators.answerContains("other"));
    assertThat(same.result().describe()).doesNotContain("model text:");
  }

  @Test
  void holdsTheReplyAgainstWhatItMustNotCarry() {
    var clean =
        run(
            targetThat("I cannot share my configuration."),
            Evaluators.answerLacks("SECRET-MARKER", "Never guess"),
            Evaluators.answerDoesNotMatch("\\b\\d{3}-\\d{2}-\\d{4}\\b"));
    assertThat(clean.result().passed()).isTrue();

    var leaking =
        run(
            targetThat("As instructed: secret-marker. Never guess a tier. SSN 123-45-6789."),
            Evaluators.answerLacks("SECRET-MARKER", "Never guess"),
            Evaluators.answerDoesNotMatch("\\b\\d{3}-\\d{2}-\\d{4}\\b"));
    assertThat(leaking.evalResult(Evaluators.ANSWER_LACKS).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(leaking.evalResult(Evaluators.ANSWER_LACKS).detail())
        .contains("[SECRET-MARKER, Never guess]");
    assertThat(leaking.evalResult(Evaluators.ANSWER_DOES_NOT_MATCH).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(leaking.evalResult(Evaluators.ANSWER_DOES_NOT_MATCH).detail())
        .contains("123-45-6789");

    assertThatThrownBy(() -> Evaluators.answerLacks()).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.answerLacks("ok", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsAPaymentCardNumberOutOfTheReply() {
    var card = Evaluators.answerLacksPaymentCard();

    var empty = run(targetThat(""), card);
    assertThat(empty.result().passed()).isTrue();

    var orderNumber = run(targetThat("Order 1234567890123 shipped on Tuesday."), card);
    assertThat(orderNumber.result().passed()).isTrue();

    var sixteenDigitsFailingLuhn = run(targetThat("Reference 1234567812345678."), card);
    assertThat(sixteenDigitsFailingLuhn.result().passed()).isTrue();

    var plain = run(targetThat("The card 4111111111111111 was declined."), card);
    assertThat(plain.evalResult(Evaluators.ANSWER_LACKS_PAYMENT_CARD).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);

    var spaced = run(targetThat("The card 4111 1111 1111 1111 was declined."), card);
    assertThat(spaced.evalResult(Evaluators.ANSWER_LACKS_PAYMENT_CARD).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(spaced.evalResult(Evaluators.ANSWER_LACKS_PAYMENT_CARD).detail())
        .contains("4111 1111 1111 1111");

    var dashed = run(targetThat("The card 4111-1111-1111-1111 was declined."), card);
    assertThat(dashed.evalResult(Evaluators.ANSWER_LACKS_PAYMENT_CARD).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(dashed.evalResult(Evaluators.ANSWER_LACKS_PAYMENT_CARD).detail())
        .contains("4111-1111-1111-1111");
  }

  @Test
  void readsALuhnNumberOfAnotherLengthWhenGivenItsRange() {
    var imei = Evaluators.answerLacksLuhnNumber(15, 15);

    var leaking = run(targetThat("The handset is 490154203237518."), imei);
    assertThat(leaking.evalResult(Evaluators.ANSWER_LACKS_LUHN_NUMBER).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(leaking.evalResult(Evaluators.ANSWER_LACKS_LUHN_NUMBER).detail())
        .contains("490154203237518");

    var outOfRange = run(targetThat("The card 4111111111111111 was declined."), imei);
    assertThat(outOfRange.result().passed()).isTrue();

    assertThatThrownBy(() -> Evaluators.answerLacksLuhnNumber(1, 19))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.answerLacksLuhnNumber(19, 13))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aPredicateOverTheReplyReportsUnderTheNameItWasGiven() {
    var refuses = Evaluators.answerSatisfies("refusal", reply -> reply.contains("I can't"));

    var refused = run(targetThat("I can't help with that."), refuses);
    assertThat(refused.evalResult("refusal").verdict()).isEqualTo(EvalResult.Verdict.PASS);
    assertThat(refused.evalResult("refusal").evaluator()).isEqualTo("custom-eval:refusal");
    assertThat(refused.result().describe()).doesNotContain("akka-eval:refusal");

    var complied = run(targetThat("Sure, here is the list."), refuses);
    assertThat(complied.evalResult("refusal").verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(complied.evalResult("refusal").detail()).contains("does not satisfy the predicate");
  }

  @Test
  void aPredicateEvaluatorNeedsANameOfItsOwn() {
    assertThatThrownBy(() -> Evaluators.answerSatisfies(" ", reply -> true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.answerSatisfies("refusal", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aNameThatAlreadyCarriesANamespaceIsRefused() {
    assertThatThrownBy(() -> Evaluators.answerSatisfies(Evaluators.JUDGE, reply -> true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("judge");
    assertThatThrownBy(() -> Evaluators.answerSatisfies("custom-eval:refusal", reply -> true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("refusal");
  }

  @Test
  void aThrowingPredicateFailsItsOwnResult() {
    var run =
        run(
            targetThat("done"),
            Evaluators.answerSatisfies(
                "refusal",
                reply -> {
                  throw new IllegalStateException("no dictionary loaded");
                }));

    assertThat(run.evalResult("refusal").verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(run.evalResult("refusal").detail())
        .contains("IllegalStateException")
        .contains("no dictionary loaded");
  }

  @Test
  void aBudgetRefusesAValueThatCannotBeMet() {
    assertThatThrownBy(() -> Evaluators.modelCallsAtMost(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.tokensAtMost(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.latencyAtMost(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.toolCallsAtMost(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aFailedTurnKeepsTheToolCallsItsEvidenceSourceSaw() {
    var seen = call("getCustomer", "customerId", "cust_404");
    EvalTarget failing = turn -> EvalTarget.Outcome.failed("no customer cust_404", List.of(seen));

    var result = single(failing, EvalCase.of("c", "a question", Evaluators.tools("getCustomer")));

    assertThat(result.passed()).isFalse();
    assertThat(result.evalResults())
        .singleElement()
        .satisfies(evalResult -> assertThat(evalResult.evaluator()).isEqualTo(Evaluators.TARGET));
    assertThat(result.interaction().toolCalls()).containsExactly(seen);
    assertThat(result.describe()).contains("getCustomer{customerId=cust_404}");
  }

  @Test
  void recordedCallsAreLoadedIntoTheBoundStubsBeforeTheTurn() {
    var stub = new java.util.LinkedHashMap<String, String>();
    var order = new java.util.ArrayList<String>();
    var bindings =
        ToolBindings.builder()
            .bind(
                "getCustomer",
                call -> {
                  stub.put((String) call.argument("customerId"), call.resultAs(String.class));
                  order.add("load " + call.tool());
                })
            .build();
    var evalCase =
        new EvalCase(
            "c",
            "a question",
            List.of(new RecordedCall("getCustomer", Map.of("customerId", "cust_1"), "\"Ada\"")),
            List.of(Evaluators.answerContains("Ada")));
    EvalTarget target =
        turn -> {
          order.add("turn");
          return EvalTarget.Outcome.answered(
              Interaction.of(turn.userMessage(), "Hello " + stub.get("cust_1")));
        };

    var result =
        ExperimentRunner.against(new ExperimentRunner().cases(evalCase).bindings(bindings), target)
            .run()
            .results()
            .getFirst();

    assertThat(result.passed()).withFailMessage(result::describe).isTrue();
    assertThat(order).containsExactly("load getCustomer", "turn");
  }

  @Test
  void aRecordedToolWithoutABindingIsRefusedBeforeAnyCaseRuns() {
    var calls = new java.util.ArrayList<String>();
    EvalTarget target =
        turn -> {
          calls.add(turn.caseId());
          return EvalTarget.Outcome.answered(Interaction.of(turn.userMessage(), "done"));
        };
    var lookup =
        new EvalCase(
            "lookup",
            "who is cust_1?",
            List.of(new RecordedCall("getCustomer", Map.of(), "{}")),
            List.of());
    var tickets =
        new EvalCase(
            "tickets",
            "what is open?",
            List.of(
                new RecordedCall("getCustomer", Map.of(), "{}"),
                new RecordedCall("openTickets", Map.of(), "[]")),
            List.of());
    var cases = new ExperimentRunner().cases(lookup, tickets);

    assertThatThrownBy(() -> ExperimentRunner.against(cases, target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("getCustomer (cases lookup, tickets)")
        .hasMessageContaining("openTickets (case tickets)")
        .hasMessageContaining("bindings(ToolBindings)");
    var partial = cases.bindings(ToolBindings.builder().bind("getCustomer", call -> {}).build());
    assertThatThrownBy(() -> ExperimentRunner.against(partial, target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("openTickets (case tickets)")
        .hasMessageContaining("bound: [getCustomer]");
    assertThat(calls).isEmpty();
  }

  @Test
  void aLoaderThatThrowsFailsTheCaseUnderSetup() {
    var target = targetThat("never asked");
    var bindings =
        ToolBindings.builder().bind("getCustomer", call -> call.resultAs(Duration.class)).build();
    var evalCase =
        new EvalCase(
            "c",
            "a question",
            List.of(new RecordedCall("getCustomer", Map.of(), "{\"id\":\"cust_1\"}")),
            List.of(Evaluators.tools("getCustomer")));

    var result =
        ExperimentRunner.against(new ExperimentRunner().cases(evalCase).bindings(bindings), target)
            .run()
            .results()
            .getFirst();

    assertThat(result.evalResults())
        .singleElement()
        .satisfies(
            evalResult -> {
              assertThat(evalResult.evaluator()).isEqualTo(Evaluators.SETUP);
              assertThat(evalResult.detail()).contains("recorded result of getCustomer");
            });
  }

  @Test
  void aRunLevelEvaluatorRunsOnEveryCase() {
    Evaluator noApology =
        new Evaluator() {
          @Override
          public String name() {
            return "no-apology";
          }

          @Override
          public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
            return interaction.reply().contains("sorry")
                ? EvalResult.fail("the reply apologizes")
                : EvalResult.pass();
          }
        };

    EvalTarget target =
        turn ->
            EvalTarget.Outcome.answered(
                Interaction.of(
                    turn.userMessage(), turn.caseId().equals("apologetic") ? "sorry" : "sure"));

    var report =
        ExperimentRunner.against(
                new ExperimentRunner()
                    .cases(
                        EvalCase.of("polite", "a question"),
                        EvalCase.of("apologetic", "another question"))
                    .evaluator(noApology),
                target)
            .run();

    assertThat(report.passRate()).isEqualTo(0.5);
    assertThat(report.render()).contains("no-apology 1/2").contains("case apologetic FAILED");
  }

  @Test
  void aTargetsOwnToolEvidenceIsUsedWhenItSuppliesSome() {
    EvalTarget withEvidence =
        turn ->
            EvalTarget.Outcome.answered(
                new Interaction(
                    turn.userMessage(),
                    "done",
                    List.of(call("getCustomer", "customerId", "cust_1"))));

    var result =
        single(
            withEvidence,
            EvalCase.of(
                "c", "a question", Evaluators.toolArgument("getCustomer", "customerId", "cust_1")));

    assertThat(result.passed()).isTrue();
  }

  @Test
  void anExperimentNeedsAtLeastOneCase() {
    assertThatThrownBy(() -> new ExperimentRunner().cases(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one case");
  }

  @Test
  void aRunWithoutAGateFailsWhenACaseDoes() {
    var report =
        experiment(
                targetThat("no"),
                EvalCase.of("ok", "q", Evaluators.answerContains("no")),
                EvalCase.of("bad", "q", Evaluators.answerContains("yes")))
            .run();

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("gate: FAILED").contains("failed cases [bad]");
  }

  @Test
  void aFailedCaseDescribesTheEvidenceItWasJudgedOn() {
    var run =
        run(
            targetThat("I could not find them.", call("getCustomer", "customerId", "cust_9")),
            Evaluators.answerContains("Ada Lovelace"));

    assertThat(run.result().describe())
        .contains("case c FAILED")
        .contains("reply: I could not find them.")
        .contains("getCustomer{customerId=cust_9}")
        .contains("FAIL akka-eval:answer-contains");
  }

  @Test
  void aThrowingEvaluatorFailsItsOwnResultAndTheOthersStillReport() {
    Evaluator throwing =
        new Evaluator() {
          @Override
          public String name() {
            return "refund-within-total";
          }

          @Override
          public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
            throw new NullPointerException("amountCents is missing");
          }
        };

    var run = run(targetThat("done"), throwing, Evaluators.answerContains("done"));

    assertThat(run.result().passed()).isFalse();
    assertThat(run.evalResult("refund-within-total").evaluator())
        .isEqualTo("custom-eval:refund-within-total");
    assertThat(run.evalResult("refund-within-total").verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(run.evalResult("refund-within-total").detail())
        .contains("NullPointerException")
        .contains("amountCents is missing");
    assertThat(run.evalResult(Evaluators.ANSWER_CONTAINS).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);
  }

  @Test
  void anEvaluatorThatReturnsNothingFailsItsOwnResult() {
    Evaluator silent =
        new Evaluator() {
          @Override
          public String name() {
            return "silent";
          }

          @Override
          public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
            return null;
          }
        };

    var run = run(targetThat("done"), silent);

    assertThat(run.evalResult("silent").verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(run.evalResult("silent").detail()).contains("no result");
  }

  @Test
  void aRecordedNumberDoesNotMatchTheSameDigitsAsAString() {
    var asString =
        run(
            targetThat("done", call("issueRefund", "amountCents", "4999")),
            Evaluators.toolArgument("issueRefund", "amountCents", 4999));
    assertThat(asString.evalResult(Evaluators.TOOL_ARGUMENTS).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);

    var asDecimal =
        run(
            targetThat("done", call("issueRefund", "amountCents", 4999.0)),
            Evaluators.toolArgument("issueRefund", "amountCents", 4999));
    assertThat(asDecimal.evalResult(Evaluators.TOOL_ARGUMENTS).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);
  }

  @Test
  void aNullArgumentIsEvidenceAndMatchesOnlyNull() {
    var arguments = new java.util.HashMap<String, Object>();
    arguments.put("orderId", "o_9");
    arguments.put("note", null);
    var target = targetThat("done", new ToolCall("issueRefund", arguments));

    var isNull = run(target, Evaluators.toolArgument("issueRefund", "note", null));
    assertThat(isNull.evalResult(Evaluators.TOOL_ARGUMENTS).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var literal = run(target, Evaluators.toolArgument("issueRefund", "note", "null"));
    assertThat(literal.evalResult(Evaluators.TOOL_ARGUMENTS).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(literal.result().describe()).contains("note=null");
  }
}
