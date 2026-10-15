/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.evaluation.Evaluator;
import akka.javasdk.evaluation.Subject;
import akka.javasdk.testkit.impl.EvaluatorResultImpl;
import akka.javasdk.testkit.impl.TestKitEvaluationContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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
 * EvaluatorResult}. An evaluator that reads {@link
 * akka.javasdk.evaluation.EvaluationContext#config()} gets the settings given to {@link
 * #withSettings(Config)}, empty by default.
 */
public class EvaluatorTestKit<E extends Evaluator> {

  /** The default evaluation id used when one is not supplied. */
  public static final String DEFAULT_EVALUATION_ID = "test-evaluation";

  private final Supplier<E> evaluatorFactory;
  private final Config settings;

  private EvaluatorTestKit(Supplier<E> evaluatorFactory, Config settings) {
    this.evaluatorFactory = evaluatorFactory;
    this.settings = settings;
  }

  /** Create a testkit for the evaluator supplied by the given factory. */
  public static <E extends Evaluator> EvaluatorTestKit<E> of(Supplier<E> evaluatorFactory) {
    return new EvaluatorTestKit<>(evaluatorFactory, ConfigFactory.empty());
  }

  /**
   * A testkit whose evaluations see the given settings through {@link
   * akka.javasdk.evaluation.EvaluationContext#config()}. In a running service these are the {@code
   * settings} of the binding over the {@code settings} of the evaluator.
   *
   * @param settings the settings for the evaluations
   * @return a testkit with the settings
   */
  public EvaluatorTestKit<E> withSettings(Config settings) {
    return new EvaluatorTestKit<>(evaluatorFactory, settings);
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
   * @param evaluationId the id of the evaluation
   * @return the result of the evaluation
   */
  public EvaluatorResult evaluate(Subject subject, String evaluationId) {
    var context = new TestKitEvaluationContext(subject, evaluationId, settings);
    var effect = evaluatorFactory.get().evaluate(context);
    return new EvaluatorResultImpl(effect);
  }
}
