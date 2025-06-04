package demo.multiagent.application;

import akka.javasdk.client.ComponentClient;
import demo.multiagent.domain.SessionMessage;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

public class SessionMemory {

  private final ComponentClient componentClient;

  public SessionMemory(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public List<SessionMessage> getConversationHistory(String memoryId) {
    return componentClient
      .forEventSourcedEntity(memoryId)
      .method(SessionEntity::getHistory)
      .invoke();
  }

  public  void saveConversation(String memoryId, String query, String response) {
    var exchange = new SessionEntity.ChatExchange(
      query,
      response
    );

    componentClient
      .forEventSourcedEntity(memoryId)
      .method(SessionEntity::addExchange)
      .invoke(exchange);
  }

}
