/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.workflow;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.CommandException;
import akka.javasdk.Metadata;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.ErrorHandling;
import akka.javasdk.impl.client.MethodRefResolver;
import akka.javasdk.impl.workflow.WorkflowDescriptor;
import akka.javasdk.impl.workflow.WorkflowEffects;
import akka.javasdk.timer.TimerScheduler;
import akka.javasdk.workflow.Workflow.RecoverStrategy.MaxRetries;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Workflows are stateful components and are defined by a set of steps and transitions between them.
 *
 * <p>You can use workflows to implement business processes that span multiple services.
 *
 * <p>When implementing a workflow, you define a state type and a set of steps. Each step defines a
 * call to be executed and the transition to the next step based on the result of the call. The
 * workflow state can be updated after each successful step execution.
 *
 * <p>The runtime keeps track of the state of the workflow and the current step. If the workflow is
 * stopped for any reason, it can be resumed from the last known state and step.
 *
 * <p>Workflow methods that handle incoming commands should return an {@link Workflow.Effect}
 * describing the next processing actions.
 *
 * <p>Concrete classes can accept the following types to the constructor:
 *
 * <ul>
 *   <li>{@link ComponentClient}
 *   <li>{@link akka.javasdk.http.HttpClientProvider}
 *   <li>{@link akka.javasdk.timer.TimerScheduler}
 *   <li>{@link akka.stream.Materializer}
 *   <li>{@link com.typesafe.config.Config}
 *   <li>{@link akka.javasdk.workflow.WorkflowContext}
 *   <li>{@link akka.javasdk.agent.AgentRegistry}
 *   <li>{@link akka.javasdk.Sanitizer}
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup
 * </ul>
 *
 * <p>Concrete class must be annotated with {@link akka.javasdk.annotations.Component}.
 *
 * @param <S> The type of the state for this workflow.
 */
public abstract class Workflow<S> {

  private Optional<CommandContext> commandContext = Optional.empty();
  private Optional<TimerScheduler> timerScheduler = Optional.empty();

  private Optional<S> currentState = Optional.empty();

  private boolean stateHasBeenSet = false;
  private boolean deleted = false;

  /**
   * Start a step definition with a given step name.
   *
   * @param name Step name.
   * @return Step builder.
   * @deprecated use methods returning {@link StepEffect} instead.
   */
  @Deprecated
  public StepBuilder step(String name) {
    return new StepBuilder(name);
  }

  /**
   * Returns the initial empty state object. This object will be passed into the command and step
   * handlers, until a new state replaces it.
   *
   * <p>Also known as "zero state" or "neutral state".
   *
   * <p>The default implementation of this method returns {@code null}. It can be overridden to
   * return a more sensible initial state.
   */
  public S emptyState() {
    return null;
  }

  /**
   * Additional context and metadata for a command handler.
   *
   * <p>It will throw an exception if accessed from constructor.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final CommandContext commandContext() {
    return commandContext.orElseThrow(
        () ->
            new IllegalStateException("CommandContext is only available when handling a command."));
  }

  /** Returns a {@link TimerScheduler} that can be used to schedule further in time. */
  public final TimerScheduler timers() {
    return timerScheduler.orElseThrow(
        () ->
            new IllegalStateException(
                "Timers can only be scheduled or cancelled when handling a command or running a"
                    + " step action."));
  }

  /**
   * Returns the state as currently stored.
   *
   * <p>Note that modifying the state directly will not update it in storage. To save the state, one
   * must call {{@code effects().updateState()}}.
   *
   * <p>This method can only be called when handling a command. Calling it outside a method (eg: in
   * the constructor) will raise a IllegalStateException exception.
   *
   * @throws IllegalStateException if accessed outside a handler method
   */
  protected final S currentState() {
    // user may call this method inside a command handler and get a null because it's legal
    // to have emptyState set to null.
    if (stateHasBeenSet) return currentState.orElse(null);
    else
      throw new IllegalStateException(
          "Current state is only available when handling a command. Make sure that that you are"
              + " calling the `currentState` method only in the command handler or step `call`,"
              + " `andThen` lambda functions.");
  }

