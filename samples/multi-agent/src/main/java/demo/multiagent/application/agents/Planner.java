package demo.multiagent.application.agents;

import demo.multiagent.domain.AgentSelection;
import demo.multiagent.domain.Plan;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

public class Planner {

  private final AgentsRegistry agentsRegistry;
  private final ChatLanguageModel chatLanguageModel;

  public Planner(AgentsRegistry agentsRegistry, ChatLanguageModel chatLanguageModel) {
    this.agentsRegistry = agentsRegistry;
    this.chatLanguageModel = chatLanguageModel;
  }


  private String buildSystemMessage(AgentSelection selection) {
    return """
        Your job is to analyse the user request and the list of agents and devise the best order in which
        the agents should be called in order to produce a suitable answer to the user.
      
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
      // note: here we are not using the full list of agents, but a pre-selection
      .formatted(agentsRegistry.agentSelectionInJson(selection.agents()));
  }

  interface Assistant {
    Plan chat(String message);
  }


  public Plan createPlan(String message, AgentSelection agentSelection) {

    var assistant = AiServices.builder(Planner.Assistant.class)
      .chatLanguageModel(chatLanguageModel)
      .systemMessageProvider(__ -> buildSystemMessage(agentSelection))
      .build();

    return assistant.chat(message);
  }
}
