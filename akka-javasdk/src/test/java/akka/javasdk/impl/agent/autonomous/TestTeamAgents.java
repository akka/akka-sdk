/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent.autonomous;

import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.annotations.Component;

/** Test fixtures for team leadership converter tests. */
public class TestTeamAgents {

  @Component(id = "test-developer", description = "A test developer agent")
  public abstract static class TestDeveloper extends AutonomousAgent {}

  @Component(id = "test-reviewer", description = "A test reviewer agent")
  public abstract static class TestReviewer extends AutonomousAgent {}
}
