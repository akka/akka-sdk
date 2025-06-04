package demo.multiagent.application.agents;

import demo.multiagent.application.SessionMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;

@AgentCard(
  id = "activity-agent",
  name = "Activity Agent",
  description = """
      An agent that suggests activities in the real world. Like for example, a team building activity, sports,
      an indoor or outdoor game, board games, a city trip, etc.
    """
)
public class ActivityAgent extends Agent {

  private final String sysMessage = """
      You are an activity agent. Your job is to suggest activities in the real world. Like for example, a team
      building activity, sports, an indoor or outdoor game, board games, a city trip, etc.
    """;

  public ActivityAgent(SessionMemory sessionMemory, ChatLanguageModel chatLanguageModel) {
    super(sessionMemory, chatLanguageModel);
  }


  @Override
  public String agentSpecificSystemMessage() {
    return sysMessage;
  }


}
