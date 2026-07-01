/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.javasdk.impl.evaluation.EvaluatorEffectImpl;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * An Evaluator is a stateless component that evaluates agent interactions.
 *
 * <p>An Evaluator is bound to one or more agents with {@link
 * akka.javasdk.annotations.EvaluatesAgent}. The runtime invokes {@link
 * #evaluate(EvaluationContext)} for each interaction of a bound agent, passing an {@link
 * EvaluationContext} that identifies the interaction to evaluate. The handler returns an {@link
 * Effect} describing the outcome — the recorded evaluations, a deliberate error, or an asynchronous
 * continuation.
 *
 * <p>Blocking calls made from {@link #evaluate(EvaluationContext)} run on virtual threads. For
 * durable multi-step evaluation, delegate to a workflow and return {@link
 * Effect.Builder#asyncEffect(CompletionStage)}.
 *
 * <p>Concrete classes can accept the following types to the constructor:
 *
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient}
 *   <li>{@link akka.javasdk.http.HttpClientProvider}
 *   <li>{@link akka.javasdk.timer.TimerScheduler}
 *   <li>{@link akka.stream.Materializer}
 *   <li>{@link com.typesafe.config.Config}
 *   <li>{@link akka.javasdk.agent.AgentRegistry}
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup
 * </ul>
 *
 * <p>Concrete class must be annotated with {@link akka.javasdk.annotations.Component} and at least
 * one {@link akka.javasdk.annotations.EvaluatesAgent}.
 */
public abstract class Evaluator {

  /**
   * Evaluate the interaction identified by the given context.
   *
   * @param context identifies the interaction to evaluate and provides judge session ids
   * @return an {@link Effect} describing the outcome of the evaluation
   */
  public abstract Effect evaluate(EvaluationContext context);

  /** Returns a builder for the {@link Effect} to be returned by {@link #evaluate}. */
  protected final Effect.Builder effects() {
    return EvaluatorEffectImpl.builder();
  }

  /**
   * An Effect is a description of what the runtime needs to do after the evaluation is handled.
   *
   * <p>An Evaluator Effect can either:
   *
   * <ul>
   *   <li>record one or more {@link Evaluation}s
   *   <li>report a deliberate error — the evaluation could not be performed
   *   <li>continue asynchronously from a {@link CompletionStage} of another effect
   * </ul>
   */
  public interface Effect {

    /**
     * Construct the effect that is returned by the evaluation handler. The effect describes the
     * outcome of the evaluation.
     */
    interface Builder {

      /**
       * Record the result of the evaluation.
       *
       * @param evaluation the evaluation outcome
       * @param more additional evaluation outcomes
       * @return the record effect
       */
      Effect record(Evaluation evaluation, Evaluation... more);

      /**
       * Record the results of the evaluation.
       *
       * @param evaluations the evaluation outcomes (must not be empty)
       * @return the record effect
       */
      Effect record(List<Evaluation> evaluations);

      /**
       * Report that the evaluation could not be performed.
       *
       * <p>This is a deliberate "could not evaluate" outcome, distinct from a failure caused by a
       * thrown exception or a failed future.
       *
       * @param reason the reason the evaluation could not be performed
       * @return the error effect
       */
      Effect error(String reason);

      /**
       * Continue the evaluation asynchronously from the result of the given stage.
       *
       * <p>The pending stage represents a suspended evaluation; the runtime completes the
       * evaluation when the stage completes.
       *
       * @param futureEffect the future effect to continue with
       * @return the async effect
       */
      Effect asyncEffect(CompletionStage<Effect> futureEffect);
    }
  }
}
