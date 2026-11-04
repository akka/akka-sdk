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
import java.util.Map;
import java.util.function.Supplier;

/**
 * Test harness for unit testing an {@link Evaluator} in isolation.
 *
 * <p>Create a testkit instance using {@link #of(Supplier)}. The instance preserves no state between
 * invocations and can be reused across test cases.
 *
 * <p>Provide a factory supplier that constructs the evaluator with any required test doubles or
 * stubs, such as an in-memory {@link TestLedgerClient} or a stubbed {@link
 * akka.javasdk.client.ComponentClient}.
 *
 * <p>Call {@code evaluate(...)} to execute the evaluator handler against a {@link Subject} and
 * assert on the returned {@link EvaluatorResult}. If the evaluator reads configuration, supply it
 * using {@link #withConfig(Config)}.
 *
 * @param <E> the type of evaluator under test
 */
public class EvaluatorTestKit<E extends Evaluator> {

  /** Default evaluation ID used when an explicit ID is omitted. */
  public static final String DEFAULT_EVALUATION_ID = "test-evaluation";

  private final Supplier<E> evaluatorFactory;
  private final Config config;

  private EvaluatorTestKit(Supplier<E> evaluatorFactory, Config config) {
    this.evaluatorFactory = evaluatorFactory;
    this.config = config;
  }

  /**
   * Creates a testkit for the evaluator supplied by the given factory.
   *
   * @param evaluatorFactory factory that creates the evaluator instance under test
   * @param <E> the type of evaluator under test
   * @return a new testkit instance
   */
  public static <E extends Evaluator> EvaluatorTestKit<E> of(Supplier<E> evaluatorFactory) {
    return new EvaluatorTestKit<>(evaluatorFactory, ConfigFactory.empty());
  }

  /**
   * Returns a copy of this testkit that exposes the given configuration to the evaluator.
   *
   * <p>The evaluator accesses this configuration through {@link
   * akka.javasdk.evaluation.EvaluationContext#config()}.
   *
   * @param config configuration provided to the evaluation context
   * @return a copy of this testkit with the specified configuration
   */
  public EvaluatorTestKit<E> withConfig(Config config) {
    return new EvaluatorTestKit<>(evaluatorFactory, config);
  }

  /**
   * Runs the evaluator against the given subject using {@link #DEFAULT_EVALUATION_ID} and empty
   * attributes.
   *
   * @param subject the subject to evaluate; must not be null
   * @return the evaluation result containing the outcome effect
   */
  public EvaluatorResult evaluate(Subject subject) {
    return evaluate(subject, DEFAULT_EVALUATION_ID, Map.of());
  }

  /**
   * Runs the evaluator against the given subject using the specified evaluation ID and empty
   * attributes.
   *
   * @param subject the subject to evaluate; must not be null
   * @param evaluationId the evaluation ID; must not be null
   * @return the evaluation result containing the outcome effect
   */
  public EvaluatorResult evaluate(Subject subject, String evaluationId) {
    return evaluate(subject, evaluationId, Map.of());
  }

  /**
   * Runs the evaluator against the given subject with trigger attributes, using {@link
   * #DEFAULT_EVALUATION_ID}.
   *
   * @param subject the subject to evaluate; must not be null
   * @param attributes trigger attributes exposed through {@link
   *     akka.javasdk.evaluation.EvaluationContext#attributes()}; must not be null
   * @return the evaluation result containing the outcome effect
   */
  public EvaluatorResult evaluate(Subject subject, Map<String, String> attributes) {
    return evaluate(subject, DEFAULT_EVALUATION_ID, attributes);
  }

  /**
   * Runs the evaluator against the given subject with the specified evaluation ID and trigger
   * attributes.
   *
   * @param subject the subject to evaluate; must not be null
   * @param evaluationId the evaluation ID; must not be null
   * @param attributes trigger attributes exposed through {@link
   *     akka.javasdk.evaluation.EvaluationContext#attributes()}; must not be null
   * @return the evaluation result containing the outcome effect
   */
  public EvaluatorResult evaluate(
      Subject subject, String evaluationId, Map<String, String> attributes) {
    var context = new TestKitEvaluationContext(subject, evaluationId, attributes, config);
    var effect = evaluatorFactory.get().evaluate(context);
    return new EvaluatorResultImpl(effect);
  }
}
