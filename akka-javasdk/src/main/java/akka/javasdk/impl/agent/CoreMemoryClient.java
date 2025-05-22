/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.ConversationHistory;
import akka.javasdk.agent.ConversationMemory;
import akka.javasdk.agent.CoreMemory;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL USE
 * Not for user extension or instantiation
 */
@InternalApi
public class CoreMemoryClient implements CoreMemory {

  private final Logger logger = LoggerFactory.getLogger(CoreMemoryClient.class);
  private final ComponentClient componentClient;

  public CoreMemoryClient(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public void addUserMessage(String componentId, String sessionId, String message) {
    logger.debug("Adding user message to sessionId={} from componentId={}", sessionId, componentId);
    componentClient
        .forEventSourcedEntity(sessionId)
        .method(ConversationMemory::addUserMessage)
        .invoke(message);
  }

  @Override
  public void addAiMessage(String sessionId, String message) {
    logger.debug("Adding AI message to session: {}", sessionId);
    componentClient
        .forEventSourcedEntity(sessionId)
        .method(ConversationMemory::addAiMessage)
        .invoke(message);
  }

  public void addInteraction(String sessionId, String userMessage, String aiMessage) {
    logger.debug("Adding interaction to session: {}", sessionId);
    componentClient.forEventSourcedEntity(sessionId)
        .method(ConversationMemory::addInteraction)
        .invoke(new ConversationMemory.AddInteractionCmd(userMessage, aiMessage));
  }

  @Override
  public ConversationHistory getFullHistory(String sessionId) {
    var history = componentClient.forEventSourcedEntity(sessionId)
        .method(ConversationMemory::getHistory)
        .invoke();
    logger.debug("Full history retrieved for sessionId={} size={}", sessionId, history.messages().size());
    return history;
  }
}
