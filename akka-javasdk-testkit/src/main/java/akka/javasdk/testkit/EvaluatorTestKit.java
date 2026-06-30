/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.testkit.impl.EvaluatorResultImpl;
import akka.javasdk.testkit.impl.TestKitEvaluationContext;
import java.util.function.Supplier;

/**
 * Evaluator Testkit for use in unit tests for Evaluators.
 *
 * <p>To test an Evaluator create a testkit instance by calling {@link
 * EvaluatorTestKit#of(Supplier)}. The returned testkit can be used as many times as you want; it
 * doesn't preserve any state between invocations.
 *
 * <p>The {@code factory} supplies the evaluator under test — construct it with whatever
 * collaborators the test needs (for example a stubbed {@code ComponentClient} for LLM-as-judge
 * calls, or an in-memory ledger client). Use the {@code evaluate} methods to run the {@code
 * evaluate} handler over a chosen {@link Subject} and assert on the returned {@link
 * EvaluatorResult}.
 */
public class EvaluatorTestKit<E extends Evaluator> {

  /** The default evaluation id used when one is not supplied. */
  public static final String DEFAULT_EVALUATION_ID = "test-evaluation";

  private final Supplier<E> evaluatorFactory;

  private EvaluatorTestKit(Supplier<E> evaluatorFactory) {
    this.evaluatorFactory = evaluatorFactory;
  }

  /** Create a testkit for the evaluator supplied by the given factory. */
  public static <E extends Evaluator> EvaluatorTestKit<E> of(Supplier<E> evaluatorFactory) {
    return new EvaluatorTestKit<>(evaluatorFactory);
  }

  /**
   * Run the evaluator's {@code evaluate} handler over the given subject, using the {@link
   * #DEFAULT_EVALUATION_ID}.
   *
   * @param subject the interaction to evaluate
   * @return the result of the evaluation
   */
  public EvaluatorResult evaluate(Subject subject) {
    return evaluate(subject, DEFAULT_EVALUATION_ID);
  }

  /**
   * Run the evaluator's {@code evaluate} handler over the given subject, using the given evaluation
   * id.
   *
   * @param subject the interaction to evaluate
   * @param evaluationId the id of the evaluation (also used to derive judge session ids)
   * @return the result of the evaluation
   */
  public EvaluatorResult evaluate(Subject subject, String evaluationId) {
    var context = new TestKitEvaluationContext(subject, evaluationId);
    var effect = evaluatorFactory.get().evaluate(context);
    return new EvaluatorResultImpl(effect);
  }
}
