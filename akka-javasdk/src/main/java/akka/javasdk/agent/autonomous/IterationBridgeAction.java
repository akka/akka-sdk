/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async bridge between the workflow and the LLM agent. The workflow fires a zero-delay timer
 * targeting this action, then pauses (becoming command-ready). This action calls the LLM agent
 * synchronously (blocking is fine in a TimedAction), then delivers the result back to the workflow
 * as a command.
 *
 * <p>Timer retry (3s exponential backoff, 30s max) handles transient failures automatically.
 */
@Component(
    id = "akka-iteration-bridge",
    name = "IterationBridgeAction",
    description = "Async bridge: calls LLM agent and delivers result to workflow")
public final class IterationBridgeAction extends TimedAction {

  private static final Logger log = LoggerFactory.getLogger(IterationBridgeAction.class);

  private final ComponentClient componentClient;

  public IterationBridgeAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /**
   * Small correlation payload for the timer. The actual ExecuteRequest is built by the workflow and
   * fetched via a command — this keeps the timer payload well under the 1024-byte limit.
   */
  public record BridgeRequest(
      String workflowId, String sessionId, int iteration, boolean teamMember) {}

  /**
   * Execute the LLM agent call and deliver the result back to the workflow. Called by a timer with
   * zero delay — the workflow is paused and command-ready while this runs.
   *
   * <p>Reads the full ExecuteRequest from the workflow (which holds all state needed to construct
   * it), calls the StrategyExecutor, then delivers the result back as a workflow command.
   */
  public Effect executeAndDeliver(BridgeRequest request) {
    log.info(
        "Bridge executing iteration {} for {} (teamMember={})",
        request.iteration(),
        request.sessionId(),
        request.teamMember());

    // Read the full ExecuteRequest from the workflow state
    var executeRequest =
        componentClient
            .forWorkflow(request.workflowId())
            .method(AutonomousAgentWorkflow::getExecuteRequest)
            .invoke();

    // Call the agent synchronously — blocking is fine in a TimedAction
    componentClient
        .forAgent()
        .inSession(request.sessionId())
        .method(StrategyExecutor::execute)
        .invoke(executeRequest);

    log.info(
        "Bridge delivering iteration {} result to workflow {}",
        request.iteration(),
        request.workflowId());

    // Deliver result back to the workflow as a command
    componentClient
        .forWorkflow(request.workflowId())
        .method(AutonomousAgentWorkflow::deliverIterationResult)
        .invoke(
            new AutonomousAgentWorkflow.IterationResult(request.iteration(), request.teamMember()));

    return effects().done();
  }
}
