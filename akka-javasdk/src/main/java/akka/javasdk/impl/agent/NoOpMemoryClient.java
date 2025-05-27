/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.ConversationHistory;
import akka.javasdk.agent.CoreMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL USE
 */
@InternalApi
public final class NoOpMemoryClient implements CoreMemory {

  private final Logger logger = LoggerFactory.getLogger(NoOpMemoryClient.class);

  @Override
  public void addInteraction(String sessionId, String componentId, String userMessage, String aiMessage) {
    logger.debug("Not saving interaction for session: {}, component: {}", sessionId, componentId);
  }

  @Override
  public ConversationHistory getHistory(String sessionId) {
    logger.debug("Not retrieving history for session: {}", sessionId);
    return ConversationHistory.EMPTY;
  }
}
