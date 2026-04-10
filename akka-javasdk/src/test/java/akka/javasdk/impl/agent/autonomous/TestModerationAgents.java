/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/** Test fixtures for moderation converter tests. */
public class TestModerationAgents {

  @Component(id = "test-advocate", description = "A test advocate agent")
  public abstract static class TestAdvocate extends AutonomousAgent {}

  @Component(id = "test-critic", description = "A test critic agent")
  public abstract static class TestCritic extends AutonomousAgent {}
}
