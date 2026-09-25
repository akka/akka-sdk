/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;

/**
 * Bound by role to the "role-scoped" sanitizer, see test application.conf. An autonomous agent
 * carries its role for sanitizer binding the same way a request based agent does.
 */
@Component(id = "sanitizer-autonomous-agent", description = "Test agent for scoped sanitizers.")
@AgentRole("sanitizer-role")
public class SanitizerAutonomousTestAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define().capability(TaskAcceptance.of(TestTasks.STRING_TASK).maxIterationsPerTask(2));
  }
}
