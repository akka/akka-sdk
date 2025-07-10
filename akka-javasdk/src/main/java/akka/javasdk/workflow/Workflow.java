/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.workflow;

import akka.annotation.InternalApi;
import akka.javasdk.Metadata;
import akka.javasdk.impl.workflow.WorkflowEffects;
import akka.javasdk.timer.TimerScheduler;
import akka.javasdk.workflow.Workflow.RecoverStrategy.MaxRetries;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * <p>Workflow methods that handle incoming commands should return an {@link
 * Workflow.Effect} describing the next processing actions.
 *
 * <p>
 * Concrete classes can accept the following types to the constructor:
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient}</li>
 *   <li>{@link akka.javasdk.http.HttpClientProvider}</li>
 *   <li>{@link akka.javasdk.timer.TimerScheduler}</li>
 *   <li>{@link akka.stream.Materializer}</li>
 *   <li>{@link com.typesafe.config.Config}</li>
 *   <li>{@link akka.javasdk.workflow.WorkflowContext}</li>
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup</li>
 * </ul>
 * <p>
 * Concrete class must be annotated with {@link akka.javasdk.annotations.ComponentId}.
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
   */
  public StepBuilder step(String name) {
    return new StepBuilder(name);
  }

  /**
   * Returns the initial empty state object. This object will be passed into the
   * command and step handlers, until a new state replaces it.
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
    return commandContext.orElseThrow(() -> new IllegalStateException("CommandContext is only available when handling a command."));
  }



  /**
   * Returns a {@link TimerScheduler} that can be used to schedule further in time.
   */
  public final TimerScheduler timers() {
    return timerScheduler.orElseThrow(() -> new IllegalStateException("Timers can only be scheduled or cancelled when handling a command or running a step action."));
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
    else throw new IllegalStateException("Current state is only available when handling a command.");
  }

  /**
   * Returns true if the entity has been deleted.
   */
  protected boolean isDeleted() {
    return deleted;
  }

  /**
   * INTERNAL API
   * @hidden
   */
  @InternalApi
  public void _internalSetup(S state, CommandContext context, TimerScheduler timerScheduler, boolean deleted) {
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

  public Settings settings() {
    var def = definition();
    if (def.legacy) return new LegacySettings<>(def);
    else return Settings.builder().build();
  }



  protected final Effect.Builder<S> effects() {
    return WorkflowEffects.createEffectBuilder();
  }

  protected final StepEffect.Builder<S> stepEffects() {
    return WorkflowEffects.createStepEffectBuilder();
  }

  /**
   * An Effect is a description of what the runtime needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to the runtime, which will process
   * the instructions on your behalf.
   * <p>
   * Each component defines its own effects, which are a set of predefined
   * operations that match the capabilities of that component.
   * <p>
   * A Workflow Effect can either:
   * <p>
   * <ul>
   *   <li>update the state of the workflow
   *   <li>define the next step to be executed (transition)
   *   <li>pause the workflow
   *   <li>end the workflow
   *   <li>fail the step or reject a command by returning an error
   *   <li>reply to incoming commands
   * </ul>
   * <p>
   * <p>
   *  @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the command handler or a step transition.
     * <p>
     * The effect describes next processing actions, such as updating state, transition to another step
     * and sending a reply.
     *
     * @param <S> The type of the state for this workflow.
     */
    interface Builder<S> {

      PersistenceEffectBuilder<S> updateState(S newState);

      /**
       * Pause the workflow execution and wait for an external input, e.g. via command handler.
       */
      Transitional pause();

      /**
       * Defines the next step to which the workflow should transition to.
       * <p>
       * The step definition identified by {@code stepName} must have an input parameter of type I.
       * In other words, the next step call (or asyncCall) must have been defined with a {@link Function} that
       * accepts an input parameter of type I.
       *
       * @param stepName The step name that should be executed next.
       * @param input    The input param for the next step.
       * @deprecated use {@link Builder#transitionTo(akka.japi.function.Function2)} instead.
       */
      @Deprecated
      <I> Transitional transitionTo(String stepName, I input);

      /**
       * Defines the next step to which the workflow should transition to.
       * <p>
       * The step definition identified by {@code stepName} must not have an input parameter.
       * In other words, the next step call (or asyncCall) must have been defined with a {@link Supplier} function.
       *
       * @param stepName The step name that should be executed next.
       * @deprecated use {@link Builder#transitionTo(akka.japi.function.Function)} instead.
       */
      @Deprecated
      Transitional transitionTo(String stepName);

      <W> Transitional transitionTo(akka.japi.function.Function <W, StepEffect> methodRef);

      <W, I> CallWithInput<I, Transitional> transitionTo(akka.japi.function.Function2<W, I, StepEffect> methodRef);

      /**
       * Finish the workflow execution.
       * After transition to {@code end}, no more transitions are allowed.
       */
      Transitional end();

      /**
       * Finish and delete the workflow execution.
       * After transition to {@code delete}, no more transitions are allowed.
       * The actual workflow state deletion is done with a configurable delay to allow downstream consumers to observe that fact.
       */
      Transitional delete();

      /**
       * Create a message reply.
       *
       * @param replyMessage The payload of the reply.
       * @param <R>          The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> ReadOnlyEffect<R> reply(R replyMessage);


      /**
       * Reply after for example {@code updateState}.
       *
       * @param message  The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R>      The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> ReadOnlyEffect<R> reply(R message, Metadata metadata);

      /**
       * Create an error reply.
       *
       * @param description The description of the error.
       * @param <R>         The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <R> ReadOnlyEffect<R> error(String description);

    }

    /**
     * A workflow effect type that contains information about the transition to the next step.
     * This could be also a special transition to pause or end the workflow.
     */
    interface TransitionalEffect<T> extends Effect<T> {

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param <R>     The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message);

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message  The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R>      The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message, Metadata metadata);
    }

    interface Transitional extends TransitionalEffect<Void> {

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message The payload of the reply.
       * @param <R>     The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message);

      /**
       * Reply after for example {@code updateState}.
       *
       * @param message  The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <R>      The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <R> Effect<R> thenReply(R message, Metadata metadata);
    }


    interface PersistenceEffectBuilder<T> {

      /**
       * Pause the workflow execution and wait for an external input, e.g. via command handler.
       */
      Transitional pause();

      /**
       * Defines the next step to which the workflow should transition to.
       * <p>
       * The step definition identified by {@code stepName} must have an input parameter of type I.
       * In other words, the next step call (or asyncCall) must have been defined with a {@link Function} that
       * accepts an input parameter of type I.
       *
       * @param stepName The step name that should be executed next.
       * @param input    The input param for the next step.
       * @deprecated use {@link PersistenceEffectBuilder#transitionTo(akka.japi.function.Function2)} instead.
       */
      @Deprecated
      <I> Transitional transitionTo(String stepName, I input);

      /**
       * Defines the next step to which the workflow should transition to.
       * <p>
       * The step definition identified by {@code stepName} must not have an input parameter.
       * In other words, the next step call (or asyncCall) must have been defined with a {@link Supplier}.
       *
       * @param stepName The step name that should be executed next.
       * @deprecated use {@link PersistenceEffectBuilder#transitionTo(akka.japi.function.Function)} instead.
       */
      @Deprecated
      Transitional transitionTo(String stepName);


      <W> Transitional transitionTo(akka.japi.function.Function <W, StepEffect> lambda);

      <W, I> CallWithInput<I, Transitional> transitionTo(akka.japi.function.Function2<W, I, StepEffect> lambda);

      /**
       * Finish the workflow execution.
       * After transition to {@code end}, no more transitions are allowed.
       */
      Transitional end();

      /**
       * Finish and delete the workflow execution.
       * After transition to {@code delete}, no more transitions are allowed.
       * The actual workflow state deletion is done with a configurable delay to allow downstream consumers to observe that fact.
       */
      Transitional delete();
    }

  }

  public interface StepEffect {

    /**
     * Construct the step effect that is returned by step method.
     * <p>
     * The step effect describes next processing actions, such as updating state and transition to another step.
     *
     * @param <S> The type of the state for this workflow.
     */
    interface Builder<S> {

      PersistenceEffectBuilder updateState(S newState);

      /**
       * Pause the workflow execution and wait for an external input, e.g. via command handler.
       */
      StepEffect thenPause();

      /**
       * Defines the next step to which the workflow should transition to.
       */
      <W> StepEffect thenTransitionTo(akka.japi.function.Function <W, StepEffect> stepName);

      <W, I> CallWithInput<I, StepEffect> thenTransitionTo(akka.japi.function.Function2<W, I, Workflow.StepEffect> lambda);

      /**
       * Finish the workflow execution.
       * After transition to {@code end}, no more transitions are allowed.
       */
      StepEffect thenEnd();

      /**
       * Finish and delete the workflow execution.
       * After transition to {@code delete}, no more transitions are allowed.
       * The actual workflow state deletion is done with a configurable delay to allow downstream consumers to observe that fact.
       */
      StepEffect thenDelete();

      StepEffect error(String description);

    }


    interface PersistenceEffectBuilder {

      /**
       * Pause the workflow execution and wait for an external input, e.g. via command handler.
       */
      StepEffect thenPause();

      /**
       * Defines the next step to which the workflow should transition to.
      <I> StepEffect transitionTo(String stepName, I input);

      /**
       * Defines the next step to which the workflow should transition to.
       */
      <W> StepEffect thenTransitionTo(akka.japi.function.Function<W, Workflow.StepEffect> methodRef);


      <W, I> CallWithInput<I, StepEffect> thenTransitionTo(akka.japi.function.Function2<W, I, Workflow.StepEffect> methodRef);

      /**
       * Finish the workflow execution.
       * After transition to {@code end}, no more transitions are allowed.
       */
      StepEffect thenEnd();

      /**
       * Finish and delete the workflow execution.
       * After transition to {@code delete}, no more transitions are allowed.
       * The actual workflow state deletion is done with a configurable delay to allow downstream consumers to observe that fact.
       */
      StepEffect thenDelete();

      StepEffect error(String description);
    }

  }


  public interface CallWithInput<I, R> {
    R withInput(I input);
  }
  /**
   * An effect that is known to be read only and does not update the state of the entity.
   */
  public interface ReadOnlyEffect<T> extends Effect<T> {
  }


  /**
   * @deprecated use {@link Workflow.Settings} instead
   */
  @Deprecated
  public static class WorkflowDef<S> {

    private final boolean legacy;
    final private List<Step> steps = new ArrayList<>();
    final private List<StepConfig> stepConfigs = new ArrayList<>();
    final private Set<String> uniqueNames = new HashSet<>();
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
        stepConfigs.add(new StepConfig(step.name(), step.timeout(), Optional.empty()));
      }
      return this;
    }

    /**
     * Add step to workflow definition with a dedicated {@link RecoverStrategy}. Step name must be unique.
     *
     * @param step            A workflow step
     * @param recoverStrategy A Step recovery strategy
     */
    public WorkflowDef<S> addStep(Step step, RecoverStrategy<?> recoverStrategy) {
      addStepWithValidation(step);
      stepConfigs.add(new StepConfig(step.name(), step.timeout(), Optional.of(recoverStrategy)));
      return this;
    }

    private void addStepWithValidation(Step step) {
      if (uniqueNames.contains(step.name()))
        throw new IllegalArgumentException("Name '" + step.name() + "' is already in use by another step in this workflow");

      this.steps.add(step);
      this.uniqueNames.add(step.name());
    }


    /**
     * Define a timeout for the duration of the entire workflow. When the timeout expires, the workflow is finished and no transitions are allowed.
     *
     * @param timeout Timeout duration
     */
    public WorkflowDef<S> timeout(Duration timeout) {
      this.workflowTimeout = Optional.of(timeout);
      return this;
    }

    /**
     * Define a failover step name after workflow timeout. Note that recover strategy for this step can set only the number of max retries.
     *
     * @param stepName   A failover step name
     * @param maxRetries A recovery strategy for failover step.
     */
    public WorkflowDef<S> failoverTo(String stepName, MaxRetries maxRetries) {
      this.failoverStepName = Optional.of(stepName);
      this.failoverMaxRetries = Optional.of(maxRetries);
      return this;
    }

    /**
     * Define a failover step name after workflow timeout. Note that recover strategy for this step can set only the number of max retries.
     *
     * @param stepName   A failover step name
     * @param stepInput  A failover step input
     * @param maxRetries A recovery strategy for failover step.
     */
    public <I> WorkflowDef<S> failoverTo(String stepName, I stepInput, MaxRetries maxRetries) {
      this.failoverStepName = Optional.of(stepName);
      this.failoverStepInput = Optional.of(stepInput);
      this.failoverMaxRetries = Optional.of(maxRetries);
      return this;
    }

    /**
     * Define a default step timeout. If not set, a default value of 5 seconds is used.
     * Can be overridden with step configuration.
     */
    public WorkflowDef<S> defaultStepTimeout(Duration timeout) {
      this.stepTimeout = Optional.of(timeout);
      return this;
    }

    /**
     * Define a default step recovery strategy. Can be overridden with step configuration.
     */
    public WorkflowDef<S> defaultStepRecoverStrategy(RecoverStrategy recoverStrategy) {
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

    public List<StepConfig> getStepConfigs() {
      return stepConfigs;
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

  public sealed interface Settings {


    static SettingsBuilder builder() {
      return new SettingsBuilder(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
    }

    Optional<Duration> workflowTimeout();

    Optional<Duration> defaultStepTimeout();

    Optional<RecoverStrategy<?>> stepRecoverStrategy();

    Optional<String> failoverStepName();

    Optional<?> failoverStepInput();

    Optional<MaxRetries> failoverMaxRetries();

    List<StepConfig> stepConfigs();

  }

  private record LegacySettings<S>(WorkflowDef<S> legacyDefinition) implements Settings {

    @Override
    public Optional<Duration> workflowTimeout() {
      return legacyDefinition.getWorkflowTimeout();
    }

    @Override
    public Optional<RecoverStrategy<?>> stepRecoverStrategy() {
      return legacyDefinition.getStepRecoverStrategy();
    }

    @Override
    public Optional<String> failoverStepName() {
      return legacyDefinition.getFailoverStepName();
    }

    @Override
    public Optional<?> failoverStepInput() {
      return legacyDefinition.getFailoverStepInput();
    }

    @Override
    public Optional<MaxRetries> failoverMaxRetries() {
      return legacyDefinition.getFailoverMaxRetries();
    }

    @Override
    public Optional<Duration> defaultStepTimeout() {
      return legacyDefinition.getStepTimeout();
    }

    @Override
    public List<StepConfig> stepConfigs() {
      return legacyDefinition.getStepConfigs();
    }

  }

  private record SettingsImpl(
      List<StepConfig> stepConfigs,
      Optional<Duration> workflowTimeout,
      Optional<String> failoverStepName,
      Optional<?> failoverStepInput,
      Optional<MaxRetries> failoverMaxRetries,
      Optional<Duration> defaultStepTimeout,
      Optional<RecoverStrategy<?>> stepRecoverStrategy)
      implements Settings {

        private static List<StepConfig> buildStepConfigs(List<StepMethod> stepMethods) {
          return stepMethods.stream()
              .map(sm -> new StepConfig(sm.methodName, Optional.empty(), Optional.empty()))
              .toList();
        }

    public SettingsImpl(
        Optional<Duration> workflowTimeout,
        Optional<String> failoverStepName,
        Optional<?> failoverStepInput,
        Optional<MaxRetries> failoverMaxRetries,
        Optional<Duration> defaultStepTimeout,
        Optional<RecoverStrategy<?>> stepRecoverStrategy) {
      this(
          List.of(), // buildStepConfigs(stepMethods),
          workflowTimeout,
          failoverStepName,
          failoverStepInput,
          failoverMaxRetries,
          defaultStepTimeout,
          stepRecoverStrategy);
        }

  }

  public record SettingsBuilder(
      Optional<Duration> workflowTimeout,
      Optional<String> failoverStepName,
      Optional<?> failoverStepInput,
      Optional<MaxRetries> failoverMaxRetries,
      Optional<Duration> defaultStepTimeout,
      Optional<RecoverStrategy<?>> stepRecoverStrategy) {


    public SettingsBuilder timeout(Duration duration) {
      return new SettingsBuilder(
          Optional.of(duration),
          failoverStepName,
          failoverStepInput,
          failoverMaxRetries,
          defaultStepTimeout,
          stepRecoverStrategy);
    }

    // TODO: refactor to method ref
    public SettingsBuilder failoverTo(String stepName, MaxRetries maxRetries) {
      return new SettingsBuilder(
          workflowTimeout,
          Optional.of(stepName),
          Optional.empty(),
          Optional.of(maxRetries),
          defaultStepTimeout,
          Optional.empty());
    }

    // TODO: refactor to method ref
    public <I> SettingsBuilder failoverTo(String stepName, I stepInput, MaxRetries maxRetries) {
      return new SettingsBuilder(
          workflowTimeout,
          Optional.of(stepName),
          Optional.of(stepInput),
          Optional.of(maxRetries),
          defaultStepTimeout,
          Optional.empty());
    }

    public SettingsBuilder defaultStepRecoverStrategy(RecoverStrategy<?> recoverStrategy) {
      return new SettingsBuilder(
          workflowTimeout,
          failoverStepName,
          failoverStepInput,
          failoverMaxRetries,
          defaultStepTimeout,
          Optional.of(recoverStrategy));
    }

    public SettingsBuilder defaultStepTimeout(Duration timeout) {
      return new SettingsBuilder(
          workflowTimeout,
          failoverStepName,
          failoverStepInput,
          failoverMaxRetries,
          Optional.of(timeout),
          stepRecoverStrategy);
    }

    public Settings build() {


      return new SettingsImpl(
          workflowTimeout,
          failoverStepName,
          failoverStepInput,
          failoverMaxRetries,
          defaultStepTimeout,
          stepRecoverStrategy);
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

  public record StepMethod(String methodName, Method javaMethod)  {
    public StepEffect invoke(Object instance, Object... args)  {
      try {
        javaMethod.setAccessible(true);
        return (StepEffect) javaMethod.invoke(instance, args);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * @deprecated use methods returning {@link StepEffect} instead.
   */
  @Deprecated
  public static final class CallStep<CallInput, CallOutput, FailoverInput> implements Step {

    final private String _name;
    final public Function<CallInput, CallOutput> callFunc;
    final public Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc;
    final public Class<CallInput> callInputClass;
    final public Class<CallOutput> transitionInputClass;
    private Optional<Duration> _timeout = Optional.empty();

    /**
     * Not for direct user construction, instances are created through the workflow DSL
     */
    public CallStep(String name,
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

    /**
     * Define a step timeout.
     */
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

    final private String _name;
    final public Runnable runnable;
    final public Supplier<Effect.TransitionalEffect<Void>> transitionFunc;
    private Optional<Duration> _timeout = Optional.empty();

    /**
     * Not for direct user construction, instances are created through the workflow DSL
     */
    public RunnableStep(String name,
                        Runnable runnable,
                        Supplier<Effect.TransitionalEffect<Void>> transitionFunc) {
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

    /**
     * Define a step timeout.
     */
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

    final private String _name;
    final public Function<CallInput, CompletionStage<CallOutput>> callFunc;
    final public Function<CallOutput, Effect.TransitionalEffect<Void>> transitionFunc;
    final public Class<CallInput> callInputClass;
    final public Class<CallOutput> transitionInputClass;
    private Optional<Duration> _timeout = Optional.empty();

    /**
     * Not for direct user construction, instances are created through the workflow DSL
     */
    public AsyncCallStep(String name,
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

    /**
     * Define a step timeout.
     */
    public AsyncCallStep<CallInput, CallOutput, FailoverInput> timeout(Duration timeout) {
      this._timeout = Optional.of(timeout);
      return this;
    }
  }

  public static class StepConfig {
    public final String stepName;
    public final Optional<Duration> timeout;
    public final Optional<RecoverStrategy<?>> recoverStrategy;

    public StepConfig(String stepName, Optional<Duration> timeout, Optional<RecoverStrategy<?>> recoverStrategy) {
      this.stepName = stepName;
      this.timeout = timeout;
      this.recoverStrategy = recoverStrategy;
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

  public static class RecoverStrategy<T> {

    public final int maxRetries;
    public final String failoverStepName;
    public final Optional<T> failoverStepInput;

    public RecoverStrategy(int maxRetries, String failoverStepName, Optional<T> failoverStepInput) {
      this.maxRetries = maxRetries;
      this.failoverStepName = failoverStepName;
      this.failoverStepInput = failoverStepInput;
    }

    /**
     * Retry strategy without failover configuration
     */
    public static class MaxRetries {
      public final int maxRetries;

      public MaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
      }

      /**
       * Once max retries is exceeded, transition to a given step name.
       */
      public RecoverStrategy<?> failoverTo(String stepName) {
        return new RecoverStrategy<>(maxRetries, stepName, Optional.<Void>empty());
      }

      /**
       * Once max retries is exceeded, transition to a given step name with the input parameter.
       */
      public <T> RecoverStrategy<T> failoverTo(String stepName, T input) {
        return new RecoverStrategy<>(maxRetries, stepName, Optional.of(input));
      }

      public int getMaxRetries() {
        return maxRetries;
      }
    }

    /**
     * Set the number of retires for a failed step, {@code maxRetries} equals 0 means that the step won't retry in case of failure.
     */
    public static MaxRetries maxRetries(int maxRetries) {
      return new MaxRetries(maxRetries);
    }

    /**
     * In case of a step failure don't retry but transition to a given step name.
     */
    public static RecoverStrategy<?> failoverTo(String stepName) {
      return new RecoverStrategy<>(0, stepName, Optional.<Void>empty());
    }

    /**
     * In case of a step failure don't retry but transition to a given step name with the input parameter.
     */
    public static <T> RecoverStrategy<T> failoverTo(String stepName, T input) {
      return new RecoverStrategy<>(0, stepName, Optional.of(input));
    }
  }

}
