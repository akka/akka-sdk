/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.Moderation;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.annotations.Component;

@Component(id = "test-moderator")
public class TestModerator extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .goal("Moderate structured conversations between participants.")
        .capability(TaskAcceptance.of(TestTasks.MODERATE))
        .capability(Moderation.of(DebaterA.class, DebaterB.class).maxRounds(3));
  }
}
