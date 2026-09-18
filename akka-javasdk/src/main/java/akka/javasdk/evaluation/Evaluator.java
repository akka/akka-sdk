/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import akka.javasdk.annotations.Evaluates;
import akka.javasdk.annotations.EvaluatorVersion;
import akka.javasdk.impl.evaluation.EvaluatorEffectImpl;
import java.util.concurrent.CompletionStage;

/**
 * An Evaluator is a stateless component that evaluates agent interactions.
 *
 * <p>An Evaluator is bound to one or more agents through configuration under {@code
 * akka.javasdk.evaluation.evaluators}, keyed by the evaluator's component id. The runtime invokes
 * {@link #evaluate(EvaluationContext)} for each interaction of a bound agent, passing an {@link
 * EvaluationContext} that identifies the interaction to evaluate. The handler returns an {@link
 * Effect} describing the outcome — the recorded evaluations, an inconclusive result, or an
 * asynchronous continuation.
 *
 * <p>Annotate the class with {@link Evaluates} to declare the {@link Subject} kinds it can be bound
 * to; a binding naming a kind it does not declare is rejected at startup. Defaults to {@link
 * Subject.Interaction} when absent.
 *
 * <p>Annotate the class with {@link EvaluatorVersion} when what this evaluator measures, or how,
 * changes — a different prompt, a different scoring rule, a different model. Defaults to {@code
 * "1"} when absent.
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
 * <p>Concrete class must be annotated with {@link akka.javasdk.annotations.Component}.
 */
public abstract class Evaluator {

  /**
   * Evaluate the interaction identified by the given context.
   *
   * @param context identifies the interaction to evaluate
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
   *   <li>complete with an {@link Evaluation} (the verdict)
   *   <li>report that the evaluation was inconclusive — it ran but reached no verdict
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
       * Complete the evaluation with its verdict.
       *
       * <p>An evaluator with several criteria combines them itself into one verdict here; record
       * each criterion as a classification against the evaluation id so a full breakdown survives
       * one model call, or use a separate evaluator per criterion when they don't combine into one
       * verdict.
       *
       * @param evaluation the evaluation outcome
       * @return the complete effect
       */
      Effect complete(Evaluation evaluation);

      /**
       * Report that the evaluation was inconclusive.
       *
       * <p>Use this when the evaluator ran but could not reach a verdict, for example there was no
       * transcript or the interaction was not applicable.
       *
       * @param reason why the evaluation was inconclusive
       * @return the inconclusive effect
       */
      Effect inconclusive(String reason);

      /**
       * Continue the evaluation asynchronously from the result of the given stage.
       *
       * @param futureEffect the future effect to continue with
       * @return the async effect
       */
      Effect asyncEffect(CompletionStage<Effect> futureEffect);
    }
  }
}
