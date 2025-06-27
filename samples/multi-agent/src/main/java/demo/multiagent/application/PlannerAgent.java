package demo.multiagent.application;

import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.Plan;
import demo.multiagent.domain.PlanStep;

// tag::all[]
import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

import java.util.List;

// tag::class[]
@ComponentId("planner-agent")
@AgentDescription(
    name = "Planner",
    description = """
        An agent that analyzes the user request and available agents to plan the tasks
        to produce a suitable answer.
        """)
public class PlannerAgent extends Agent {

  public record Request(String message, AgentSelection agentSelection) {}

  private final AgentRegistry agentsRegistry;

  public PlannerAgent(AgentRegistry agentsRegistry) {
    this.agentsRegistry = agentsRegistry;
  }

  private String buildSystemMessage(AgentSelection agentSelection) {
    var agents = agentSelection.agents().stream().map(agentsRegistry::agentInfo).toList(); // <1>
    return """
        Your job is to analyse the user request and the list of agents and devise the best order in
        which the agents should be called in order to produce a suitable answer to the user.
      
        You can find the list of exiting agents below (in JSON format):
        %s
      
        Note that each agent has a description of its capabilities. Given the user request,
        you must define the right ordering.
      
        Moreover, you must generate a concise request to be sent to each agent. This agent request is
        of course based on the user original request, but is tailored to the specific agent. Each
        individual agent should not receive requests or any text that is not related with its domain
        of expertise.
      
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
        The order of the items inside the "steps" array should be the order of execution.
      
        Do not include any explanations or text outside of the JSON structure.
      
      """.stripIndent()
      // note: here we are not using the full list of agents, but a pre-selection
      .formatted(JsonSupport.encodeToString(agents)); // <2>
  }

  public Effect<Plan> createPlan(Request request) {
    if (request.agentSelection.agents().size() == 1) {
      // no need to call an LLM to make a plan where selection has a single agent
      var step = new PlanStep(request.agentSelection.agents().getFirst(), request.message());
      return effects().reply(new Plan(List.of(step)));
    } else {
      return effects()
        .systemMessage(buildSystemMessage(request.agentSelection))
        .userMessage(request.message())
        .responseAs(Plan.class)
        .thenReply();
      }
  }
}
// end::class[]
// end::all[]
