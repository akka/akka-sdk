/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.AutonomousAgentWorkflow;
import akka.javasdk.agent.autonomous.Capability;
import akka.javasdk.agent.task.ContentRef;
import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskRef;
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
  public <R> TaskRef<R> runSingleTask(Task<R> task) {
    var taskId = UUID.randomUUID().toString();
    var contentRefs = task.attachments().stream().map(ContentRef::fromMessageContent).toList();
    componentClient
        .forEventSourcedEntity(taskId)
        .method(TaskEntity::create)
        .invoke(
            new TaskEntity.CreateRequest(
                task.description(),
                task.instructions(),
                task.resultType().getName(),
                List.of(),
                contentRefs,
                task.policyClassNames()));
    assignSingleTask(taskId);
    return task.ref(taskId);
  }

  @Override
  public Done start() {
    var startRequest =
        new AutonomousAgentWorkflow.StartRequest(
            strategy.maxIterations(),
            strategy.instructions(),
            strategy.toolClassNames(),
            strategy.capabilities(),
            strategy.contentLoaderClassName());

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
            allCapabilities,
            strategy.contentLoaderClassName());

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
