/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgeTest {

  private static final String CRITERION = "the reply explains why the fee was charged";

  private ExperimentRunner.CaseResult judged(Judge judge, String reply, Expectations expectations) {
    EvalTarget target =
        turn ->
            EvalTarget.Outcome.answered(
                new Interaction(
                    reply, List.of(new ToolCall("getLoan", Map.of("loanId", "loan_5001")))));
    return ExperimentRunner.forTarget(target)
        .runSingle(EvalCase.of("c", "Why was I charged a late fee?", expectations));
  }

  private EvalResult findingOf(ExperimentRunner.CaseResult result) {
    return result.evalResults().stream()
        .filter(finding -> finding.evaluator().equals(Evaluators.JUDGE))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aScoreOverTheThresholdPassesAndCarriesTheJudgesReason() {
    Judge judge = question -> Judge.Verdict.of(0.8, "it names the overdue payment");

    var result =
        judged(
            judge,
            "The fee was charged because the payment was 12 days overdue.",
            Expectations.expect().satisfies(judge.scoringAtLeast(CRITERION, 0.7)));

    assertThat(result.passed()).isTrue();
    assertThat(findingOf(result).detail())
        .isEqualTo(CRITERION + ": scored 0.80, needed 0.70 — it names the overdue payment");
  }

  @Test
  void aScoreUnderTheThresholdFailsTheCase() {
    Judge judge = question -> Judge.Verdict.of(0.3, "it states the fee without a reason");

    var result =
        judged(
            judge,
            "You were charged 12.50.",
            Expectations.expect().satisfies(judge.mustSatisfy(CRITERION)));

    assertThat(result.passed()).isFalse();
    assertThat(findingOf(result).detail()).contains("scored 0.30, needed 0.50");
  }

  @Test
  void theJudgeIsAskedAboutTheCaseAndWhatItCalled() {
    var asked = new Judge.Question[1];
    Judge judge =
        question -> {
          asked[0] = question;
          return Judge.Verdict.of(1, "");
        };

    judged(judge, "an answer", Expectations.expect().satisfies(judge.mustSatisfy(CRITERION)));

    assertThat(asked[0].criterion()).isEqualTo(CRITERION);
    assertThat(asked[0].userMessage()).isEqualTo("Why was I charged a late fee?");
    assertThat(asked[0].reply()).isEqualTo("an answer");
    assertThat(asked[0].toolNames()).containsExactly("getLoan");
  }

  @Test
  void aScoreOffTheScaleAbstainsRatherThanFailingTheCase() {
    Judge judge = question -> Judge.Verdict.of(7, "seven out of ten");

    var result =
        judged(judge, "an answer", Expectations.expect().satisfies(judge.mustSatisfy(CRITERION)));

    assertThat(findingOf(result).verdict()).isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(result.passed()).isTrue();
  }

  @Test
  void aJudgeThatThrowsAbstainsAndSaysSo() {
    Judge judge =
        question -> {
          throw new IllegalStateException("the judge's provider is not configured");
        };

    var result =
        judged(judge, "an answer", Expectations.expect().satisfies(judge.mustSatisfy(CRITERION)));

    assertThat(findingOf(result).verdict()).isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(findingOf(result).detail()).contains("provider is not configured");
  }

  @Test
  void aRunWithNoReplyIsNotSentToTheJudge() {
    Judge judge =
        question -> {
          throw new AssertionError("the judge was asked about an empty reply");
        };

    var result =
        ExperimentRunner.forTarget(turn -> EvalTarget.Outcome.answered(Interaction.of("")))
            .runSingle(
                EvalCase.of(
                    "c",
                    "a question",
                    Expectations.expect().satisfies(judge.mustSatisfy(CRITERION))));

    assertThat(findingOf(result).verdict()).isEqualTo(EvalResult.Verdict.ABSTAIN);
  }

  @Test
  void aBatchIsGatedOnTheRateTheJudgePassed() {
    Judge judge =
        question -> Judge.Verdict.of(question.reply().contains("because") ? 0.9 : 0.2, "");
    var cases =
        List.of(
            EvalCase.of(
                "explained", "why?", Expectations.expect().satisfies(judge.mustSatisfy(CRITERION))),
            EvalCase.of(
                "bare", "why?", Expectations.expect().satisfies(judge.mustSatisfy(CRITERION))));

    var report =
        ExperimentRunner.cases(cases)
            .target(
                turn ->
                    EvalTarget.Outcome.answered(
                        Interaction.of(
                            turn.caseId().equals("explained")
                                ? "because it was overdue"
                                : "12.50")))
            .gate(Gate.evaluatorRateAtLeast(Evaluators.JUDGE, 0.5))
            .run();

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("judge 1/2");
    assertThat(
            ExperimentRunner.cases(cases)
                .target(turn -> EvalTarget.Outcome.answered(Interaction.of("12.50")))
                .gate(Gate.evaluatorRateAtLeast(Evaluators.JUDGE, 0.5))
                .run()
                .passed())
        .isFalse();
  }
}
