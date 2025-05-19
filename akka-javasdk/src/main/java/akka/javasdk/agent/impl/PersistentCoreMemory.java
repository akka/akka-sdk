/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.impl;

import akka.javasdk.agent.ConversationMemory;
import akka.javasdk.agent.CoreMemory;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL USE
 * Not for user extension or instantiation
 */
public class PersistentCoreMemory implements CoreMemory {

  private final Logger logger = LoggerFactory.getLogger(PersistentCoreMemory.class);
  private final ComponentClient componentClient;

  public PersistentCoreMemory(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public void addUserMessage(String sessionId, String message) {
    logger.debug("Adding user message to session: {}", sessionId);
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

  @Override
  public ConversationHistory getFullHistory(String sessionId) {
    var msgs = componentClient.forEventSourcedEntity(sessionId)
        .method(ConversationMemory::getHistory)
        .invoke()
        .messages();
    return new ConversationHistory(msgs);
  }
}
