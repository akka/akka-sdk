/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.annotation.InternalApi;
import akka.javasdk.impl.evaluation.WorkflowEvaluatorEffects;
import java.time.Duration;
import java.util.Optional;

/**
 * A WorkflowEvaluator is a stateful, durable component that evaluates agent interactions in
 * multiple steps.
 *
 * <p>Use a WorkflowEvaluator instead of an {@link Evaluator} when the evaluation is long-running or
 * needs durable, multi-step execution — for example composed evaluations that accumulate state
 * across several judge calls, or evaluations that wait on human review. Each evaluation runs as its
 * own durable instance: if it is stopped for any reason, it resumes from the last completed step.
 *
 * <p>Like an {@link Evaluator}, a WorkflowEvaluator is bound to one or more agents through
 * configuration under {@code akka.javasdk.evaluation.evaluators}, keyed by the evaluator's
 * component id. The runtime invokes {@link #onEvaluation(EvaluationContext)} for each interaction
 * of a bound agent. The evaluation then progresses through steps — methods returning {@link Effect}
 * — until it finishes with {@link Effect.Builder#complete} or {@link Effect.Builder#inconclusive},
 * which records the outcome and cleans up the instance. There is no other way to finish: every
 * evaluation terminates with a recorded outcome, including step failures that exhaust their
 * retries.
 *
 * <p>Concrete classes can accept the following types to the constructor:
 *
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient}
 *   <li>{@link akka.javasdk.http.HttpClientProvider}
 *   <li>{@link akka.stream.Materializer}
 *   <li>{@link com.typesafe.config.Config}
 *   <li>{@link akka.javasdk.agent.AgentRegistry}
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup
 * </ul>
 *
 * <p>Concrete class must be annotated with {@link akka.javasdk.annotations.Component}.
 *
 * <p>Annotate the class with {@link akka.javasdk.annotations.Evaluates} to declare the {@link
 * Subject} kinds it can be bound to; a binding naming a kind it does not declare is rejected at
 * startup. Defaults to {@link Subject.Interaction} when absent.
 *
 * <p>Annotate the class with {@link akka.javasdk.annotations.EvaluatorVersion} when what this
 * evaluator measures, or how, changes. Defaults to {@code "1"} when absent.
 *
 * @param <S> The type of the state accumulated across the steps of this evaluation.
 */
public abstract class WorkflowEvaluator<S> {

  private Optional<S> currentState = Optional.empty();
  private Optional<EvaluationContext> evaluationContext = Optional.empty();
  private boolean stateHasBeenSet = false;

  /**
   * Start the evaluation of the interaction identified by the given context. Invoked by the runtime
   * when a trigger fires for this evaluator.
   *
   * @param context identifies the interaction to evaluate
   * @return an {@link Effect} with the first step transition, or directly the outcome
   */
  public abstract Effect onEvaluation(EvaluationContext context);

  /**
   * Returns the initial empty state object, passed into the step methods until a new state replaces
   * it.
   *
   * <p>The default implementation of this method returns {@code null}. It can be overridden to
   * return a more sensible initial state.
   */
  public S emptyState() {
    return null;
  }

  /**
   * Returns the state as currently stored.
   *
   * <p>Note that modifying the state directly will not update it in storage. To save the state, one
   * must call {@code effects().updateState()}.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final S currentState() {
    if (stateHasBeenSet) return currentState.orElse(null);
    else
      throw new IllegalStateException(
          "Current state is only available when handling the evaluation. Make sure that you are"
              + " calling the `currentState` method only in `onEvaluation` or a step method.");
  }

  /**
   * The evaluation being run — the subject under evaluation and the evaluation id. Available in
   * {@link #onEvaluation} and in every step method.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final EvaluationContext evaluationContext() {
    return evaluationContext.orElseThrow(
        () ->
            new IllegalStateException(
                "EvaluationContext is only available when handling the evaluation."));
  }

  /**
   * Returns a builder for the {@link Effect} returned by {@link #onEvaluation} and step methods.
   */
  protected final Effect.Builder<S> effects() {
    return WorkflowEvaluatorEffects.createBuilder();
  }

