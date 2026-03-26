/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "test-autonomous-agent")
public class TestAutonomousAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Test agent")
        .capability(TaskAcceptance.of(TestTasks.TEST_TASK, TestTasks.STRING_TASK));
  }
}
