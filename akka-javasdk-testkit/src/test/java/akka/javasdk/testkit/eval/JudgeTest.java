/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.ToolCall;
import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgeTest {

  private static final String CRITERION = "the reply explains why the fee was charged";

  private ExperimentRunner.CaseResult judged(Judge judge, String reply, Evaluator... evaluators) {
    EvalTarget target =
        turn ->
            EvalTarget.Outcome.answered(
                new Interaction(
                    reply, List.of(new ToolCall("getLoan", Map.of("loanId", "loan_5001")))));
    return single(target, EvalCase.of("c", "Why was I charged a late fee?", evaluators));
  }

  /** Runs one case against a scripted target and reads its result out of the report. */
  private static ExperimentRunner.CaseResult single(EvalTarget target, EvalCase evalCase) {
    return ExperimentRunner.against(new ExperimentRunner().cases(evalCase), target)
        .run()
        .results()
        .getFirst();
  }

  private EvalResult resultOf(ExperimentRunner.CaseResult result) {
    return result.evalResults().stream()
        .filter(evalResult -> evalResult.evaluator().equals(Evaluators.JUDGE))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aScoreOverTheThresholdPassesAndCarriesTheJudgesReason() {
    Judge judge = input -> Judge.Verdict.of(0.8, "it names the overdue payment");

    var result =
        judged(
            judge,
            "The fee was charged because the payment was 12 days overdue.",
            judge.scoringAtLeast(CRITERION, 0.7));

    assertThat(result.passed()).isTrue();
    assertThat(resultOf(result).detail())
        .isEqualTo(CRITERION + ": scored 0.80, needed 0.70 — it names the overdue payment");
  }

  @Test
  void aScoreUnderTheThresholdFailsTheCase() {
    Judge judge = input -> Judge.Verdict.of(0.3, "it states the fee without a reason");

    var result = judged(judge, "You were charged 12.50.", judge.mustSatisfy(CRITERION));

    assertThat(result.passed()).isFalse();
    assertThat(resultOf(result).detail()).contains("scored 0.30, needed 0.50");
  }

  @Test
  void theJudgeIsAskedAboutTheCaseAndWhatItCalled() {
    var asked = new Judge.Input[1];
    Judge judge =
        input -> {
          asked[0] = input;
          return Judge.Verdict.of(1, "");
        };

    judged(judge, "an answer", judge.mustSatisfy(CRITERION));

    assertThat(asked[0].criterion()).isEqualTo(CRITERION);
    assertThat(asked[0].userMessage()).isEqualTo("Why was I charged a late fee?");
    assertThat(asked[0].reply()).isEqualTo("an answer");
    assertThat(asked[0].toolNames()).containsExactly("getLoan");
  }

  @Test
  void aScoreOffTheScaleAbstainsRatherThanFailingTheCase() {
    Judge judge = input -> Judge.Verdict.of(7, "seven out of ten");

    var result = judged(judge, "an answer", judge.mustSatisfy(CRITERION));

    assertThat(resultOf(result).verdict()).isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(result.passed()).isTrue();
  }

  @Test
  void aJudgeThatThrowsAbstainsAndSaysSo() {
    Judge judge =
        input -> {
          throw new IllegalStateException("the judge's provider is not configured");
        };

    var result = judged(judge, "an answer", judge.mustSatisfy(CRITERION));

    assertThat(resultOf(result).verdict()).isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(resultOf(result).detail()).contains("provider is not configured");
  }

  @Test
  void aRunWithNoReplyIsNotSentToTheJudge() {
    Judge judge =
        input -> {
          throw new AssertionError("the judge was asked about an empty reply");
        };

    var result =
        single(
            turn -> EvalTarget.Outcome.answered(Interaction.of("")),
            EvalCase.of("c", "a question", judge.mustSatisfy(CRITERION)));

    assertThat(resultOf(result).verdict()).isEqualTo(EvalResult.Verdict.ABSTAIN);
  }

  @Test
  void aBatchIsGatedOnTheRateTheJudgePassed() {
    Judge judge = input -> Judge.Verdict.of(input.reply().contains("because") ? 0.9 : 0.2, "");
    var cases =
        List.of(
            EvalCase.of("explained", "why?", judge.mustSatisfy(CRITERION)),
            EvalCase.of("bare", "why?", judge.mustSatisfy(CRITERION)));

    EvalTarget explaining =
        turn ->
            EvalTarget.Outcome.answered(
                Interaction.of(
                    turn.caseId().equals("explained") ? "because it was overdue" : "12.50"));
    var report =
        ExperimentRunner.against(new ExperimentRunner().cases(cases), explaining)
            .gate(Gate.evaluatorRateAtLeast(Evaluators.JUDGE, 0.5))
            .run();

    assertThat(report.passed()).isTrue();
    assertThat(report.render()).contains("judge 1/2");
    EvalTarget bare = turn -> EvalTarget.Outcome.answered(Interaction.of("12.50"));
    assertThat(
            ExperimentRunner.against(new ExperimentRunner().cases(cases), bare)
                .gate(Gate.evaluatorRateAtLeast(Evaluators.JUDGE, 0.5))
                .run()
                .passed())
        .isFalse();
  }

  @Test
  void aJudgeThatGivesNoVerdictAbstains() {
    Judge judge = input -> null;

    var result = judged(judge, "an answer", judge.mustSatisfy(CRITERION));

    assertThat(resultOf(result).verdict()).isEqualTo(EvalResult.Verdict.ABSTAIN);
    assertThat(resultOf(result).detail()).contains("no verdict");
    assertThat(result.passed()).isTrue();
  }
}
