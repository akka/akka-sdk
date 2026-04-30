/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.autonomous.AgentDefinition;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.agent.autonomous.capability.TaskAcceptance;
import akka.javasdk.agent.autonomous.capability.TeamLeadership;
import akka.javasdk.agent.autonomous.capability.TeamLeadership.TeamMember;
import akka.javasdk.annotations.Component;

@Component(id = "team-lead-agent")
public class TeamLeadAgent extends AutonomousAgent {

  @Override
  public AgentDefinition definition() {
    return define()
        .purpose("Plan and coordinate work by leading a team.")
        .capability(TaskAcceptance.of(TestTasks.PLAN))
        .capability(TeamLeadership.of(TeamMember.of(TeamWorkerAgent.class).maxInstances(2)));
  }
}
