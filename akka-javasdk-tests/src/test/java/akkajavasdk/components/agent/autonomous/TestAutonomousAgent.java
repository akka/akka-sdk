/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

@Component(id = "test-autonomous-agent")
public class TestAutonomousAgent extends AutonomousAgent {

  // Injected to exercise the SdkRunner scan-time wiring path: a ComponentClient
  // dependency on a non-last-scanned AutonomousAgent used to force the lazy
  // capability converter mid-scan, snapshotting an incomplete agent registry.
  @SuppressWarnings("unused")
  private final ComponentClient componentClient;

  public TestAutonomousAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Test agent")
        .capability(TaskAcceptance.of(TestTasks.TEST_TASK, TestTasks.STRING_TASK));
  }
}
