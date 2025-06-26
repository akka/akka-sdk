package demo.multiagent.application;

import demo.multiagent.domain.AgentSelection;

// tag::all[]
import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;

// tag::class[]
@ComponentId("selector-agent")
@AgentDescription(
    name = "Selector Agent",
    description = """
      An agent that analyses the user request and selects useful agents for
      answering the request.
    """
)
public class SelectorAgent extends Agent {

  private final String systemMessage;

  public SelectorAgent(AgentRegistry agentsRegistry) { // <1>

    var agents = agentsRegistry.agentsWithRole("worker"); // <2>

    this.systemMessage = """
        Your job is to analyse the user request and select the agents that should be used to answer
        the user. In order to do that, you will receive a list of available agents. Each agent has
        an id, a name and a description of its capabilities.
      
        For example, a user may be asking to book a trip. If you see that there is a weather agent,
        a city trip agent and a hotel booking agent, you should select those agents to complete the
        task. Note that this is just an example. The list of available agents may vary, so you need
        to use reasoning to dissect the original user request and using the list of available agents,
        decide which agents must be selected.
      
        You don't need to come up with an execution order. Your task is to analyze user's request and
        select the agents.
      
        Your response should follow a strict json schema as defined bellow.
        It should contain a single field 'agents'. The field agents must be array of strings
        containing the agent's IDs. If none of the existing agents are suitable for executing
        the task, you return an empty array.
      
         {
           "agents": []
         }
      
        Do not include any explanations or text outside of the JSON structure.
      
        You can find the list of existing agents below (in JSON format):
        Also important, use the agent id to identify the agents.
        %s
      """
        .stripIndent()
        .formatted(JsonSupport.encodeToString(agents)); // <3>
  }


  public Effect<AgentSelection> selectAgents(String message) {
    return effects()
        .systemMessage(systemMessage)
        .userMessage(message)
        .responseAs(AgentSelection.class)
        .thenReply();
  }
}
// end::class[]
// end::all[]
