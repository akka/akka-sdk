/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import akka.javasdk.workflow.Workflow;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL API
 *
 * <p>Answers a short skill whose {@code SendMessage} set {@code returnImmediately}: its step calls
 * the agent and records the outcome in the task. The workflow id is the task id.
 *
 * <p>A workflow keeps the call durable: when the node stops during the call, the step runs again on
 * another node, so the agent can be called more than once. An agent that does not answer within
 * {@code akka.javasdk.a2a.endpoint.short-task-timeout} fails the task, and so does a step that
 * fails twice, so the task does not stay working.
 */
@InternalApi
@Component(id = "akka-a2a-short-task")
public final class A2aShortTaskWorkflow extends Workflow<A2aShortTaskWorkflow.State> {

  private static final Logger log = LoggerFactory.getLogger(A2aShortTaskWorkflow.class);

  // Time for the task to be stored after the agent has answered
  private static final Duration RECORD_TIME = Duration.ofSeconds(15);

  /**
   * @param taskId the A2A task id
   * @param contextId the A2A context id, as the client sent it
   * @param sessionId the session of the agent
   * @param agentId the component id of the agent
   * @param inputType the canonical name of the agent's input type, or null when it takes none
   * @param input the input of the agent as JSON, or null
   * @param artifactId the id of the artifact that holds the answer
   */
  public record State(
      String taskId,
      String contextId,
      String sessionId,
      String agentId,
      String inputType,
      JsonNode input,
      String artifactId) {}

  private final A2aTaskStore store;
  private final ComponentClient componentClient;
  private final Duration timeout;

  public A2aShortTaskWorkflow(ComponentClient componentClient, A2aEndpointSettings settings) {
    this.componentClient = componentClient;
    this.store = new A2aTaskStore(componentClient);
    this.timeout = settings.shortTaskTimeout();
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(timeout.plus(RECORD_TIME))
        .stepRecovery(
            A2aShortTaskWorkflow::answer,
            RecoverStrategy.maxRetries(1).failoverTo(A2aShortTaskWorkflow::fail))
        .build();
  }

  /** Starts the call. A call that has started is left as it is. */
  public Effect<Done> start(State call) {
    if (currentState() != null) return effects().reply(Done.getInstance());
    return effects()
        .updateState(call)
        .transitionTo(A2aShortTaskWorkflow::answer)
        .thenReply(Done.getInstance());
  }

  private StepEffect answer() {
    var call = currentState();
    Object result = null;
    Throwable error = null;
    try {
      result = callAgent(call).toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      error = new TimeoutException("The agent did not answer within " + timeout);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (Exception e) {
      error = e;
    }
    store.change(
        call.taskId(),
        A2aRequestHandler.outcome(
            call.taskId(), call.contextId(), result, error, call.artifactId()));
    return stepEffects().thenDelete();
  }

  private StepEffect fail() {
    var call = currentState();
    log.warn("Recording A2A task [{}] as failed, because its step failed", call.taskId());
    var reply =
        A2aTaskStore.agentMessage(
            call.taskId(),
            call.contextId(),
            List.of(A2aPart.text("The agent could not answer the message.")));
    store.change(call.taskId(), A2aTaskEntity.Change.status(A2aTaskState.FAILED, reply));
    return stepEffects().thenDelete();
  }

  private java.util.concurrent.CompletionStage<Object> callAgent(State call) {
    DynamicMethodRef<Object, Object> agent =
        componentClient.forAgent().inSession(call.sessionId()).dynamicCall(call.agentId());
    if (call.inputType() == null) return agent.invokeAsync();
    var mapper = JsonSupport.getObjectMapper();
    var type = mapper.getTypeFactory().constructFromCanonical(call.inputType());
    return agent.invokeAsync(mapper.convertValue(call.input(), type));
  }
}
