/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.swarm;

import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.time.Duration.ofSeconds;

/**
 * Abstract Swarm class that extends Workflow.
 *
 * <p>Two type parameters define the swarm's contract:
 * <ul>
 *   <li>{@code A} — the input type, passed to {@link #run(Object)} and available
 *       via {@link #getInput()} when configuring the swarm</li>
 *   <li>{@code B} — the result type, carried through to {@link SwarmResult.Completed#result()}</li>
 * </ul>
 *
 * <p>Users define a swarm by subclassing and overriding abstract methods to declare
 * instructions, handoffs, result type, etc. The agent loop and built-in operations
 * (pause, resume, stop) are provided by this base class using the Workflow machinery.
 *
 * @param <A> the input type accepted by {@link #run(Object)}
 * @param <B> the result type produced when the swarm completes successfully
 */
public abstract class Swarm<A, B>
    // Real implementation will probably not extend Workflow like this
    extends Workflow<SwarmState> {

  private static final Logger logger = LoggerFactory.getLogger(Swarm.class);

  private final ComponentClient componentClient;

  protected Swarm(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // ========== User defines the swarm by overriding these ==========

  /** The system instructions for the orchestrator LLM. */
  protected abstract String instructions();

  /** The handoff targets available to the orchestrator. */
  protected abstract List<Handoff> handoffs();

  /**
   * The expected result type. When the LLM produces output conforming to this type,
   * the swarm terminates successfully.
   */
  protected abstract Class<B> resultType();

  /** Maximum number of LLM round-trips before forced termination. Default: 10. */
  protected int maxTurns() {
    return 10;
  }

  /** Tools available to the orchestrator LLM (in addition to handoff tools). Default: none. */
  protected List<Object> tools() {
    return List.of();
  }

  /** Step timeout for the orchestration step in seconds. Default: 120. */
  protected int stepTimeoutSeconds() {
    return 120;
  }

  /** Maximum retries for the orchestration step. Default: 1. */
  protected int maxRetries() {
    return 1;
  }

  // ========== Input accessor ==========

  /**
   * Access the input that was passed to {@link #run(Object)}.
   * Available for use in {@link #instructions()}, {@link #handoffs()},
   * and {@link #tools()} to dynamically configure the swarm based on input.
   */
  protected A getInput() {
    return null; // provided by runtime
  }

  // ========== Workflow settings ==========

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(stepTimeoutSeconds()))
        .defaultStepRecovery(maxRetries(maxRetries()).failoverTo("error"))
        .build();
  }

  // ========== Built-in command handlers ==========

  /** Start the swarm with the given input. */
  public Effect<Void> run(A input) {
    if (currentState() != null) {
      return effects().error("Swarm '" + commandContext().workflowId() + "' already started");
    }
    var userMessage = String.valueOf(input);
    return effects()
        .updateState(SwarmState.initial(userMessage, maxTurns()))
        .transitionTo("orchestrate")
        .thenReply(null);
  }

  /** Get the current result/status as a fully-typed SwarmResult. */
  public ReadOnlyEffect<SwarmResult<B>> getResult() {
    if (currentState() == null) {
      return effects().error("Swarm not started");
    }
    return effects().reply(currentState().toSwarmResult());
  }

  // ========== Workflow steps ==========

  @StepName("orchestrate")
  private StepEffect orchestrateStep() {
    var state = currentState();
    logger.info("Starting orchestration for: {}", state.userMessage());

    var request = new SwarmOrchestratorAgent.OrchestrateRequest(
        instructions(),
        handoffs(),
        state.userMessage(),
        sessionId());

    var result = componentClient
        .forAgent()
        .inSession(sessionId())
        .method(SwarmOrchestratorAgent::orchestrate)
        .invoke(request);

    logger.info("Orchestration completed");

    return stepEffects()
        .updateState(state.completed(result))
        .thenEnd();
  }

  @StepName("error")
  private StepEffect errorStep() {
    logger.error("Orchestration failed for: {}", currentState().userMessage());
    return stepEffects()
        .updateState(currentState().failed("Orchestration failed after retries"))
        .thenEnd();
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
}
