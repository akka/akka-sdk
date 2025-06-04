package demo.multiagent.application.agents;

import demo.multiagent.application.SessionMemory;
import demo.multiagent.domain.AgentResponse;
import demo.multiagent.domain.MessageAdapter;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;

import java.util.Collection;
import java.util.Collections;

public abstract class Agent {

  private final SessionMemory sessionMemory;
  private final ChatLanguageModel chatLanguageModel;

  protected Agent(SessionMemory sessionMemory, ChatLanguageModel chatLanguageModel) {
    this.sessionMemory = sessionMemory;
    this.chatLanguageModel = chatLanguageModel;
  }

  interface Assistant {
    String chat(String message);
  }

  public  Collection<Object> availableTools() {
    return Collections.emptyList();
  }

  public abstract String agentSpecificSystemMessage();

  private final String agentResponseSpec = """
      IMPORTANT:
      Output should be in json format with the following fields:
        {
          "response": "string",
          "error": "string",
        }
      
      When you can generate a response, the error field should be empty.
        For example:
        {
          "response": "The weather is sunny.",
          "error": ""
        }
      
      When you can't generate a response, then it should be empty and the error
      field should contain a message explaining why you couldn't generate a response.
        For example:
        {
            "response": "",
            "error": "I cannot provide a response for this question."
        }
  
      You return an error if the asked question is outside your domain of expertise,
       if it's invalid or if you cannot provide a response for any other reason.

      Do not include any explanations or text outside of the JSON structure.
      """;

  public AgentResponse query(String memoryId, String message) {

    var chatMessages =
      sessionMemory.getConversationHistory(memoryId)
        .stream().map(MessageAdapter::toLangchain4jChatMessage).toList();

    var chatMemoryStore = new InMemoryChatMemoryStore();
    chatMemoryStore.updateMessages(memoryId, chatMessages);

    var chatMemory = MessageWindowChatMemory.builder()
      .maxMessages(30000)
      .chatMemoryStore(chatMemoryStore)
      .id(memoryId)
      .build();

    var assistant = AiServices.builder(Assistant.class)
      .chatLanguageModel(chatLanguageModel)
      .chatMemory(chatMemory)
      .systemMessageProvider(__ -> agentSpecificSystemMessage() + agentResponseSpec)
      .tools(availableTools())
      .build();

    var res = AgentResponse.fromJson(assistant.chat(message));
    if (res.isValid()) {
      sessionMemory.saveConversation(memoryId, message, res.response());
    }
    return res;
  }

}
