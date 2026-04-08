/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

@Component(id = "coordinator-agent")
public class CoordinatorAgent extends AutonomousAgent {

  // Injected to exercise the SdkRunner scan-time wiring path: a ComponentClient
  // dependency on a non-last-scanned AutonomousAgent used to force the lazy
  // capability converter mid-scan, snapshotting an incomplete agent registry.
  @SuppressWarnings("unused")
  private final ComponentClient componentClient;

  public CoordinatorAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Coordinate research by delegating to workers and synthesising results.")
        .capability(TaskAcceptance.of(TestTasks.RESEARCH).maxIterationsPerTask(5))
        .capability(Delegation.to(WorkerAgent.class).maxParallelWorkers(2));
  }
}
