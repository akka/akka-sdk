/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.AutonomousAgentWorkflow;
import akka.javasdk.agent.autonomous.Capability;
import akka.javasdk.client.AutonomousAgentClient;
import akka.javasdk.client.ComponentClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** INTERNAL USE Not for user extension or instantiation */
@InternalApi
public final class AutonomousAgentClientImpl implements AutonomousAgentClient {

  private final ComponentClient componentClient;
  private final String agentId;
  private final AutonomousAgent.StrategyView strategy;

  public AutonomousAgentClientImpl(
      ComponentClient componentClient, String agentId, AutonomousAgent agent) {
    this.componentClient = componentClient;
    this.agentId = agentId;
    this.strategy = agent.configure().toView();
  }

  @Override
  public String runSingleTask(String description, Class<?> resultType) {
    var taskId = UUID.randomUUID().toString();
    componentClient.forTask(taskId, resultType).create(description);
    assignSingleTask(taskId);
    return taskId;
  }

  @Override
  public Done start() {
    var startRequest =
        new AutonomousAgentWorkflow.StartRequest(
            strategy.maxIterations(),
            strategy.instructions(),
            strategy.toolClassNames(),
            strategy.capabilities());

    return componentClient
        .forWorkflow(agentId)
        .method(AutonomousAgentWorkflow::start)
        .invoke(startRequest);
  }

  @Override
  public Done assignTask(String taskId) {
    ensureStarted();
    return componentClient
        .forWorkflow(agentId)
        .method(AutonomousAgentWorkflow::assignTask)
        .invoke(taskId);
  }

  @Override
  public Done assignSingleTask(String taskId) {
    ensureStarted();
    return componentClient
        .forWorkflow(agentId)
        .method(AutonomousAgentWorkflow::assignSingleTask)
        .invoke(taskId);
  }

  @Override
  public Done assignHandedOffTask(String taskId) {
    ensureStarted();
    return componentClient
        .forWorkflow(agentId)
        .method(AutonomousAgentWorkflow::assignHandedOffTask)
        .invoke(taskId);
  }

  private void ensureStarted() {
    start(); // idempotent — no-op if already started
  }

  @Override
  public Done startAsTeamMember(List<Capability> teamCapabilities) {
    var allCapabilities = new ArrayList<>(strategy.capabilities());
    allCapabilities.addAll(teamCapabilities);

    var request =
        new AutonomousAgentWorkflow.TeamMemberStartRequest(
            strategy.maxIterations(),
            strategy.instructions(),
            strategy.toolClassNames(),
            allCapabilities);

    return componentClient
        .forWorkflow(agentId)
        .method(AutonomousAgentWorkflow::startAsTeamMember)
        .invoke(request);
  }

  @Override
  public Done stop() {
    return componentClient.forWorkflow(agentId).method(AutonomousAgentWorkflow::stop).invoke();
  }
}
