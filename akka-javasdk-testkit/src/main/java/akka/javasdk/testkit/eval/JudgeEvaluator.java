/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.ToolCall;
import java.util.List;
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
  public Finding evaluate(EvalCase evalCase, Interaction reply, List<ToolCall> toolCalls) {
    if (reply.text().isBlank()) {
      return Finding.abstain(criterion + ": there is no reply to judge");
    }
    Judge.Verdict verdict;
    try {
      verdict =
          judge.assess(
              new Judge.Question(criterion, evalCase.userMessage(), reply.text(), toolCalls));
    } catch (RuntimeException e) {
      return Finding.abstain(criterion + ": the judge failed: " + e.getMessage());
    }
    var score = verdict.score();
    if (Double.isNaN(score) || score < 0 || score > 1) {
      return Finding.abstain(criterion + ": the judge scored " + score + ", which is not a share");
    }
    var detail =
        String.format(
            Locale.ROOT,
            "%s: scored %.2f, needed %.2f%s",
            criterion,
            score,
            threshold,
            verdict.reason().isEmpty() ? "" : " — " + verdict.reason());
    return score >= threshold ? Finding.pass(detail) : Finding.fail(detail);
  }
}
