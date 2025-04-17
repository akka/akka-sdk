/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.workflow;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

public class StepBuilder {

  final private String name;

  /**
   * Not for direct user construction, instances are created through the workflow DSL
   */
  public StepBuilder(String name) {
    this.name = name;
  }

  public StepBuilder step(String stepName) {
    return new StepBuilder(stepName);
  }

  /**
   * Build a step action with a  call.
   * <p>
   * The value returned by the call {@link Function} is made available to this workflow via the {@code andThen} method.
   * In the {@code andThen} method, we can use the result to update the workflow state and transition to the next step.
   * <p>
   * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
   *
   * @param callInputClass Input class for call factory.
   * @param callFactory    Factory method for creating a call.
   * @param <Input>        Input for the call factory, provided by transition method.
   * @param <Output>       Output of the call.
   * @return Step builder.
   */
  public <Input, Output> CallStepBuilder<Input, Output> call(Class<Input> callInputClass, Function<Input, Output> callFactory) {
    return new CallStepBuilder<>(name, callInputClass, callFactory);
  }

  /**
   * Build a step action with a call.
   * <p>
   * The value returned by the {@link Supplier} is made available to this workflow via the {@code andThen} method.
   * In the {@code andThen} method, we can use the result to update the workflow state and transition to the next step.
   * <p>
   * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
   *
   * @param callSupplier Factory method for creating a call.
   * @param <Output>     Output of the call.
   * @return Step builder.
   */
  public <Output> CallStepBuilder<Void, Output> call(Supplier<Output> callSupplier) {
    return new CallStepBuilder<>(name, Void.class, (Void v) -> callSupplier.get());
  }

  /**
   * Build a step action with a call.
   * <p>
   * The {@link Runnable} passed to this method is executed and if successful, the {@code andThen} method is called.
   * In the {@code andThen} method, we can update the workflow state and transition to the next step.
   * <p>
   * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
   *
   * @return Step builder.
   */
  public RunnableStepBuilder call(Runnable runnable) {
    return new RunnableStepBuilder(name,runnable);
  }

  /**
   * Build a step action with an async call.
   * <p>
   * The {@link Function} passed to this method should return a {@link CompletionStage}.
   * On successful completion, its result is made available to this workflow via the {@code andThen} method.
   * In the {@code andThen} method, we can use the result to update the workflow state and transition to the next step.
   * <p>
   * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
   *
   * @param callInputClass Input class for call factory.
   * @param callFactory    Factory method for creating async call.
   * @param <Input>        Input for async call factory, provided by transition method.
   * @param <Output>       Output of async call.
   * @return Step builder.
   */
  public <Input, Output> AsyncCallStepBuilder<Input, Output> asyncCall(Class<Input> callInputClass, Function<Input, CompletionStage<Output>> callFactory) {
    return new AsyncCallStepBuilder<>(name, callInputClass, callFactory);
  }


  /**
   * Build a step action with an async call.
   * <p>
   * The {@link Supplier} function passed to this method should return a {@link CompletionStage}.
   * On successful completion, its result is made available to this workflow via the {@code andThen} method.
   * In the {@code andThen} method, we can use the result to update the workflow state and transition to the next step.
   * <p>
   * On failure, the step will be retried according to the default retry strategy or the one defined in the step configuration.
   *
   * @param callSupplier Factory method for creating async call.
   * @param <Output>     Output of async call.
   * @return Step builder.
   */
  public <Output> AsyncCallStepBuilder<Void, Output> asyncCall(Supplier<CompletionStage<Output>> callSupplier) {
    return new AsyncCallStepBuilder<>(name, Void.class, (Void v) -> callSupplier.get());
  }

  public static class CallStepBuilder<CallInput, CallOutput> {
    final private String name;

    final private Class<CallInput> callInputClass;
    final private Function<CallInput, CallOutput> callFunc;

    CallStepBuilder(String name, Class<CallInput> callInputClass, Function<CallInput, CallOutput> callFunc) {
      this.name = name;
      this.callInputClass = callInputClass;
      this.callFunc = callFunc;
    }

