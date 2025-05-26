/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

abstract class MultiAgentSample {

  @ComponentId("planner")
  @AgentDescription(name = "Agent planner", description = "...")
  static class Planner extends Agent {
    record Plan(List<PlanStep> steps) {}
    record PlanStep(String agentId, String query) {}

    private final AgentRegistry registry;

    private final String systemMessageTemplate =
        """
        Your job is to analyse the user request and the list of agents and devise which agents to use
        and the best order in which the agents should be called in order to produce a suitable answer
        to the user.
      
        You can find the list of exiting agents below (in JSON format):
        %s
      
        Note that each agent has a description of its capabilities. Given the user request,
        you must define the right ordering.
      
        Moreover, you must generate a concise request to be sent to each agent. This agent request is of course
        based on the user original request, but is tailored to the specific agent. Each individual agent should not receive
        requests or any text that is not related with its domain of expertise.
      
        Your response should follow a strict json schema as defined bellow.
         {
           "steps": [
              {
                "agentId": "<the id of the agent>",
                "query: "<agent tailored query>",
              }
           ]
         }
      
        The '<the id of the agent>' should be filled with the agent id.
        The '<agent tailored query>' should contain the agent tailored message.
        The order og the items inside the "steps" array should be the order of execution.
      
        Do not include any explanations or text outside of the JSON structure.
        """.stripIndent();

    Planner(AgentRegistry registry) {
      this.registry = registry;
    }

    public Effect<Plan> plan(String arg) {
      var agentIds = registry.agentIdsWithRole("workers");
      var agentDescriptions = agentIds.stream()
          .map(registry::agentDescriptionAsJson)
          .collect(Collectors.joining(", ", "[", "]"));

      return effects()
          .systemMessage(systemMessageTemplate.formatted(agentDescriptions))
          .userMessage(arg)
          .thenReplyAs(Plan.class);
    }
  }

  @ComponentId("agent1")
  @AgentDescription(name = "Agent 1", description = "...", role = "worker")
  static class Agent1 extends Agent {
    public Effect<String> call1(String arg) {
      return effects().reply("rsp1");
    }
  }

  @ComponentId("agent2")
  @AgentDescription(name = "Agent 2", description = "...", role = "worker")
  static class Agent2 extends Agent {
    public Effect<String> call2(String arg) {
      return effects().reply("rsp2");
    }
  }

  @ComponentId("agent-team-a")
  static class TeamA { // this would be a workflow
    private final ComponentClient componentClient;
    private final AgentRegistry registry;

    TeamA(ComponentClient componentClient, AgentRegistry registry) {
      this.componentClient = componentClient;
      this.registry = registry;
    }

    public void run() {
      var sessionId = UUID.randomUUID().toString();

      var plan =
        componentClient.forAgent()
          .inSession(sessionId)
          .method(Planner::plan)
          .invoke("task...");

      List<Object> results =
        plan.steps.stream().map(step -> {
          return registry.getAgent(step.agentId)
              .invoke(step.query);
        }).toList();

      System.out.println(results);
    }

  }
}
