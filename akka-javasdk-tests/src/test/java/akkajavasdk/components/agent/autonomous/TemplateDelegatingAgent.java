/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "template-delegating-agent")
public class TemplateDelegatingAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Coordinate research by delegating work items using task templates.")
        .capability(TaskAcceptance.of(TestTasks.RESEARCH).maxIterationsPerTask(5))
        .capability(Delegation.to(TeamWorkerAgent.class));
  }
}
