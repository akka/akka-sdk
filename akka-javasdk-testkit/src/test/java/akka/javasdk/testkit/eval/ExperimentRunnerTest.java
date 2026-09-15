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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The runner over a scripted target that needs no runtime. */
class ExperimentRunnerTest {

  /** Answers with the given text and tool calls. */
  private static EvalTarget<String> targetThat(String answer, ToolCall... calls) {
    return turn ->
        EvalTarget.Outcome.answered(new Interaction(turn.command(), answer, List.of(calls)));
  }

  private static ToolCall call(String name, String argument, Object value) {
    return new ToolCall(name, Map.of(argument, value));
  }

  private CaseResultOf run(EvalTarget<String> target, Evaluator... evaluators) {
    var evalCase = EvalCase.of("c", "a question", evaluators);
    return new CaseResultOf(single(target, evalCase));
  }

  /** The cases against a scripted target, ready to run. */
  @SafeVarargs
  private static Experiment experiment(EvalTarget<String> target, EvalCase<String>... cases) {
    return ExperimentRunner.against(new ExperimentRunner().cases(List.of(cases)), target);
  }

  /** Runs one case and reads its result out of the report. */
  private static ExperimentRunner.CaseResult single(
      EvalTarget<String> target, EvalCase<String> evalCase) {
    return experiment(target, evalCase).run().results().getFirst();
  }

  /** Reads one evaluator's result out of a case result. */
  private record CaseResultOf(ExperimentRunner.CaseResult result) {
    EvalResult evalResult(Class<? extends Evaluator> evaluator) {
      var label = Evaluators.label(evaluator);
      return result.evalResults().stream()
          .filter(f -> f.evaluator().equals(label))
          .findFirst()
          .orElseThrow(() -> new AssertionError(label + " did not report"));
    }
  }

  @EvalLabel("no-apology")
  private record NoApology() implements Evaluator {
    @Override
    public EvalResult evaluate(EvalCase<?> evalCase, Interaction interaction) {
      return interaction.reply().contains("sorry")
          ? EvalResult.fail("the reply apologizes")
          : EvalResult.pass();
    }
  }

  @EvalLabel("refund-within-total")
  private record Throwing() implements Evaluator {
    @Override
    public EvalResult evaluate(EvalCase<?> evalCase, Interaction interaction) {
      throw new NullPointerException("amountCents is missing");
    }
  }

  @EvalLabel("silent")
  private record Silent() implements Evaluator {
    @Override
    public EvalResult evaluate(EvalCase<?> evalCase, Interaction interaction) {
      return null;
    }
  }

  @EvalLabel(" ")
  private record BlankLabel() implements Evaluator {
    @Override
    public EvalResult evaluate(EvalCase<?> evalCase, Interaction interaction) {
      return EvalResult.pass();
    }
  }

  @EvalLabel("custom-eval:refusal")
  private record PrefixedLabel() implements Evaluator {
    @Override
    public EvalResult evaluate(EvalCase<?> evalCase, Interaction interaction) {
      return EvalResult.pass();
    }
  }

