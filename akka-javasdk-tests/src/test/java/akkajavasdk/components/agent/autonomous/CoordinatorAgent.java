/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "coordinator-agent")
public class CoordinatorAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Coordinate research by delegating to workers and synthesising results.")
        .capability(TaskAcceptance.of(DelegationTaskDefs.RESEARCH).maxIterationsPerTask(5))
        .capability(Delegation.to(WorkerAgent.class).maxParallelWorkers(2));
  }
}
