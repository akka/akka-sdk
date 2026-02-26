/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;
import java.util.List;

/**
 * Child orchestrator agent for testing delegation from SimpleOrchestratorAgent to another agent
 * team.
 */
@Component(id = "child-orchestrator")
public class ChildOrchestratorAgent extends Agent implements AgentTeam<String, String> {

  @Override
  public String instructions() {
    return "You are a child orchestrator. Complete the task and respond with a plain text result.";
  }

  @Override
  public List<Delegation> delegations() {
    return List.of();
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }
}