  @Test
  void holdsTheReplyAndTheToolCallsAgainstTheExpectations() {
    var target =
        targetThat("Ada Lovelace is a gold customer.", call("getCustomer", "customerId", "cust_1"));

    var run =
        run(
            target,
            Evaluators.shouldCallTool("getCustomer"),
            Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_1"),
            Evaluators.shouldNotCallTool("openTickets"),
            Evaluators.replyShouldContain("ada lovelace"),
            Evaluators.replyShouldMatch("gold"));

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
            Evaluators.shouldCallTool("getCustomer"),
            Evaluators.shouldCallToolsInOrder("getCustomer"),
            Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_1"));

    assertThat(run.evalResult(Evaluators.Tools.class).verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(run.evalResult(Evaluators.Tools.class).detail()).contains("no tools");
    assertThat(run.evalResult(Evaluators.ToolOrder.class).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
    assertThat(run.evalResult(Evaluators.ToolArgument.class).verdict())
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
            Evaluators.shouldCallToolsInOrder("getCustomer", "openTickets"));
    assertThat(inOrder.evalResult(Evaluators.ToolOrder.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var reversed =
        run(
            targetThat("done", ToolCall.of("openTickets"), ToolCall.of("getCustomer")),
            Evaluators.shouldCallToolsInOrder("getCustomer", "openTickets"));
    assertThat(reversed.evalResult(Evaluators.ToolOrder.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(reversed.evalResult(Evaluators.ToolOrder.class).detail())
        .contains("[openTickets, getCustomer]");
  }

  @Test
  void aWrongArgumentNamesWhatArrived() {
    var run =
        run(
            targetThat("done", call("getCustomer", "customerId", "cust_2")),
            Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_1"));

    assertThat(run.evalResult(Evaluators.ToolArgument.class).detail())
        .contains("expected cust_1")
        .contains("was [cust_2]");
  }

  @Test
  void aRecordedNumberComparesAgainstTheValueTheToolReceived() {
    var run =
        run(
            targetThat("done", call("charge", "amount", 12L)),
            Evaluators.shouldCallToolWith("charge", "amount", 12));

    assertThat(run.evalResult(Evaluators.ToolArgument.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);
  }

  @Test
  void aForbiddenToolFailsTheCaseEvenWhenTheAnswerIsRight() {
    var run =
        run(
            targetThat("Ada Lovelace", ToolCall.of("openTickets")),
            Evaluators.replyShouldContain("Ada"),
            Evaluators.shouldNotCallTool("openTickets"));

    assertThat(run.result().passed()).isFalse();
    assertThat(run.evalResult(Evaluators.ForbiddenTools.class).detail()).contains("openTickets");
  }

  @Test
  void aThrownTargetIsAFailedCaseNotAWrongAnswer() {
    EvalTarget<String> throwing =
        turn -> {
          throw new IllegalStateException("model unavailable");
        };

    var result =
        single(throwing, EvalCase.of("c", "a question", Evaluators.replyShouldContain("x")));

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
            Evaluators.toolResultShouldContain("getCustomer", "Ada Lovelace"));

    assertThat(run.result().passed()).isTrue();
    assertThat(run.evalResult(Evaluators.ToolResult.class).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
    assertThat(run.evalResult(Evaluators.ToolResult.class).detail()).contains("no recorded result");
  }

  /** A target whose evidence carries model calls, tokens and timing. */
  private static EvalTarget<String> tracedThat(
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
            new Interaction(turn.command(), "", answer, List.of(), calls, List.of(), took, answer));
  }

  @Test
  void aToolCallBudgetCountsEveryCall() {
    var twoCalls = targetThat("done", ToolCall.of("getCustomer"), ToolCall.of("getCustomer"));

    var within = run(twoCalls, Evaluators.shouldMakeAtMostToolCalls(2));
    assertThat(within.evalResult(Evaluators.ToolCallBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(twoCalls, Evaluators.shouldMakeAtMostToolCalls(1));
    assertThat(over.evalResult(Evaluators.ToolCallBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.evalResult(Evaluators.ToolCallBudget.class).detail())
        .contains("made 2 tool calls, allowed 1");
  }

  @Test
  void aModelCallBudgetReadsTheTracedCallsAndIsInconclusiveWithoutThem() {
    var threeCalls = tracedThat("done", 3, 100, Duration.ofMillis(40));

    var within = run(threeCalls, Evaluators.shouldMakeAtMostModelCalls(3));
    assertThat(within.evalResult(Evaluators.ModelCallBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(threeCalls, Evaluators.shouldMakeAtMostModelCalls(2));
    assertThat(over.evalResult(Evaluators.ModelCallBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.evalResult(Evaluators.ModelCallBudget.class).detail())
        .contains("made 3 model calls, allowed 2");

    var untraced = run(targetThat("done"), Evaluators.shouldMakeAtMostModelCalls(1));
    assertThat(untraced.evalResult(Evaluators.ModelCallBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
    assertThat(untraced.result().passed()).isTrue();
  }

  @Test
  void aTokenBudgetSumsInputAndOutputAndIsInconclusiveWhenNoneWereReported() {
    var threeHundred = tracedThat("done", 3, 100, Duration.ofMillis(40));

    var within = run(threeHundred, Evaluators.shouldUseAtMostTokens(300));
    assertThat(within.evalResult(Evaluators.TokenBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(threeHundred, Evaluators.shouldUseAtMostTokens(299));
    assertThat(over.evalResult(Evaluators.TokenBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.evalResult(Evaluators.TokenBudget.class).detail()).contains("used 300 tokens");

    var unreported =
        run(tracedThat("done", 2, 0, Duration.ofMillis(40)), Evaluators.shouldUseAtMostTokens(10));
    assertThat(unreported.evalResult(Evaluators.TokenBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
  }

  @Test
  void aLatencyBudgetReadsTheCommandsDurationAndIsInconclusiveWithoutTiming() {
    var forty = tracedThat("done", 1, 10, Duration.ofMillis(40));

    var within = run(forty, Evaluators.shouldReplyWithin(Duration.ofMillis(40)));
    assertThat(within.evalResult(Evaluators.LatencyBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var over = run(forty, Evaluators.shouldReplyWithin(Duration.ofMillis(39)));
    assertThat(over.evalResult(Evaluators.LatencyBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(over.evalResult(Evaluators.LatencyBudget.class).detail())
        .contains("took 40 ms, allowed 39 ms");

    var untimed = run(targetThat("done"), Evaluators.shouldReplyWithin(Duration.ofSeconds(1)));
    assertThat(untimed.evalResult(Evaluators.LatencyBudget.class).verdict())
        .isEqualTo(EvalResult.Verdict.INCONCLUSIVE);
  }

  @Test
  void theReportSumsWhatTheRunSpentOverTheCasesWithEvidence() {
    EvalTarget<String> target =
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
    EvalTarget<String> mapped =
        turn ->
            EvalTarget.Outcome.answered(
                new Interaction(
                    turn.command(),
                    "",
                    "{\"tier\":\"gold\"}",
                    List.of(),
                    List.of(),
                    List.of(),
                    Duration.ofMillis(5),
                    "```json\n{\"tier\":\"gold\"}\n```"));

    var result = single(mapped, EvalCase.of("c", "q", Evaluators.replyShouldContain("silver")));

    assertThat(result.describe())
        .contains("reply: {\"tier\":\"gold\"}")
        .contains("model text: ```json {\"tier\":\"gold\"} ```");

    var same = run(targetThat("plain"), Evaluators.replyShouldContain("other"));
    assertThat(same.result().describe()).doesNotContain("model text:");
  }

  private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

  @Test
  void holdsTheReplyAgainstWhatItMustNotCarry() {
    var clean =
        run(
            targetThat("I cannot share my configuration."),
            Evaluators.replyShouldNotContain("SECRET-MARKER", "Never guess"),
            Evaluators.replyShouldNotMatch(SSN));
    assertThat(clean.result().passed()).isTrue();

    var leaking =
        run(
            targetThat("As instructed: secret-marker. Never guess a tier. SSN 123-45-6789."),
            Evaluators.replyShouldNotContain("SECRET-MARKER", "Never guess"),
            Evaluators.replyShouldNotMatch(SSN));
    assertThat(leaking.evalResult(Evaluators.ReplyLacks.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(leaking.evalResult(Evaluators.ReplyLacks.class).detail())
        .contains("[SECRET-MARKER, Never guess]");
    assertThat(leaking.evalResult(Evaluators.ReplyDoesNotMatch.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(leaking.evalResult(Evaluators.ReplyDoesNotMatch.class).detail())
        .contains("123-45-6789");

    assertThatThrownBy(() -> Evaluators.replyShouldNotContain())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.replyShouldNotContain("ok", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsAPaymentCardNumberOutOfTheReply() {
    var card = Evaluators.replyShouldNotContainPaymentCard();

    var empty = run(targetThat(""), card);
    assertThat(empty.result().passed()).isTrue();

    var orderNumber = run(targetThat("Order 1234567890123 shipped on Tuesday."), card);
    assertThat(orderNumber.result().passed()).isTrue();

    var sixteenDigitsFailingLuhn = run(targetThat("Reference 1234567812345678."), card);
    assertThat(sixteenDigitsFailingLuhn.result().passed()).isTrue();

    var plain = run(targetThat("The card 4111111111111111 was declined."), card);
    assertThat(plain.evalResult(Evaluators.ReplyLacksPaymentCard.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);

    var spaced = run(targetThat("The card 4111 1111 1111 1111 was declined."), card);
    assertThat(spaced.evalResult(Evaluators.ReplyLacksPaymentCard.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(spaced.evalResult(Evaluators.ReplyLacksPaymentCard.class).detail())
        .contains("4111 1111 1111 1111");

    var dashed = run(targetThat("The card 4111-1111-1111-1111 was declined."), card);
    assertThat(dashed.evalResult(Evaluators.ReplyLacksPaymentCard.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(dashed.evalResult(Evaluators.ReplyLacksPaymentCard.class).detail())
        .contains("4111-1111-1111-1111");
  }

  @Test
  void readsALuhnNumberOfAnotherLengthWhenGivenItsRange() {
    var imei = Evaluators.replyShouldNotContainLuhnNumber(15, 15);

    var leaking = run(targetThat("The handset is 490154203237518."), imei);
    assertThat(leaking.evalResult(Evaluators.ReplyLacksLuhnNumber.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(leaking.evalResult(Evaluators.ReplyLacksLuhnNumber.class).detail())
        .contains("490154203237518");

    var outOfRange = run(targetThat("The card 4111111111111111 was declined."), imei);
    assertThat(outOfRange.result().passed()).isTrue();

    assertThatThrownBy(() -> Evaluators.replyShouldNotContainLuhnNumber(1, 19))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.replyShouldNotContainLuhnNumber(19, 13))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBudgetRefusesAValueThatCannotBeMet() {
    assertThatThrownBy(() -> Evaluators.shouldMakeAtMostModelCalls(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.shouldUseAtMostTokens(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.shouldReplyWithin(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Evaluators.shouldMakeAtMostToolCalls(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aFailedTurnKeepsTheToolCallsItsEvidenceSourceSaw() {
    var seen = call("getCustomer", "customerId", "cust_404");
    EvalTarget<String> failing =
        turn -> EvalTarget.Outcome.failed("no customer cust_404", List.of(seen));

    var result =
        single(failing, EvalCase.of("c", "a question", Evaluators.shouldCallTool("getCustomer")));

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
        new EvalCase<>(
            "c",
            "a question",
            List.of(new RecordedCall("getCustomer", Map.of("customerId", "cust_1"), "\"Ada\"")),
            List.of(Evaluators.replyShouldContain("Ada")));
    EvalTarget<String> target =
        turn -> {
          order.add("turn");
          return EvalTarget.Outcome.answered(
              Interaction.of(turn.command(), "Hello " + stub.get("cust_1")));
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
    EvalTarget<String> target =
        turn -> {
          calls.add(turn.caseId());
          return EvalTarget.Outcome.answered(Interaction.of(turn.command(), "done"));
        };
    var lookup =
        new EvalCase<>(
            "lookup",
            "who is cust_1?",
            List.of(new RecordedCall("getCustomer", Map.of(), "{}")),
            List.of());
    var tickets =
        new EvalCase<>(
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
        new EvalCase<>(
            "c",
            "a question",
            List.of(new RecordedCall("getCustomer", Map.of(), "{\"id\":\"cust_1\"}")),
            List.of(Evaluators.shouldCallTool("getCustomer")));

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
    EvalTarget<String> target =
        turn ->
            EvalTarget.Outcome.answered(
                Interaction.of(
                    turn.command(), turn.caseId().equals("apologetic") ? "sorry" : "sure"));

    var report =
        ExperimentRunner.against(
                new ExperimentRunner()
                    .cases(
                        EvalCase.of("polite", "a question"),
                        EvalCase.of("apologetic", "another question"))
                    .evaluator(new NoApology()),
                target)
            .run();

    assertThat(report.passRate()).isEqualTo(0.5);
    assertThat(report.render())
        .contains("custom-eval:no-apology 1/2")
        .contains("case apologetic FAILED");
  }

  @Test
  void anEvaluatorWithoutALabelIsRefusedWhenTheCaseIsBuilt() {
    Evaluator anonymous =
        new Evaluator() {
          @Override
          public EvalResult evaluate(EvalCase<?> evalCase, Interaction interaction) {
            return EvalResult.pass();
          }
        };

    assertThatThrownBy(() -> EvalCase.of("c", "a question", anonymous))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("@EvalLabel");
    assertThatThrownBy(
            () -> new ExperimentRunner().cases(EvalCase.of("c", "a question")).evaluator(anonymous))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("@EvalLabel");
  }

  @Test
  void aBlankOrPrefixedLabelIsRefusedWhenTheCaseIsBuilt() {
    assertThatThrownBy(() -> EvalCase.of("c", "a question", new BlankLabel()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank @EvalLabel");
    assertThatThrownBy(() -> EvalCase.of("c", "a question", new PrefixedLabel()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("This prefix is reserved");
    assertThatThrownBy(() -> Gate.passRateShouldBeAtLeast(PrefixedLabel.class, 1.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("This prefix is reserved");
  }

  @Test
  void aTargetsOwnToolEvidenceIsUsedWhenItSuppliesSome() {
    EvalTarget<String> withEvidence =
        turn ->
            EvalTarget.Outcome.answered(
                new Interaction(
                    turn.command(), "done", List.of(call("getCustomer", "customerId", "cust_1"))));

    var result =
        single(
            withEvidence,
            EvalCase.of(
                "c",
                "a question",
                Evaluators.shouldCallToolWith("getCustomer", "customerId", "cust_1")));

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
                EvalCase.of("ok", "q", Evaluators.replyShouldContain("no")),
                EvalCase.of("bad", "q", Evaluators.replyShouldContain("yes")))
            .run();

    assertThat(report.passed()).isFalse();
    assertThat(report.render()).contains("gate: FAILED").contains("failed cases [bad]");
  }

  @Test
  void aFailedCaseDescribesTheEvidenceItWasJudgedOn() {
    var run =
        run(
            targetThat("I could not find them.", call("getCustomer", "customerId", "cust_9")),
            Evaluators.replyShouldContain("Ada Lovelace"));

    assertThat(run.result().describe())
        .contains("case c FAILED")
        .contains("reply: I could not find them.")
        .contains("getCustomer{customerId=cust_9}")
        .contains("FAIL reply-contains");
  }

  @Test
  void aThrowingEvaluatorFailsItsOwnResultAndTheOthersStillReport() {
    var run = run(targetThat("done"), new Throwing(), Evaluators.replyShouldContain("done"));

    assertThat(run.result().passed()).isFalse();
    assertThat(run.evalResult(Throwing.class).evaluator())
        .isEqualTo("custom-eval:refund-within-total");
    assertThat(run.evalResult(Throwing.class).verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(run.evalResult(Throwing.class).detail())
        .contains("NullPointerException")
        .contains("amountCents is missing");
    assertThat(run.evalResult(Evaluators.ReplyContains.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);
  }

  @Test
  void anEvaluatorThatReturnsNothingFailsItsOwnResult() {
    var run = run(targetThat("done"), new Silent());

    assertThat(run.evalResult(Silent.class).verdict()).isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(run.evalResult(Silent.class).detail()).contains("no result");
  }

  @Test
  void aRecordedNumberDoesNotMatchTheSameDigitsAsAString() {
    var asString =
        run(
            targetThat("done", call("issueRefund", "amountCents", "4999")),
            Evaluators.shouldCallToolWith("issueRefund", "amountCents", 4999));
    assertThat(asString.evalResult(Evaluators.ToolArgument.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);

    var asDecimal =
        run(
            targetThat("done", call("issueRefund", "amountCents", 4999.0)),
            Evaluators.shouldCallToolWith("issueRefund", "amountCents", 4999));
    assertThat(asDecimal.evalResult(Evaluators.ToolArgument.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);
  }

  @Test
  void aNullArgumentIsEvidenceAndMatchesOnlyNull() {
    var arguments = new java.util.HashMap<String, Object>();
    arguments.put("orderId", "o_9");
    arguments.put("note", null);
    var target = targetThat("done", new ToolCall("issueRefund", arguments));

    var isNull = run(target, Evaluators.shouldCallToolWith("issueRefund", "note", null));
    assertThat(isNull.evalResult(Evaluators.ToolArgument.class).verdict())
        .isEqualTo(EvalResult.Verdict.PASS);

    var literal = run(target, Evaluators.shouldCallToolWith("issueRefund", "note", "null"));
    assertThat(literal.evalResult(Evaluators.ToolArgument.class).verdict())
        .isEqualTo(EvalResult.Verdict.FAIL);
    assertThat(literal.result().describe()).contains("note=null");
  }

  record Ask(String customerId, String question) {}

  @Test
  void sendsTheCommandTypeOfTheCaseToTheAgent() {
    var asked = new java.util.ArrayList<Ask>();
    EvalTarget<Ask> target =
        turn -> {
          asked.add(turn.command());
          return EvalTarget.Outcome.answered(Interaction.of(turn.commandText(), "o_42 is shipped"));
        };

    var evalCase =
        EvalCase.of(
            "c", new Ask("cust_1", "Where is o_42?"), Evaluators.replyShouldContain("shipped"));
    var result =
        ExperimentRunner.against(new ExperimentRunner().cases(evalCase), target)
            .run()
            .results()
            .getFirst();

    assertThat(asked).containsExactly(new Ask("cust_1", "Where is o_42?"));
    assertThat(result.passed()).isTrue();
    // A judge and the report read a command that is not a String as its JSON.
    assertThat(result.interaction().input())
        .isEqualTo("{\"customerId\":\"cust_1\",\"question\":\"Where is o_42?\"}");
  }

  @Test
  void aCaseWithoutACommandIsRejected() {
    assertThatThrownBy(() -> EvalCase.of("c", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command required");

    assertThatThrownBy(() -> EvalCase.of("c", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("command required");
  }
}
