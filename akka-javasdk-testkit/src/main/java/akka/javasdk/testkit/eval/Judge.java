/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import java.util.List;
import java.util.Locale;

/**
 * A model asked whether a reply meets a criterion, as a {@link Evaluator}.
 *
 * <p>For the expectations the built-ins cannot state: that an answer explains itself, keeps the
 * customer's tone, or refuses what it should refuse. A criterion is written in words, the judge
 * returns a score and a reason, and a threshold turns that into a finding.
 *
 * <pre>{@code
 * Expectations.expect()
 *     .tools("getCustomer")
 *     .satisfies(judge.mustSatisfy("the reply states the customer's tier and invents nothing"));
 * }</pre>
 *
 * <p>This interface holds no model and no provider. {@link #agent} is the one implementation
 * shipped: it puts the question to the {@link JudgeAgent}, whose model is the consumer's. A test
 * returns a {@link Verdict} directly, or mocks that agent's model, which is what keeps a suite that
 * uses a judge runnable with no provider.
 *
 * <p>A judged case is scored on an opinion, so it does not reproduce the way the built-ins do. Two
 * runs of the same reply can land either side of a threshold. Judge what has no right answer to
 * compare against, gate a batch on the rate rather than asserting per case, and keep the
 * deterministic checks for everything else.
 */
@FunctionalInterface
public interface Judge {

  Verdict assess(Question question);

  /** The judge that asks a model through {@link JudgeAgent}, using the TestKit's client. */
  static AgentJudge agent(akka.javasdk.testkit.TestKit testKit) {
    if (testKit == null) throw new IllegalArgumentException("testKit required");
    return AgentJudge.backedBy(testKit.getComponentClient());
  }

  /**
   * What the judge is asked. The criterion is the consumer's sentence; the rest is the evidence the
   * case produced.
   */
  record Question(String criterion, String userMessage, String reply, List<ToolCall> toolCalls) {

    public Question {
      if (criterion == null || criterion.isBlank())
        throw new IllegalArgumentException("criterion required");
      userMessage = userMessage == null ? "" : userMessage;
      reply = reply == null ? "" : reply;
      toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * The tools called while answering, in order, for a criterion about how the reply was reached.
     */
    public List<String> toolNames() {
      return toolCalls.stream().map(ToolCall::name).toList();
    }
  }

  /**
   * What the judge said.
   *
   * @param score between 0 and 1. Anything else, {@code NaN} included, is read as "the judge did
   *     not answer" and abstains rather than failing the case
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

  /** The criterion has to score at least this. */
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

  /** The criterion at the middle of the scale, which is where a judged check starts. */
  default Evaluator mustSatisfy(String criterion) {
    return scoringAtLeast(criterion, 0.5);
  }
}
