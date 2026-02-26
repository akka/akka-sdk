/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import java.util.List;

/** Simple orchestrator agent for testing the agent team feature. */
@Component(id = "simple-orchestrator")
public class SimpleOrchestratorAgent extends Agent implements AgentTeam<String, String> {

  public static class DateTools {
    @FunctionTool(description = "Get the current date")
    public String getDate() {
      return "2025-01-15";
    }
  }

  @Override
  public String instructions() {
    return "Process this request, and respond with a plain text answer.";
  }

  @Override
  public List<Object> tools() {
    return List.of(new DateTools());
  }

  @Override
  public List<Delegation> delegations() {
    return List.of(
        Delegation.toAgent(SimpleWorkerAgent.class),
        Delegation.toAgent(ChildOrchestratorAgent.class));
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }
}