  /** Override to configure timeouts and retries for this evaluator. */
  public Settings settings() {
    return Settings.defaults();
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetup(S state, EvaluationContext context) {
    this.stateHasBeenSet = true;
    this.currentState = Optional.ofNullable(state);
    this.evaluationContext = Optional.of(context);
  }

  /**
   * An Effect is a description of what the runtime needs to do after `onEvaluation` or a step
   * method is handled.
   *
   * <p>An Effect can either:
   *
   * <ul>
   *   <li>update the state of the evaluation
   *   <li>transition to the next step
   *   <li>complete the evaluation with an {@link Evaluation} (the verdict)
   *   <li>report that the evaluation was inconclusive — it ran but reached no verdict
   * </ul>
   *
   * <p>Completing (or reporting inconclusive) records the outcome and cleans up the evaluation
   * instance; no further transitions are possible.
   */
  public interface Effect {

    /**
     * Construct the effect that is returned by {@link #onEvaluation} or a step method.
     *
     * @param <S> The type of the state for this evaluation.
     */
    interface Builder<S> {

      /** Update the state of the evaluation, kept across steps. */
      PersistenceEffectBuilder<S> updateState(S newState);

      /**
       * Defines the next step to which the evaluation should transition to.
       *
       * <p>The step is identified by a method reference to a method that accepts no input
       * parameters and returns an {@link Effect}.
       *
       * @param methodRef Reference to the step method
       * @param <W> The evaluator type containing the step method
       */
      <W> Effect transitionTo(akka.japi.function.Function<W, Effect> methodRef);

      /**
       * Defines the next step to which the evaluation should transition to.
       *
       * <p>The step is identified by a method reference that accepts an input parameter.
       *
       * @param methodRef Reference to the step method
       * @param <W> The evaluator type containing the step method
       * @param <I> The input parameter type for the step
       * @return A builder to provide the input parameter
       */
      <W, I> WithInput<I, Effect> transitionTo(
          akka.japi.function.Function2<W, I, Effect> methodRef);

      /**
       * Complete the evaluation with its verdict. The outcome is recorded and the evaluation
       * instance is cleaned up.
       *
       * <p>An evaluator with several criteria combines them itself into one verdict here; record
       * each criterion as a classification against the evaluation id so a full breakdown survives
       * one model call, or use a separate evaluator per criterion when they don't combine into one
       * verdict.
       *
       * @param evaluation the evaluation outcome
       */
      Effect complete(Evaluation evaluation);

      /**
       * Report that the evaluation was inconclusive — it ran but could not reach a verdict, for
       * example there was no transcript or the interaction was not applicable.
       *
       * @param reason why the evaluation was inconclusive
       */
      Effect inconclusive(String reason);
    }

    /** Effect builder after the state was updated, for defining the follow-up transition. */
    interface PersistenceEffectBuilder<S> {

      /**
       * Defines the next step to which the evaluation should transition to.
       *
       * @param methodRef Reference to the step method
       * @param <W> The evaluator type containing the step method
       */
      <W> Effect transitionTo(akka.japi.function.Function<W, Effect> methodRef);

      /**
       * Defines the next step to which the evaluation should transition to, with an input
       * parameter.
       *
       * @param methodRef Reference to the step method
       * @param <W> The evaluator type containing the step method
       * @param <I> The input parameter type for the step
       * @return A builder to provide the input parameter
       */
      <W, I> WithInput<I, Effect> transitionTo(
          akka.japi.function.Function2<W, I, Effect> methodRef);

      /**
       * Complete the evaluation with its verdict. The outcome is recorded and the evaluation
       * instance is cleaned up.
       *
       * @param evaluation the evaluation outcome
       */
      Effect complete(Evaluation evaluation);

      /**
       * Report that the evaluation was inconclusive — it ran but could not reach a verdict.
       *
       * @param reason why the evaluation was inconclusive
       */
      Effect inconclusive(String reason);
    }
  }

  /**
   * Represents an operation that accepts an input of type I and produces a result of type R. Used
   * by builders accepting {@link akka.japi.function.Function2}.
   */
  public interface WithInput<I, R> {
    R withInput(I input);
  }

  /**
   * Settings for a workflow evaluator. Unset values fall back to the runtime defaults.
   *
   * <p>Failures are always terminal: when a step exhausts its retries, or the evaluation timeout
   * expires, a failed evaluation is recorded and the instance is cleaned up.
   */
  public static final class Settings {

    private final Optional<Duration> evaluationTimeout;
    private final Optional<Duration> defaultStepTimeout;
    private final Optional<Integer> maxStepRetries;

    private Settings(
        Optional<Duration> evaluationTimeout,
        Optional<Duration> defaultStepTimeout,
        Optional<Integer> maxStepRetries) {
      this.evaluationTimeout = evaluationTimeout;
      this.defaultStepTimeout = defaultStepTimeout;
      this.maxStepRetries = maxStepRetries;
    }

    /** Settings with all values falling back to the runtime defaults. */
    public static Settings defaults() {
      return new Settings(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Maximum duration of the entire evaluation. When it expires, a failed evaluation is recorded
     * and the instance is cleaned up.
     */
    public Settings withEvaluationTimeout(Duration timeout) {
      return new Settings(Optional.of(timeout), defaultStepTimeout, maxStepRetries);
    }

    /** Default timeout for each step of the evaluation. */
    public Settings withDefaultStepTimeout(Duration timeout) {
      return new Settings(evaluationTimeout, Optional.of(timeout), maxStepRetries);
    }

    /**
     * Number of retries for a failed step. Once retries are exhausted, a failed evaluation is
     * recorded and the instance is cleaned up.
     */
    public Settings withMaxStepRetries(int maxRetries) {
      return new Settings(evaluationTimeout, defaultStepTimeout, Optional.of(maxRetries));
    }

    public Optional<Duration> evaluationTimeout() {
      return evaluationTimeout;
    }

    public Optional<Duration> defaultStepTimeout() {
      return defaultStepTimeout;
    }

    public Optional<Integer> maxStepRetries() {
      return maxStepRetries;
    }
  }
}
