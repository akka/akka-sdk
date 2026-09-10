/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

/**
 * Decides how well a reply meets a criterion given as a sentence. {@link #mustSatisfy} and {@link
 * #scoringAtLeast} turn the score into an {@link Evaluator}.
 *
 * <pre>{@code
 * var judge = Judge.modelBased(testKit);
 *
 * EvalCase.of(
 *     "tier",
 *     "Which tier is cust_1 on?",
 *     Evaluators.tools("getCustomer"),
 *     judge.mustSatisfy("the reply states the customer's tier and invents nothing"));
 * }</pre>
 *
 * <p>{@link #modelBased} asks a model through {@link JudgeAgent}, which uses the model provider of
 * the test configuration. A test can also return a {@link Verdict} directly, or mock that agent's
 * model.
 *
 * <p>A judged score can differ between runs of the same reply. Use a judge for criteria that have
 * no exact answer to compare against, and gate a batch on the rate instead of asserting per case.
 */
public interface Judge {

  /**
   * @param criterion what a good reply does, as one sentence
   * @param interaction the user message, the reply and the evidence the case produced
   */
  Verdict decide(String criterion, Interaction interaction);

  /** A judge that asks a model through {@link JudgeAgent}, using the TestKit component client. */
  static ModelBasedJudge modelBased(akka.javasdk.testkit.TestKit testKit) {
    if (testKit == null) throw new IllegalArgumentException("testKit required");
    return ModelBasedJudge.backedBy(testKit.getComponentClient());
  }

  /**
   * The judge's answer.
   *
   * @param score between 0 and 1. Any other value, {@code NaN} included, makes the evaluator
   *     inconclusive
   * @param reason one line, printed under a failed case. Empty when the model gave none
   */
  record Verdict(double score, String reason) {

    public Verdict {
      // The model's JSON reply is read into this record, and a model may leave the reason out.
      reason = reason == null ? "" : reason;
    }

    public static Verdict of(double score, String reason) {
      return new Verdict(score, reason);
    }
  }

  /**
   * The criterion must score at least this. Inconclusive when there is no reply, when the judge
   * throws or gives no verdict, or when the score is not between 0 and 1.
   */
  default Evaluator scoringAtLeast(String criterion, double threshold) {
    if (criterion == null || criterion.isBlank())
      throw new IllegalArgumentException("criterion required");
    return new JudgeEvaluator(this, criterion, threshold);
  }

  /** The criterion must score at least 0.5. */
  default Evaluator mustSatisfy(String criterion) {
    return scoringAtLeast(criterion, 0.5);
  }
}
