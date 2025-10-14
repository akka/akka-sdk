/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.AgentRole;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.DynamicMethodRef;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class MultiAgentSample {

  @Component(id = "planner", name = "Agent planner", description = "...")
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
"""
            .stripIndent();

    Planner(AgentRegistry registry) {
      this.registry = registry;
    }

    public Effect<Plan> plan(String arg) {
      var agentDescriptions = JsonSupport.encodeToString(registry.agentsWithRole("workers"));

      return effects()
          .systemMessage(systemMessageTemplate.formatted(agentDescriptions))
          .userMessage(arg)
          .responseAs(Plan.class)
          .thenReply();
    }
  }

  @Component(id = "agent1", name = "Agent 1", description = "...")
  @AgentRole("worker")
  static class Agent1 extends Agent {
    public Effect<String> call1(String arg) {
      return effects().reply("rsp1");
    }
  }

  @Component(id = "agent2", name = "Agent 2", description = "...")
  @AgentRole("worker")
  static class Agent2 extends Agent {
    public Effect<String> call2(String arg) {
      return effects().reply("rsp2");
    }
  }

  @Component(id = "agent-team-a")
  static class TeamA { // this would be a workflow
    private static final Logger logger = LoggerFactory.getLogger(TeamA.class);
    private final ComponentClient componentClient;
    private final AgentRegistry registry;

    private String sessionId = UUID.randomUUID().toString();

    TeamA(ComponentClient componentClient, AgentRegistry registry) {
      this.componentClient = componentClient;
      this.registry = registry;
    }

    public void run() {
      var plan =
          componentClient.forAgent().inSession(sessionId).method(Planner::plan).invoke("task...");

      List<String> results =
          plan.steps.stream()
              .map(
                  step -> {
                    return agentCall(step.agentId).invoke(step.query);
                  })
              .toList();

      logger.info("Result: {}", results);
    }

    private DynamicMethodRef<String, String> agentCall(String agentId) {
      // We know the id of the agent to call, but not the agent class.
      // Could be Agent1 or Agent2.
      // We can still invoke the agent based on its id, given that we know that it
      // takes a String parameter and returns String.
      return componentClient.forAgent().inSession(sessionId).dynamicCall(agentId);
    }
  }
}
