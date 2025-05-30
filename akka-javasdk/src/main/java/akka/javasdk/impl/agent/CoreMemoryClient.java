/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.ConversationHistory;
import akka.javasdk.agent.ConversationMemory;
import akka.javasdk.agent.ConversationMessage;
import akka.javasdk.agent.CoreMemory;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL USE
 * Not for user extension or instantiation
 */
@InternalApi
public final class CoreMemoryClient implements CoreMemory {

  private final Logger logger = LoggerFactory.getLogger(CoreMemoryClient.class);
  private final ComponentClient componentClient;

  public CoreMemoryClient(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public void addInteraction(String sessionId,
                             String componentId,
                             ConversationMessage.UserMessage userMessage,
                             ConversationMessage.AiMessage aiMessage) {
    logger.debug("Adding interaction to session: {}", sessionId);
    componentClient.forEventSourcedEntity(sessionId)
        .method(ConversationMemory::addInteraction)
        .invoke(new ConversationMemory.AddInteractionCmd(componentId, userMessage, aiMessage));
  }

  @Override
  public ConversationHistory getHistory(String sessionId) {
    var history = componentClient.forEventSourcedEntity(sessionId)
        .method(ConversationMemory::getHistory)
        .invoke();
    logger.debug("History retrieved for sessionId={} size={}", sessionId, history.messages().size());
    return history;
  }
}
