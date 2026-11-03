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
 * Evaluator Testkit for use in unit tests for Evaluators.
 *
 * <p>To test an Evaluator create a testkit instance by calling {@link
 * EvaluatorTestKit#of(Supplier)}. The returned testkit can be used as many times as you want; it
 * doesn't preserve any state between invocations.
 *
 * <p>The {@code factory} supplies the evaluator under test — construct it with whatever
 * collaborators the test needs (for example a stubbed {@code ComponentClient} for LLM-as-judge
 * calls, or an in-memory ledger client). Use the {@code evaluate} methods to run the {@code
 * evaluate} handler over a chosen {@link Subject}, with the attributes of an evaluation started on
 * request when the evaluator reads them, and assert on the returned {@link EvaluatorResult}. An
 * evaluator that reads {@link akka.javasdk.evaluation.EvaluationContext#config()} gets the
 * configuration given to {@link #withConfig(Config)}, empty by default.
 */
public class EvaluatorTestKit<E extends Evaluator> {

  /** The default evaluation id used when one is not supplied. */
  public static final String DEFAULT_EVALUATION_ID = "test-evaluation";

  private final Supplier<E> evaluatorFactory;
  private final Config config;

  private EvaluatorTestKit(Supplier<E> evaluatorFactory, Config config) {
    this.evaluatorFactory = evaluatorFactory;
    this.config = config;
  }

  /** Create a testkit for the evaluator supplied by the given factory. */
  public static <E extends Evaluator> EvaluatorTestKit<E> of(Supplier<E> evaluatorFactory) {
    return new EvaluatorTestKit<>(evaluatorFactory, ConfigFactory.empty());
  }

  /**
   * A testkit whose evaluations see the given configuration through {@link
   * akka.javasdk.evaluation.EvaluationContext#config()}. In a running service this is the binding
   * that the runtime used for the agent, with the evaluator entry as fallback.
   *
   * @param config the configuration for the evaluations
   * @return a testkit with the configuration
   */
  public EvaluatorTestKit<E> withConfig(Config config) {
    return new EvaluatorTestKit<>(evaluatorFactory, config);
  }

  /**
   * Run the evaluator's {@code evaluate} handler over the given subject, using the {@link
   * #DEFAULT_EVALUATION_ID} and no attributes.
   *
   * @param subject what to evaluate
   * @return the result of the evaluation
   */
  public EvaluatorResult evaluate(Subject subject) {
    return evaluate(subject, DEFAULT_EVALUATION_ID, Map.of());
  }

  /**
   * Run the evaluator's {@code evaluate} handler over the given subject, using the given evaluation
   * id and no attributes.
   *
   * @param subject what to evaluate
   * @param evaluationId the id of the evaluation
   * @return the result of the evaluation
   */
  public EvaluatorResult evaluate(Subject subject, String evaluationId) {
    return evaluate(subject, evaluationId, Map.of());
  }

  /**
   * Run the evaluator's {@code evaluate} handler over the given subject with the given attributes,
   * as an evaluation started through {@link akka.javasdk.client.EvaluatorClient} carries them,
   * using the {@link #DEFAULT_EVALUATION_ID}.
   *
   * @param subject what to evaluate
   * @param attributes the attributes the evaluation sees through {@link
   *     akka.javasdk.evaluation.EvaluationContext#attributes()}
   * @return the result of the evaluation
   */
  public EvaluatorResult evaluate(Subject subject, Map<String, String> attributes) {
    return evaluate(subject, DEFAULT_EVALUATION_ID, attributes);
  }

  /**
   * Run the evaluator's {@code evaluate} handler over the given subject with the given attributes,
   * using the given evaluation id.
   *
   * @param subject what to evaluate
   * @param evaluationId the id of the evaluation
   * @param attributes the attributes the evaluation sees through {@link
   *     akka.javasdk.evaluation.EvaluationContext#attributes()}
   * @return the result of the evaluation
   */
  public EvaluatorResult evaluate(
      Subject subject, String evaluationId, Map<String, String> attributes) {
    var context = new TestKitEvaluationContext(subject, evaluationId, attributes, config);
    var effect = evaluatorFactory.get().evaluate(context);
    return new EvaluatorResultImpl(effect);
  }
}
