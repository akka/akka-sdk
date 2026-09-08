/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.Locale;

/**
 * The evaluator behind {@link Judge#scoringAtLeast}: asks the judge and holds the score to the
 * threshold.
 */
record JudgeEvaluator(Judge judge, String criterion, double threshold) implements Evaluator {

  @Override
  public String name() {
    return Evaluators.JUDGE;
  }

  @Override
  public EvalResult evaluate(EvalCase evalCase, Interaction interaction) {
    if (interaction.reply().isBlank()) {
      return EvalResult.abstain(criterion + ": there is no reply to judge");
    }
    Judge.Verdict verdict;
    try {
      verdict =
          judge.assess(
              new Judge.Input(
                  criterion, evalCase.userMessage(), interaction.reply(), interaction.toolCalls()));
    } catch (RuntimeException e) {
      return EvalResult.abstain(criterion + ": the judge failed: " + e.getMessage());
    }
    if (verdict == null) {
      return EvalResult.abstain(criterion + ": the judge gave no verdict");
    }
    var score = verdict.score();
    if (Double.isNaN(score) || score < 0 || score > 1) {
      return EvalResult.abstain(
          criterion + ": the judge scored " + score + ", which is not a share");
    }
    var detail =
        String.format(
            Locale.ROOT,
            "%s: scored %.2f, needed %.2f%s",
            criterion,
            score,
            threshold,
            verdict.reason().isEmpty() ? "" : " — " + verdict.reason());
    return score >= threshold ? EvalResult.pass(detail) : EvalResult.fail(detail);
  }
}
