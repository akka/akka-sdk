package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import java.util.Set;

public class DynamicAgentCall {

  static class Caller {

    private final ComponentClient componentClient;

    Caller(ComponentClient componentClient) {
      this.componentClient = componentClient;
    }

    String callByAgentId(String sessionId, String agentId, String request) {
      // tag::dynamic-call[]
      String response = componentClient
        .forAgent()
        .inSession(sessionId)
        .<String, String>dynamicCall(agentId)
        .invoke(request);
      // end::dynamic-call[]
      return response;
    }
  }

  @Component(id = "my-planner")
  public static class MyPlanner extends Agent {

    // tag::planner[]
    private final Set<AgentRegistry.AgentInfo> workers;

    public MyPlanner(AgentRegistry registry) {
      this.workers = registry.agentsWithRole("worker");
      // include worker descriptions in the planner's prompt
    }

    // end::planner[]

    public Effect<String> plan(String request) {
      return effects()
        .systemMessage("Available workers: " + workers.size())
        .userMessage(request)
        .thenReply();
    }
  }
}
