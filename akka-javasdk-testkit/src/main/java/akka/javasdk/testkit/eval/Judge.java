/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.util.List;
import java.util.Locale;

/**
 * A model that scores a reply against a criterion written in words. {@link #mustSatisfy} and {@link
 * #scoringAtLeast} turn the score into an {@link Evaluator}.
 *
 * <pre>{@code
 * var judge = Judge.agent(testKit);
 *
 * Expectations.expect()
 *     .tools("getCustomer")
 *     .satisfies(judge.mustSatisfy("the reply states the customer's tier and invents nothing"));
 * }</pre>
 *
 * <p>{@link #agent} asks a model through {@link JudgeAgent}, which uses the model provider of the
 * test configuration. A test can also return a {@link Verdict} directly, or mock that agent's
 * model.
 *
 * <p>A judged score can differ between runs of the same reply. Use a judge for criteria that have
 * no exact answer to compare against, and gate a batch on the rate instead of asserting per case.
 */
@FunctionalInterface
public interface Judge {

  Verdict assess(Question question);

  /** A judge that asks a model through {@link JudgeAgent}, using the TestKit component client. */
  static AgentJudge agent(akka.javasdk.testkit.TestKit testKit) {
    if (testKit == null) throw new IllegalArgumentException("testKit required");
    return AgentJudge.backedBy(testKit.getComponentClient());
  }

  /** What the judge is asked: the criterion and the evidence the case produced. */
  record Question(String criterion, String userMessage, String reply, List<ToolCall> toolCalls) {

    public Question {
      if (criterion == null || criterion.isBlank())
        throw new IllegalArgumentException("criterion required");
      userMessage = userMessage == null ? "" : userMessage;
      reply = reply == null ? "" : reply;
      toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /** The names of the tools called, in order. */
    public List<String> toolNames() {
      return toolCalls.stream().map(ToolCall::name).toList();
    }
  }

  /**
   * The judge's answer.
   *
   * @param score between 0 and 1. Any other value, {@code NaN} included, makes the evaluator
   *     abstain
   * @param reason one line, printed under a failed case
   */
  record Verdict(double score, String reason) {

    public Verdict {
      reason = reason == null ? "" : reason;
    }

    public static Verdict of(double score, String reason) {
      return new Verdict(score, reason);
    }
  }

  /**
   * The criterion must score at least this. Abstains when there is no reply, when the judge throws,
   * or when the score is not between 0 and 1.
   */
  default Evaluator scoringAtLeast(String criterion, double threshold) {
    return (evalCase, reply, toolCalls) -> {
      if (reply.text().isBlank()) {
        return EvalResult.abstain(Evaluators.JUDGE, criterion + ": there is no reply to judge");
      }
      Verdict verdict;
      try {
        verdict = assess(new Question(criterion, evalCase.userMessage(), reply.text(), toolCalls));
      } catch (RuntimeException e) {
        return EvalResult.abstain(
            Evaluators.JUDGE, criterion + ": the judge failed: " + e.getMessage());
      }
      var score = verdict.score();
      if (Double.isNaN(score) || score < 0 || score > 1) {
        return EvalResult.abstain(
            Evaluators.JUDGE, criterion + ": the judge scored " + score + ", which is not a share");
      }
      var detail =
          String.format(
              Locale.ROOT,
              "%s: scored %.2f, needed %.2f%s",
              criterion,
              score,
              threshold,
              verdict.reason().isEmpty() ? "" : " — " + verdict.reason());
      return score >= threshold
          ? new EvalResult(Evaluators.JUDGE, EvalResult.Verdict.PASS, detail)
          : EvalResult.fail(Evaluators.JUDGE, detail);
    };
  }

  /** The criterion must score at least 0.5. */
  default Evaluator mustSatisfy(String criterion) {
    return scoringAtLeast(criterion, 0.5);
  }
}
