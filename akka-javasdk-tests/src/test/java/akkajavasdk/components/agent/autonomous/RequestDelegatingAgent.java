/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Delegation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;
import akkajavasdk.components.agent.SomeAgent;

@Component(id = "request-delegating-agent")
public class RequestDelegatingAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Coordinate work by delegating fact-checking to a request-based agent.")
        .capability(TaskAcceptance.of(TestTasks.TEST_TASK).maxIterationsPerTask(5))
        .capability(Delegation.to(SomeAgent.class));
  }
}
