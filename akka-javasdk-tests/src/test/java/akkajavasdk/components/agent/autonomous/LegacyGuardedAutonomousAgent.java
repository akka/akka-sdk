/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(
    id = "legacy-guarded-autonomous-agent",
    description = "Test agent with a legacy request guardrail.")
public class LegacyGuardedAutonomousAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .instructions("You are a helpful assistant")
        .capability(TaskAcceptance.of(TestTasks.STRING_TASK));
  }
}
