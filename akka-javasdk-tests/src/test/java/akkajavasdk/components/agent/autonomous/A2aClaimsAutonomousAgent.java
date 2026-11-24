/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.RemoteA2aAgent;
import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
    id = "a2a-claims-autonomous-agent",
    description = "Test agent with the skills of a remote A2A agent.")
public class A2aClaimsAutonomousAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .instructions("You are a helpful assistant")
        .a2aAgents(RemoteA2aAgent.named("claims").withAllowedSkills("lookup-claim"))
        .capability(TaskAcceptance.of(TestTasks.STRING_TASK));
  }
}
