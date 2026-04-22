/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

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

  public WorkflowSettings settings() {
    return WorkflowSettings.builder().build();
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

    interface Transitional extends Effect<Void> {

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

  public sealed interface CommandHandler {
    record NoArgCommandHandler(akka.japi.function.Function<?, Effect<Done>> handler)
        implements CommandHandler {}

    record OneArgCommandHandler(
        akka.japi.function.Function2<?, ?, Effect<Done>> handler, Object input)
        implements CommandHandler {}
  }

  public sealed interface StepHandler {
    record NoArgStepHandler(akka.japi.function.Function<?, StepEffect> handler)
        implements StepHandler {}

    record OneArgStepHandler(akka.japi.function.Function2<?, ?, StepEffect> handler, Object input)
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
              Optional.of(new StepHandler.NoArgStepHandler(timeoutFailoverStep)));
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
              Optional.of(new StepHandler.OneArgStepHandler(timeoutFailoverStep, input)));
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

  public record StepSettings(
      String stepName,
      Optional<Duration> timeout,
      Optional<RecoverStrategy<?>> recovery,
      Optional<Object> stepLambda) {

    public static StepSettings empty(String name) {
      return new StepSettings(name, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public StepSettings withTimeout(Duration timeout) {
      return new StepSettings(stepName, Optional.of(timeout), recovery, stepLambda);
    }

    public StepSettings withRecovery(RecoverStrategy<?> recovery, Object stepLambda) {
      return new StepSettings(stepName, timeout, Optional.of(recovery), Optional.of(stepLambda));
    }
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
            Optional.of(new StepHandler.OneArgStepHandler(lambda, input)));
      }
    }

    /** Retry strategy without failover configuration */
    public record MaxRetries(int maxRetries) {

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
            Optional.of(new StepHandler.NoArgStepHandler(lambda)));
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
          0, stepName, Optional.empty(), Optional.of(new StepHandler.NoArgStepHandler(lambda)));
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