    /**
     * Transition to the next step based on the result of the step call.
     * <p>
     * The {@link Function} passed to this method should receive the return type of the step call and return
     * an {@link Workflow.Effect.TransitionalEffect} describing the next step to transition to.
     * <p>
     * When defining the Effect, you can update the workflow state and indicate the next step to transition to.
     * This can be another step, or a pause or end of the workflow.
     * <p>
     * When transition to another step, you can also pass an input parameter to the next step.
     *
     * @param transitionInputClass Input class for transition.
     * @param transitionFunc       Function that transform the action result to a {@link Workflow.Effect.TransitionalEffect}
     * @return AsyncCallStep
     */
    public Workflow.CallStep<CallInput, CallOutput, ?> andThen(Class<CallOutput> transitionInputClass, Function<CallOutput, Workflow.Effect.TransitionalEffect<Void>> transitionFunc) {
      return new Workflow.CallStep<>(name, callInputClass, callFunc, transitionInputClass, transitionFunc);
    }

    public Workflow.CallStep<CallInput, CallOutput, ?> andThen(Supplier<Workflow.Effect.TransitionalEffect<Void>> transitionFunc) {
      return new Workflow.CallStep<>(name, callInputClass, callFunc, null, __ -> transitionFunc.get());
    }
  }

  public static class RunnableStepBuilder {
    final private String name;

    final private Runnable runnable;

    RunnableStepBuilder(String name, Runnable runnable) {
      this.name = name;
      this.runnable = runnable;
    }

    /**
     * Transition to the next step after the step call completes.
     * <p>
     * The {@link Supplier} passed to this method should return
     * an {@link Workflow.Effect.TransitionalEffect} describing the next step to transition to.
     * <p>
     * When defining the Effect, you can update the workflow state and indicate the next step to transition to.
     * This can be another step, or a pause or end of the workflow.
     * <p>
     * When transition to another step, you can also pass an input parameter to the next step.
     *
     * @param transitionFunc       Supplier of{@link Workflow.Effect.TransitionalEffect}
     * @return AsyncCallStep
     */
    public Workflow.RunnableStep andThen(Supplier<Workflow.Effect.TransitionalEffect<Void>> transitionFunc) {
      return new Workflow.RunnableStep(name, runnable, transitionFunc);
    }
  }

  public static class AsyncCallStepBuilder<CallInput, CallOutput> {

    final private String name;

    final private Class<CallInput> callInputClass;
    final private Function<CallInput, CompletionStage<CallOutput>> callFunc;

    /**
     * Not for direct user construction, created through {{akka.javasdk.workflow.StepBuilder#asyncCall(java.lang.Class, java.util.function.Function)}}
     */
    public AsyncCallStepBuilder(String name, Class<CallInput> callInputClass, Function<CallInput, CompletionStage<CallOutput>> callFunc) {
      this.name = name;
      this.callInputClass = callInputClass;
      this.callFunc = callFunc;
    }

    /**
     * Transition to the next step based on the result of the step call.
     * <p>
     * The {@link Function} passed to this method should receive the return type of the step call and return
     * an {@link Workflow.Effect.TransitionalEffect} describing the next step to transition to.
     * <p>
     * When defining the Effect, you can update the workflow state and indicate the next step to transition to.
     * This can be another step, or a pause or end of the workflow.
     * <p>
     * When transition to another step, you can also pass an input parameter to the next step.
     *
     * @param transitionInputClass Input class for transition.
     * @param transitionFunc       Function that transform the action result to a {@link Workflow.Effect.TransitionalEffect}
     * @return AsyncCallStep
     */
    public Workflow.AsyncCallStep<CallInput, CallOutput, ?> andThen(Class<CallOutput> transitionInputClass, Function<CallOutput, Workflow.Effect.TransitionalEffect<Void>> transitionFunc) {
      return new Workflow.AsyncCallStep<>(name, callInputClass, callFunc, transitionInputClass, transitionFunc);
    }

    public Workflow.AsyncCallStep<CallInput, CallOutput, ?> andThen(Supplier<
      Workflow.Effect.TransitionalEffect<Void>> transitionFunc) {
      return new Workflow.AsyncCallStep<>(name, callInputClass, callFunc, null, __ -> transitionFunc.get());
    }
  }
}