  /** Returns true if the entity has been deleted. */
  protected boolean isDeleted() {
    return deleted;
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetup(
      S state, CommandContext context, TimerScheduler timerScheduler, boolean deleted) {
    this.stateHasBeenSet = true;
    this.currentState = Optional.ofNullable(state);
    this.commandContext = Optional.of(context);
    this.timerScheduler = Optional.of(timerScheduler);
    this.deleted = deleted;
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetup(S state) {
    this.stateHasBeenSet = true;
    this.currentState = Optional.ofNullable(state);
  }

  /**
   * @deprecated use {@link Workflow#settings()} instead
   */
  @Deprecated
  public WorkflowDef<S> definition() {
    return new WorkflowDef<>(false);
  }

  public WorkflowSettings settings() {
    var def = definition();
    if (def.legacy) return new LegacyWorkflowSettings<>(def);
    else return WorkflowSettings.builder().build();
  }

  protected final Effect.Builder<S> effects() {
    return WorkflowEffects.createEffectBuilder();
  }

  protected final StepEffect.Builder<S> stepEffects() {
    return WorkflowEffects.createStepEffectBuilder();
  }

  /**
   * An Effect is a description of what the runtime needs to do after the command is handled. You
   * can think of it as a set of instructions you are passing to the runtime, which will process the
   * instructions on your behalf.
   *
   * <p>Each component defines its own effects, which are a set of predefined operations that match
   * the capabilities of that component.
   *
   * <p>A Workflow Effect can either:
   *
   * <p>
   *
   * <ul>
   *   <li>update the state of the workflow
   *   <li>define the next step to be executed (transition)
   *   <li>pause the workflow
   *   <li>end the workflow
   *   <li>fail the step or reject a command by returning an error
   *   <li>reply to incoming commands
   * </ul>
   *
   * <p>
   *
   * <p>
   *
   * @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the command handler or a step transition.
     *
     * <p>The effect describes next processing actions, such as updating state, transition to
     * another step and sending a reply.
     *
     * @param <S> The type of the state for this workflow.
     */
    interface Builder<S> {

      PersistenceEffectBuilder<S> updateState(S newState);

      /** Pause the workflow execution and wait for an external input, e.g. via command handler. */
      Transitional pause();

      /**
       * Pause the workflow execution with a reason description and wait for an external input, e.g.
       * via command handler.
       */
      Transitional pause(String reason);

      /**
       * Pause the workflow execution with advanced configuration options.
       *
       * <p>This method allows pausing the workflow with a timeout and a handler that will be
       * invoked when the timeout expires. The pause can also include an optional reason
       * description.
       *
       * <p>Use the {@link Workflow#pauseSetting(Duration)} method to start building the {@link
       * PauseSettings}.
       *
       * @param pauseSettings Configuration for the pause including timeout duration and handler
       */
      Transitional pause(PauseSettings pauseSettings);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step definition identified by {@code stepName} must have an input parameter of type
       * I. In other words, the next step call (or asyncCall) must have been defined with a {@link
       * Function} that accepts an input parameter of type I.
       *
       * @param stepName The step name that should be executed next.
       * @param input The input param for the next step.
       * @deprecated use {@link Builder#transitionTo(akka.japi.function.Function2)} instead.
       */
      @Deprecated
      <I> Transitional transitionTo(String stepName, I input);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step definition identified by {@code stepName} must not have an input parameter. In
       * other words, the next step call (or asyncCall) must have been defined with a {@link
       * Supplier} function.
       *
       * @param stepName The step name that should be executed next.
       * @deprecated use {@link Builder#transitionTo(akka.japi.function.Function)} instead.
       */
      @Deprecated
      Transitional transitionTo(String stepName);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step is identified by a method reference that accepts no input parameters.
       *
       * @param methodRef Reference to the step method
       * @param <W> The workflow type containing the step method
       * @return A transitional effect
       */
      <W> Transitional transitionTo(akka.japi.function.Function<W, StepEffect> methodRef);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step is identified by a method reference that accepts an input parameter.
       *
       * @param methodRef Reference to the step method
       * @param <W> The workflow type containing the step method
       * @param <I> The input parameter type for the step
       * @return A builder to provide the input parameter
       */
      <W, I> WithInput<I, Transitional> transitionTo(
          akka.japi.function.Function2<W, I, StepEffect> methodRef);

      /**
       * Finish the workflow execution. After transition to {@code end}, no more transitions are
       * allowed.
       */
      Transitional end();

      /**
       * Finish the workflow execution with a reason description. After transition to {@code end},
       * no more transitions are allowed.
       */
      Transitional end(String reason);

      /**
       * Finish and delete the workflow execution. After transition to {@code delete}, no more
       * transitions are allowed. The actual workflow state deletion is done with a configurable
       * delay to allow downstream consumers to observe that fact.
       */
      Transitional delete();

      /**
       * Finish and delete the workflow execution with a reason description. After transition to
       * {@code delete}, no more transitions are allowed. The actual workflow state deletion is done
       * with a configurable delay to allow downstream consumers to observe that fact.
       */
      Transitional delete(String reason);

      /**
       * Create a message reply.
       *
       * @param replyMessage The payload of the reply.
       * @param <R> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> ReadOnlyEffect<R> reply(R replyMessage);

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> ReadOnlyEffect<R> reply(R message, Metadata metadata);

      /**
       * Create an error reply.
       *
       * @param message The error message.
       * @param <R> The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <R> ReadOnlyEffect<R> error(String message);

      /**
       * Create an error reply. {@link CommandException} will be serialized and sent to the client.
       * It's possible to catch it with try-catch statement or {@link CompletionStage} API when
       * using async {@link ComponentClient} API.
       *
       * @param commandException The command exception to be returned.
       * @param <R> The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <R> ReadOnlyEffect<R> error(CommandException commandException);
    }

    /**
     * A workflow effect type that contains information about the transition to the next step. This
     * could be also a special transition to pause or end the workflow.
     *
     * @deprecated Use {@link Effect.Transitional} instead.
     */
    @Deprecated
    interface TransitionalEffect<T> extends Effect<T> {

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param <R> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message);

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message, Metadata metadata);
    }

    interface Transitional extends TransitionalEffect<Void> {

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param <R> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message);

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message, Metadata metadata);
    }

    interface PersistenceEffectBuilder<T> {

      /** Pause the workflow execution and wait for an external input, e.g. via command handler. */
      Transitional pause();

      /**
       * Pause the workflow execution with a reason description and wait for an external input, e.g.
       * via command handler.
       */
      Transitional pause(String reason);

      /**
       * Pause the workflow execution with advanced configuration options.
       *
       * <p>This method allows pausing the workflow with a timeout and a handler that will be
       * invoked when the timeout expires. The pause can also include an optional reason
       * description.
       *
       * <p>Use the {@link Workflow#pauseSetting(Duration)} method to start building the {@link
       * PauseSettings}.
       *
       * @param pauseSettings Configuration for the pause including timeout duration and handler
       */
      Transitional pause(PauseSettings pauseSettings);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step definition identified by {@code stepName} must have an input parameter of type
       * I. In other words, the next step call (or asyncCall) must have been defined with a {@link
       * Function} that accepts an input parameter of type I.
       *
       * @param stepName The step name that should be executed next.
       * @param input The input param for the next step.
       * @deprecated use {@link PersistenceEffectBuilder#transitionTo(akka.japi.function.Function2)}
       *     instead.
       */
      @Deprecated
      <I> Transitional transitionTo(String stepName, I input);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step definition identified by {@code stepName} must not have an input parameter. In
       * other words, the next step call (or asyncCall) must have been defined with a {@link
       * Supplier}.
       *
       * @param stepName The step name that should be executed next.
       * @deprecated use {@link PersistenceEffectBuilder#transitionTo(akka.japi.function.Function)}
       *     instead.
       */
      @Deprecated
      Transitional transitionTo(String stepName);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step is identified by a method reference that accepts no input parameters.
       *
       * @param lambda Reference to the step method
       * @param <W> The workflow type containing the step method
       * @return A transitional effect
       */
      <W> Transitional transitionTo(akka.japi.function.Function<W, StepEffect> lambda);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step is identified by a method reference that accepts an input parameter.
       *
       * @param lambda Reference to the step method
       * @param <W> The workflow type containing the step method
       * @param <I> The input parameter type for the step
       * @return A builder to provide the input parameter
       */
      <W, I> WithInput<I, Transitional> transitionTo(
          akka.japi.function.Function2<W, I, StepEffect> lambda);

      /**
       * Finish the workflow execution. After transition to {@code end}, no more transitions are
       * allowed.
       */
      Transitional end();

      /**
       * Finish the workflow execution with a reason description. After transition to {@code end},
       * no more transitions are allowed.
       */
      Transitional end(String reason);

      /**
       * Finish and delete the workflow execution. After transition to {@code delete}, no more
       * transitions are allowed. The actual workflow state deletion is done with a configurable
       * delay to allow downstream consumers to observe that fact.
       */
      Transitional delete();

      /**
       * Finish and delete the workflow execution with a reason description. After transition to
       * {@code delete}, no more transitions are allowed. The actual workflow state deletion is done
       * with a configurable delay to allow downstream consumers to observe that fact.
       */
      Transitional delete(String reason);
    }
  }

  public interface StepEffect {

    /**
     * Construct the step effect that is returned by step method.
     *
     * <p>The step effect describes next processing actions, such as updating state and transition
     * to another step.
     *
     * @param <S> The type of the state for this workflow.
     */
    interface Builder<S> {

      PersistenceEffectBuilder updateState(S newState);

      /** Pause the workflow execution and wait for an external input, e.g. via command handler. */
      StepEffect thenPause();

      /**
       * Pause the workflow execution with a reason description and wait for an external input, e.g.
       * via command handler.
       */
      StepEffect thenPause(String reason);

      /**
       * Pause the workflow execution with advanced configuration options.
       *
       * <p>This method allows pausing the workflow with a timeout and a handler that will be
       * invoked when the timeout expires. The pause can also include an optional reason
       * description.
       *
       * <p>Use the {@link Workflow#pauseSetting(Duration)} method to start building the {@link
       * PauseSettings}.
       *
       * @param pauseSettings Configuration for the pause including timeout duration and handler
       */
      StepEffect thenPause(PauseSettings pauseSettings);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step is identified by a method reference that accepts no input parameters.
       *
       * @param stepName Reference to the step method
       * @param <W> The workflow type containing the step method
       * @return A step effect
       */
      <W> StepEffect thenTransitionTo(akka.japi.function.Function<W, StepEffect> stepName);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step is identified by a method reference that accepts an input parameter.
       *
       * @param lambda Reference to the step method
       * @param <W> The workflow type containing the step method
       * @param <I> The input parameter type for the step
       * @return A builder to provide the input parameter
       */
      <W, I> WithInput<I, StepEffect> thenTransitionTo(
          akka.japi.function.Function2<W, I, Workflow.StepEffect> lambda);

      /**
       * Finish the workflow execution. After transition to {@code end}, no more transitions are
       * allowed.
       */
      StepEffect thenEnd();

      /**
       * Finish the workflow execution with a reason description. After transition to {@code end},
       * no more transitions are allowed.
       */
      StepEffect thenEnd(String reason);

      /**
       * Finish and delete the workflow execution. After transition to {@code delete}, no more
       * transitions are allowed. The actual workflow state deletion is done with a configurable
       * delay to allow downstream consumers to observe that fact.
       */
      StepEffect thenDelete();

      /**
       * Finish and delete the workflow execution with a reason description. After transition to
       * {@code delete}, no more transitions are allowed. The actual workflow state deletion is done
       * with a configurable delay to allow downstream consumers to observe that fact.
       */
      StepEffect thenDelete(String reason);
    }

    interface PersistenceEffectBuilder {

      /** Pause the workflow execution and wait for an external input, e.g. via command handler. */
      StepEffect thenPause();

      /**
       * Pause the workflow execution with a reason description and wait for an external input, e.g.
       * via command handler.
       */
      StepEffect thenPause(String reason);

      /**
       * Pause the workflow execution with advanced configuration options.
       *
       * <p>This method allows pausing the workflow with a timeout and a handler that will be
       * invoked when the timeout expires. The pause can also include an optional reason
       * description.
       *
       * <p>Use the {@link Workflow#pauseSetting(Duration)} method to start building the {@link
       * PauseSettings}.
       *
       * @param pauseSettings Configuration for the pause including timeout duration and handler
       */
      StepEffect thenPause(PauseSettings pauseSettings);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step is identified by a method reference that accepts no input parameters.
       *
       * @param methodRef Reference to the step method
       * @param <W> The workflow type containing the step method
       * @return A step effect
       */
      <W> StepEffect thenTransitionTo(
          akka.japi.function.Function<W, Workflow.StepEffect> methodRef);

      /**
       * Defines the next step to which the workflow should transition to.
       *
       * <p>The step is identified by a method reference that accepts an input parameter.
       *
       * @param methodRef Reference to the step method
       * @param <W> The workflow type containing the step method
       * @param <I> The input parameter type for the step
       * @return A builder to provide the input parameter
       */
      <W, I> WithInput<I, StepEffect> thenTransitionTo(
          akka.japi.function.Function2<W, I, Workflow.StepEffect> methodRef);

      /**
       * Finish the workflow execution. After transition to {@code end}, no more transitions are
       * allowed.
       */
      StepEffect thenEnd();

      /**
       * Finish the workflow execution with a reason description. After transition to {@code end},
       * no more transitions are allowed.
       */
      StepEffect thenEnd(String reason);

      /**
       * Finish and delete the workflow execution. After transition to {@code delete}, no more
       * transitions are allowed. The actual workflow state deletion is done with a configurable
       * delay to allow downstream consumers to observe that fact.
       */
      StepEffect thenDelete();

      /**
       * Finish and delete the workflow execution with a reason description. After transition to
       * {@code delete}, no more transitions are allowed. The actual workflow state deletion is done
       * with a configurable delay to allow downstream consumers to observe that fact.
       */
      StepEffect thenDelete(String reason);
    }
  }

  /**
   * Represents an operation that accepts an input of type I and produces a result of type R. This
   * is used by internal builders accepting {@link akka.japi.function.Function2}
   */
  public interface WithInput<I, R> {
    R withInput(I input);
  }

  /** An effect that is known to be read-only and does not update the state of the entity. */
  public interface ReadOnlyEffect<T> extends Effect<T> {}

  /**
   * @deprecated use {@link WorkflowSettings} instead
   */
  @Deprecated
  public static class WorkflowDef<S> {

    private final boolean legacy;
    private final List<Step> steps = new ArrayList<>();
    private final List<StepSettings> stepSettings = new ArrayList<>();
    private final Set<String> uniqueNames = new HashSet<>();
    private Optional<Duration> workflowTimeout = Optional.empty();
    private Optional<String> failoverStepName = Optional.empty();
    private Optional<Object> failoverStepInput = Optional.empty();
    private Optional<MaxRetries> failoverMaxRetries = Optional.empty();
    private Optional<Duration> stepTimeout = Optional.empty();
    private Optional<RecoverStrategy<?>> stepRecoverStrategy = Optional.empty();

    private WorkflowDef(boolean legacy) {
      this.legacy = legacy;
    }

    public Optional<Step> findByName(String name) {
      return steps.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    /**
     * Add step to workflow definition. Step name must be unique.
     *
     * @param step A workflow step
     */
    public WorkflowDef<S> addStep(Step step) {
      addStepWithValidation(step);
      if (step.timeout().isPresent()) {
        stepSettings.add(new StepSettings(step.name(), step.timeout(), Optional.empty()));
      }
      return this;
    }

    /**
     * Add step to workflow definition with a dedicated {@link RecoverStrategy}. Step name must be
     * unique.
     *
     * @param step A workflow step
     * @param recoverStrategy A Step recovery strategy
     */
    public WorkflowDef<S> addStep(Step step, RecoverStrategy<?> recoverStrategy) {
      addStepWithValidation(step);
      stepSettings.add(new StepSettings(step.name(), step.timeout(), Optional.of(recoverStrategy)));
      return this;
    }

    private void addStepWithValidation(Step step) {
      if (uniqueNames.contains(step.name()))
        throw new IllegalArgumentException(
            "Name '" + step.name() + "' is already in use by another step in this workflow");

      this.steps.add(step);
      this.uniqueNames.add(step.name());
    }

    /**
     * Define a timeout for the duration of the entire workflow. When the timeout expires, the
     * workflow is finished and no transitions are allowed.
     *
     * @param timeout Timeout duration
     */
    public WorkflowDef<S> timeout(Duration timeout) {
      this.workflowTimeout = Optional.of(timeout);
      return this;
    }

    /**
     * Define a failover step name after workflow timeout. Note that recover strategy for this step
     * can set only the number of max retries.
     *
     * @param stepName A failover step name
     * @param maxRetries A recovery strategy for failover step.
     */
    public WorkflowDef<S> failoverTo(String stepName, MaxRetries maxRetries) {
      this.failoverStepName = Optional.of(stepName);
      this.failoverMaxRetries = Optional.of(maxRetries);
      return this;
    }

    /**
     * Define a failover step name after workflow timeout. Note that recover strategy for this step
     * can set only the number of max retries.
     *
     * @param stepName A failover step name
     * @param stepInput A failover step input
     * @param maxRetries A recovery strategy for failover step.
     */
    public <I> WorkflowDef<S> failoverTo(String stepName, I stepInput, MaxRetries maxRetries) {
      this.failoverStepName = Optional.of(stepName);
      this.failoverStepInput = Optional.of(stepInput);
      this.failoverMaxRetries = Optional.of(maxRetries);
      return this;
    }

    /**
     * Define a default step timeout. If not set, a default value of 5 seconds is used. Can be
     * overridden with step configuration.
     */
    public WorkflowDef<S> defaultStepTimeout(Duration timeout) {
      this.stepTimeout = Optional.of(timeout);
      return this;
    }

    /** Define a default step recovery strategy. Can be overridden with step configuration. */
    public WorkflowDef<S> defaultStepRecoverStrategy(RecoverStrategy<?> recoverStrategy) {
      this.stepRecoverStrategy = Optional.of(recoverStrategy);
      return this;
    }

    public Optional<Duration> getWorkflowTimeout() {
      return workflowTimeout;
    }

    public Optional<Duration> getStepTimeout() {
      return stepTimeout;
    }

    public Optional<RecoverStrategy<?>> getStepRecoverStrategy() {
      return stepRecoverStrategy;
    }

    public List<Step> getSteps() {
      return steps;
    }

    public List<StepSettings> getStepSettings() {
      return stepSettings;
    }

    public Optional<String> getFailoverStepName() {
      return failoverStepName;
    }

    public Optional<?> getFailoverStepInput() {
      return failoverStepInput;
    }

    public Optional<MaxRetries> getFailoverMaxRetries() {
      return failoverMaxRetries;
    }
  }

  public sealed interface CommandHandler {
    record NoArgCommandHandler(akka.japi.function.Function<?, Effect<Done>> handler)
        implements CommandHandler {}

    record OneArgCommandHandler(
        akka.japi.function.Function2<?, ?, Effect<Done>> handler, Object input)
        implements CommandHandler {}
  }

  public sealed interface StepHandler {
    record UnaryStepHandler(akka.japi.function.Function<?, StepEffect> handler)
        implements StepHandler {}

    record BinaryStepHandler(akka.japi.function.Function2<?, ?, StepEffect> handler, Object input)
        implements StepHandler {}
  }

  public record PauseSettings(
      Optional<String> reason, Duration timeout, CommandHandler timeoutHandler) {}

  public record PauseSettingsBuilder(Duration timeout, Optional<String> reason) {

    public PauseSettingsBuilder(Duration timeout) {
      this(timeout, Optional.empty());
    }

    /**
     * Specify pause reason
     *
     * @param reason pause reason
     */
    public PauseSettingsBuilder reason(String reason) {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("reason is null or blank");
      }
      return new PauseSettingsBuilder(timeout, Optional.of(reason));
    }

    /**
     * Configures the handler to be invoked when the pause timeout expires.
     *
     * <p>The pause timeout handler is a regular workflow command handler that returns a {@link
     * Effect<Done>}.
     *
     * <p>The handler function should be specified as a Java method reference, e.g.
     * MyWorkflow::pauseTimeoutHandler
     */
    public <W> PauseSettings timeoutHandler(
        akka.japi.function.Function<W, Effect<Done>> timeoutHandler) {
      return new PauseSettings(
          reason, timeout, new CommandHandler.NoArgCommandHandler(timeoutHandler));
    }

    /**
     * Configures the handler to be invoked when the pause timeout expires.
     *
     * <p>The pause timeout handler is a regular workflow command handler that returns a {@link
     * Effect<Done>}.
     *
     * <p>The handler function should be specified as a Java method reference, e.g.
     * MyWorkflow::pauseTimeoutHandler
     *
     * <p>This overload allows you to pass additional input to the timeout handler, when additional
     * context or data that should be captured at the time of pause configuration.
     */
    public <W, I> PauseSettings timeoutHandler(
        akka.japi.function.Function2<W, I, Effect<Done>> timeoutHandler, I input) {
      return new PauseSettings(
          reason, timeout, new CommandHandler.OneArgCommandHandler(timeoutHandler, input));
    }
  }

  /**
   * Creates a builder for configuring advanced pause settings with timeout and handler.
   *
   * <p>This method is used to configure a workflow or step pause with a timeout duration and a
   * handler that will be invoked when the timeout expires. The builder allows you to optionally
   * specify a reason for the pause and configure the timeout handler.
   */
  public PauseSettingsBuilder pauseSetting(Duration timeout) {
    return new PauseSettingsBuilder(timeout);
  }

  public sealed interface WorkflowSettings {

    static WorkflowSettingsBuilder builder() {
      return WorkflowSettingsBuilder.newBuilder();
    }

    Optional<Duration> defaultStepTimeout();

    Optional<RecoverStrategy<?>> defaultStepRecoverStrategy();

    List<StepSettings> stepSettings();

    Optional<Duration> passivationDelay();

    Optional<Duration> workflowTimeout();

    Optional<RecoverStrategy<?>> workflowRecoverStrategy();
  }

  /** INTERNAL API */
  @InternalApi
  public sealed interface LegacyWorkflowTimeout {
    Optional<Duration> workflowTimeout();

    Optional<RecoverStrategy<?>> workflowRecoverStrategy();
  }

  private record LegacyWorkflowSettings<S>(WorkflowDef<S> legacyDefinition)
      implements WorkflowSettings, LegacyWorkflowTimeout {

    @Override
    public Optional<Duration> workflowTimeout() {
      return legacyDefinition.getWorkflowTimeout();
    }

    @Override
    public Optional<RecoverStrategy<?>> workflowRecoverStrategy() {
      return legacyDefinition
          .getFailoverStepName()
          .map(
              stepName -> {
                // when failoverStepName exists, maxRetries must exist
                var maxRetries = legacyDefinition.getFailoverMaxRetries().get().maxRetries;
                var failoverStepInput = legacyDefinition.getFailoverStepInput();
                return new RecoverStrategy<>(
                    maxRetries, stepName, failoverStepInput, Optional.empty());
              });
    }

    @Override
    public Optional<RecoverStrategy<?>> defaultStepRecoverStrategy() {
      return legacyDefinition.getStepRecoverStrategy();
    }

    @Override
    public List<StepSettings> stepSettings() {
      return legacyDefinition.getStepSettings();
    }

    @Override
    public Optional<Duration> passivationDelay() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> defaultStepTimeout() {
      return legacyDefinition.getStepTimeout();
    }
  }

  private record WorkflowSettingsImpl(
      Optional<Duration> defaultStepTimeout,
      Optional<RecoverStrategy<?>> defaultStepRecoverStrategy,
      Map<String, StepSettings> stepSettingsMap,
      Optional<Duration> passivationDelay,
      Optional<Duration> workflowTimeout,
      Optional<RecoverStrategy<?>> workflowRecoverStrategy)
      implements WorkflowSettings {

    @Override
    public List<StepSettings> stepSettings() {
      return stepSettingsMap.values().stream().toList();
    }
  }

  public static class WorkflowSettingsBuilder {

    private final Optional<Duration> defaultStepTimeout;
    private final Optional<RecoverStrategy<?>> defaultStepRecoverStrategy;
    private final Map<String, StepSettings> stepSettingsMap;
    private final Optional<Duration> passivationDelay;
    private final Optional<Duration> workflowTimeout;
    private final Optional<RecoverStrategy<?>> workflowRecoveryStrategy;

    public WorkflowSettingsBuilder(
        Optional<Duration> defaultStepTimeout,
        Optional<RecoverStrategy<?>> defaultStepRecoverStrategy,
        Map<String, StepSettings> stepSettingsMap,
        Optional<Duration> passivationDelay,
        Optional<Duration> workflowTimeout,
        Optional<RecoverStrategy<?>> workflowRecoveryStrategy) {
      this.defaultStepTimeout = defaultStepTimeout;
      this.defaultStepRecoverStrategy = defaultStepRecoverStrategy;
      this.stepSettingsMap = stepSettingsMap;
      this.passivationDelay = passivationDelay;
      this.workflowTimeout = workflowTimeout;
      this.workflowRecoveryStrategy = workflowRecoveryStrategy;
    }

    public static WorkflowSettingsBuilder newBuilder() {
      return new WorkflowSettingsBuilder(
          Optional.empty(),
          Optional.empty(),
          Map.of(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }

    /**
     * Define a timeout for the duration of the entire workflow. When the timeout expires, the
     * workflow is finished and no transitions are allowed.
     *
     * @param timeout Timeout duration
     */
    public WorkflowSettingsBuilder timeout(Duration timeout) {
      return new WorkflowSettingsBuilder(
          defaultStepTimeout,
          defaultStepRecoverStrategy,
          stepSettingsMap,
          passivationDelay,
          Optional.of(timeout),
          workflowRecoveryStrategy);
    }

    /**
     * Define a timeout for the duration of the entire workflow with a timeout handler step. When
     * the timeout expires, the specified timeout handler step will be executed to handle the
     * timeout gracefully (e.g., cleanup, logging, or compensation). The timeout handler step must
     * end the workflow - no further step transitions are allowed after a global timeout.
     *
     * @param timeout Timeout duration
     * @param timeoutFailoverStep Reference to the timeout handler step method
     */
    public <W> WorkflowSettingsBuilder timeout(
        Duration timeout, akka.japi.function.Function<W, StepEffect> timeoutFailoverStep) {
      var method = MethodRefResolver.resolveMethodRef(timeoutFailoverStep);
      var stepName = WorkflowDescriptor.stepMethodName(method);
      var workflowRecovery =
          new RecoverStrategy<>(
              0,
              stepName,
              Optional.empty(),
              Optional.of(new StepHandler.UnaryStepHandler(timeoutFailoverStep)));
      return new WorkflowSettingsBuilder(
          defaultStepTimeout,
          defaultStepRecoverStrategy,
          stepSettingsMap,
          passivationDelay,
          Optional.of(timeout),
          Optional.of(workflowRecovery));
    }

    /**
     * Define a timeout for the duration of the entire workflow with a timeout handler step that
     * accepts input. When the timeout expires, the specified timeout handler step will be executed
     * with the provided input to handle the timeout gracefully (e.g., cleanup, logging, or
     * compensation). The timeout handler step must end the workflow - no further step transitions
     * are allowed after a global timeout.
     *
     * @param timeout Timeout duration
     * @param timeoutFailoverStep Reference to the timeout handler step method
     * @param input Input parameter to pass to the timeout handler step
     */
    public <W, I> WorkflowSettingsBuilder timeout(
        Duration timeout,
        akka.japi.function.Function2<W, I, StepEffect> timeoutFailoverStep,
        I input) {
      var method = MethodRefResolver.resolveMethodRef(timeoutFailoverStep);
      var stepName = WorkflowDescriptor.stepMethodName(method);
      var workflowRecovery =
          new RecoverStrategy<>(
              0,
              stepName,
              Optional.of(input),
              Optional.of(new StepHandler.BinaryStepHandler(timeoutFailoverStep, input)));
      return new WorkflowSettingsBuilder(
          defaultStepTimeout,
          defaultStepRecoverStrategy,
          stepSettingsMap,
          passivationDelay,
          Optional.of(timeout),
          Optional.of(workflowRecovery));
    }

    /** Define a default timeout duration for all steps. Can be overridden per step. */
    public WorkflowSettingsBuilder defaultStepTimeout(Duration timeout) {
      return new WorkflowSettingsBuilder(
          Optional.of(timeout),
          defaultStepRecoverStrategy,
          stepSettingsMap,
          passivationDelay,
          workflowTimeout,
          workflowRecoveryStrategy);
    }

    /** Define a default recovery strategy for all steps. Can be overridden per step. */
    @Deprecated
    public WorkflowSettingsBuilder defaultStepRecovery(RecoverStrategy<?> recoverStrategy) {
      return new WorkflowSettingsBuilder(
          defaultStepTimeout,
          Optional.of(recoverStrategy),
          stepSettingsMap,
          passivationDelay,
          workflowTimeout,
          workflowRecoveryStrategy);
    }

    /**
     * Configure a specific step with a timeout.
     *
     * @param lambda Reference to the step method
     * @param timeout Timeout duration for this step
     */
    public <W> WorkflowSettingsBuilder stepTimeout(
        akka.japi.function.Function<W, StepEffect> lambda, Duration timeout) {
      var method = MethodRefResolver.resolveMethodRef(lambda);
      var stepName = WorkflowDescriptor.stepMethodName(method);
      return addStepTimeout(stepName, timeout);
    }

    /**
     * Configure a specific step with a timeout.
     *
     * @param lambda Reference to the step method
     * @param timeout Timeout duration for this step
     */
    public <W, I> WorkflowSettingsBuilder stepTimeout(
        akka.japi.function.Function2<W, I, StepEffect> lambda, Duration timeout) {
      var method = MethodRefResolver.resolveMethodRef(lambda);
      var stepName = WorkflowDescriptor.stepMethodName(method);
      return addStepTimeout(stepName, timeout);
    }

    /**
     * Configure a specific step with a recovery strategy.
     *
     * @param lambda Reference to the step method
     * @param recovery Recovery strategy for this step
     */
    public <W> WorkflowSettingsBuilder stepRecovery(
        akka.japi.function.Function<W, StepEffect> lambda, RecoverStrategy<?> recovery) {
      var method = MethodRefResolver.resolveMethodRef(lambda);
      var stepName = WorkflowDescriptor.stepMethodName(method);
      return addStepRecovery(stepName, lambda, recovery);
    }

    /**
     * Configure a specific step with a recovery strategy.
     *
     * @param lambda Reference to the step method
     * @param recovery Recovery strategy for this step
     */
    public <W, I> WorkflowSettingsBuilder stepRecovery(
        akka.japi.function.Function2<W, I, StepEffect> lambda, RecoverStrategy<?> recovery) {
      var method = MethodRefResolver.resolveMethodRef(lambda);
      var stepName = WorkflowDescriptor.stepMethodName(method);
      return addStepRecovery(stepName, lambda, recovery);
    }

    /**
     * A paused (or finished) workflow will be kept in memory for the given delay before being
     * passivated. This improves the performance of resuming such a workflow because it doesn't have
     * to be recovered from the storage.
     */
    public <W, I> WorkflowSettingsBuilder passivationDelay(Duration delay) {
      return new WorkflowSettingsBuilder(
          defaultStepTimeout,
          defaultStepRecoverStrategy,
          stepSettingsMap,
          Optional.ofNullable(delay),
          workflowTimeout,
          workflowRecoveryStrategy);
    }

    private WorkflowSettingsBuilder addStepTimeout(String stepName, Duration timeout) {
      var settings = stepSettingsMap.getOrDefault(stepName, StepSettings.empty(stepName));
      var updatedSettings = settings.withTimeout(timeout);
      return updateStepSettings(updatedSettings);
    }

    private WorkflowSettingsBuilder addStepRecovery(
        String stepName, Object stepLambda, RecoverStrategy<?> recovery) {
      var settings = stepSettingsMap.getOrDefault(stepName, StepSettings.empty(stepName));
      var updatedSettings = settings.withRecovery(recovery, stepLambda);
      return updateStepSettings(updatedSettings);
    }

    private WorkflowSettingsBuilder updateStepSettings(StepSettings settings) {
      var mutableMap = new HashMap<>(stepSettingsMap);
      mutableMap.put(settings.stepName(), settings);
      return new WorkflowSettingsBuilder(
          defaultStepTimeout,
          defaultStepRecoverStrategy,
          Map.copyOf(mutableMap),
          passivationDelay,
          workflowTimeout,
          workflowRecoveryStrategy);
    }

    /**
     * Creates the final workflow configuration from this builder.
     *
     * @return The complete workflow configuration
     */
    public WorkflowSettings build() {
      return new WorkflowSettingsImpl(
          defaultStepTimeout,
          defaultStepRecoverStrategy,
          stepSettingsMap,
          passivationDelay,
          workflowTimeout,
          workflowRecoveryStrategy);
    }
  }

  /**
   * @deprecated use {@link Workflow#settings()} instead
   */
  @Deprecated
  public WorkflowDef<S> workflow() {
    return new WorkflowDef<>(true);
  }

  /**
   * @deprecated use methods returning {@link StepEffect} instead.
   */
  @Deprecated
  public interface Step {
    String name();

    Optional<Duration> timeout();
  }

  public record StepMethod(String methodName, Method javaMethod) {
    public StepEffect invoke(Object instance, Object... args) {
      try {
        javaMethod.setAccessible(true);
        return (StepEffect) javaMethod.invoke(instance, args);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        throw ErrorHandling.unwrapInvocationTargetException(e);
      }
    }
  }

  /**
   * @deprecated use methods returning {@link StepEffect} instead.
   */
  @Deprecated
  public static final class CallStep<CallInput, CallOutput, FailoverInput> implements Step {

    private final String _name;
    public final Function<CallInput, CallOutput> callFunc;
    public final Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc;
    public final Class<CallInput> callInputClass;
    public final Class<CallOutput> transitionInputClass;
    private Optional<Duration> _timeout = Optional.empty();

    /** Not for direct user construction, instances are created through the workflow DSL */
    public CallStep(
        String name,
        Class<CallInput> callInputClass,
        Function<CallInput, CallOutput> callFunc,
        Class<CallOutput> transitionInputClass,
        Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc) {
      _name = name;
      this.callInputClass = callInputClass;
      this.callFunc = callFunc;
      this.transitionInputClass = transitionInputClass;
      this.transitionFunc = transitionFunc;
    }

    @Override
    public String name() {
      return this._name;
    }

    @Override
    public Optional<Duration> timeout() {
      return this._timeout;
    }

    /** Define a step timeout. */
    public CallStep<CallInput, CallOutput, FailoverInput> timeout(Duration timeout) {
      this._timeout = Optional.of(timeout);
      return this;
    }
  }

  /**
   * @deprecated use methods returning {@link StepEffect} instead.
   */
  @Deprecated
  public static final class RunnableStep implements Step {

    private final String _name;
    public final Runnable runnable;
    public final Supplier<Effect.TransitionalEffect<Void>> transitionFunc;
    private Optional<Duration> _timeout = Optional.empty();

    /** Not for direct user construction, instances are created through the workflow DSL */
    public RunnableStep(
        String name, Runnable runnable, Supplier<Effect.TransitionalEffect<Void>> transitionFunc) {
      _name = name;
      this.runnable = runnable;
      this.transitionFunc = transitionFunc;
    }

    @Override
    public String name() {
      return this._name;
    }

    @Override
    public Optional<Duration> timeout() {
      return this._timeout;
    }

    /** Define a step timeout. */
    public RunnableStep timeout(Duration timeout) {
      this._timeout = Optional.of(timeout);
      return this;
    }
  }

  /**
   * @deprecated use methods returning {@link StepEffect} instead.
   */
  @Deprecated
  public static class AsyncCallStep<CallInput, CallOutput, FailoverInput> implements Step {

    private final String _name;
    public final Function<CallInput, CompletionStage<CallOutput>> callFunc;
    public final Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc;
    public final Class<CallInput> callInputClass;
    public final Class<CallOutput> transitionInputClass;
    private Optional<Duration> _timeout = Optional.empty();

    /** Not for direct user construction, instances are created through the workflow DSL */
    public AsyncCallStep(
        String name,
        Class<CallInput> callInputClass,
        Function<CallInput, CompletionStage<CallOutput>> callFunc,
        Class<CallOutput> transitionInputClass,
        Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc) {
      _name = name;
      this.callInputClass = callInputClass;
      this.callFunc = callFunc;
      this.transitionInputClass = transitionInputClass;
      this.transitionFunc = transitionFunc;
    }

    @Override
    public String name() {
      return this._name;
    }

    @Override
    public Optional<Duration> timeout() {
      return this._timeout;
    }

    /** Define a step timeout. */
    public AsyncCallStep<CallInput, CallOutput, FailoverInput> timeout(Duration timeout) {
      this._timeout = Optional.of(timeout);
      return this;
    }
  }

  public record StepSettings(
      String stepName,
      Optional<Duration> timeout,
      Optional<RecoverStrategy<?>> recovery,
      Optional<Object> stepLambda) {

    @Deprecated() // TODO
    StepSettings(
        String stepName, Optional<Duration> timeout, Optional<RecoverStrategy<?>> recovery) {
      this(stepName, timeout, recovery, Optional.empty());
    }

    public static StepSettings empty(String name) {
      return new StepSettings(name, Optional.empty(), Optional.empty());
    }

    public StepSettings withTimeout(Duration timeout) {
      return new StepSettings(stepName, Optional.of(timeout), recovery);
    }

    public StepSettings withRecovery(RecoverStrategy<?> recovery, Object stepLambda) {
      return new StepSettings(stepName, timeout, Optional.of(recovery), Optional.of(stepLambda));
    }
  }

  /**
   * Starts defining a recover strategy for the workflow or a specific step.
   *
   * @param maxRetries number of retries before giving up.
   * @return MaxRetries strategy.
   */
  public MaxRetries maxRetries(int maxRetries) {
    return RecoverStrategy.maxRetries(maxRetries);
  }

  public record RecoverStrategy<T>(
      int maxRetries,
      String failoverStepName,
      Optional<T> failoverStepInput,
      Optional<StepHandler> stepHandler) {

    public record RecoveryInput<I>(
        int maxRetries, String stepName, akka.japi.function.Function2<?, I, StepEffect> lambda)
        implements WithInput<I, RecoverStrategy<I>> {
      public RecoverStrategy<I> withInput(I input) {
        return new RecoverStrategy<>(
            maxRetries,
            stepName,
            Optional.of(input),
            Optional.of(new StepHandler.BinaryStepHandler(lambda, input)));
      }
    }

    /** Retry strategy without failover configuration */
    public record MaxRetries(int maxRetries) {

      /**
       * @deprecated use {@link MaxRetries#failoverTo(akka.japi.function.Function)} instead.
       */
      @Deprecated
      public RecoverStrategy<?> failoverTo(String stepName) {
        return new RecoverStrategy<>(
            maxRetries, stepName, Optional.<Void>empty(), Optional.empty());
      }

      /**
       * Once max retries are exceeded, transition to a given step method.
       *
       * @param lambda Reference to the step method to transition to
       * @param <W> The workflow type containing the step method
       * @return A recovery strategy transitioning to the specified step
       */
      public <W> RecoverStrategy<Void> failoverTo(
          akka.japi.function.Function<W, StepEffect> lambda) {
        var method = MethodRefResolver.resolveMethodRef(lambda);
        var stepName = WorkflowDescriptor.stepMethodName(method);
        return new RecoverStrategy<>(
            maxRetries,
            stepName,
            Optional.empty(),
            Optional.of(new StepHandler.UnaryStepHandler(lambda)));
      }

      /**
       * @deprecated use {@link MaxRetries#failoverTo(akka.japi.function.Function2)} instead.
       */
      @Deprecated
      public <T> RecoverStrategy<T> failoverTo(String stepName, T input) {
        return new RecoverStrategy<>(maxRetries, stepName, Optional.of(input), Optional.empty());
      }

      /**
       * Once max retries are exceeded, transition to a given step method with input parameter.
       *
       * @param lambda Reference to the step method to transition to
       * @param <W> The workflow type containing the step method
       * @param <I> The input parameter type for the step
       * @return A builder to provide the input parameter for the recovery strategy
       */
      public <W, I> RecoveryInput<I> failoverTo(
          akka.japi.function.Function2<W, I, StepEffect> lambda) {
        var method = MethodRefResolver.resolveMethodRef(lambda);
        var stepName = WorkflowDescriptor.stepMethodName(method);
        return new RecoveryInput<>(maxRetries, stepName, lambda);
      }
    }

    /**
     * Set the number of retries for a failed step, {@code maxRetries} equals 0 means that the step
     * won't retry in case of failure.
     */
    public static MaxRetries maxRetries(int maxRetries) {
      return new MaxRetries(maxRetries);
    }

    /**
     * @deprecated use {@link RecoverStrategy#failoverTo(akka.japi.function.Function)} instead.
     */
    @Deprecated
    public static RecoverStrategy<?> failoverTo(String stepName) {
      return new RecoverStrategy<>(0, stepName, Optional.<Void>empty(), Optional.empty());
    }

    /**
     * In case of a step fails, don't retry but transition to a given step method.
     *
     * @param lambda Reference to the step method to transition to
     * @param <W> The workflow type containing the step method
     * @return A recovery strategy transitioning to the specified step
     */
    public static <W> RecoverStrategy<Void> failoverTo(
        akka.japi.function.Function<W, StepEffect> lambda) {
      var method = MethodRefResolver.resolveMethodRef(lambda);
      var stepName = WorkflowDescriptor.stepMethodName(method);
      return new RecoverStrategy<>(
          0, stepName, Optional.empty(), Optional.of(new StepHandler.UnaryStepHandler(lambda)));
    }

    /**
     * @deprecated use {@link RecoverStrategy#failoverTo(akka.japi.function.Function2)} instead.
     */
    @Deprecated
    public static <T> RecoverStrategy<T> failoverTo(String stepName, T input) {
      return new RecoverStrategy<>(0, stepName, Optional.of(input), Optional.empty());
    }

    /**
     * In case of a step fails, don't retry but transition to a given step method with input
     * parameter.
     *
     * @param lambda Reference to the step method to transition to
     * @param <W> The workflow type containing the step method
     * @param <I> The input parameter type for the step
     * @return A builder to provide the input parameter for the recovery strategy
     */
    public static <W, I> RecoveryInput<I> failoverTo(
        akka.japi.function.Function2<W, I, StepEffect> lambda) {
      var method = MethodRefResolver.resolveMethodRef(lambda);
      var stepName = WorkflowDescriptor.stepMethodName(method);
      return new RecoveryInput<>(0, stepName, lambda);
    }
  }
}
