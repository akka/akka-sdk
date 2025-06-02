/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.ConversationHistory;
import akka.javasdk.agent.ConversationMemory;
import akka.javasdk.agent.ConversationMessage;
import akka.javasdk.agent.CoreMemory;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.client.ComponentClient;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * INTERNAL USE
 * Not for user extension or instantiation
 */
@InternalApi
public final class CoreMemoryClient implements CoreMemory {

  public record MemorySettings(
      boolean read,
      boolean write,
      Optional<Integer> historyLimit
  ) {
    static MemorySettings disabled() {
      return new MemorySettings(false, false, Optional.empty());
    }

    static MemorySettings enabled() {
      return new MemorySettings(true, true, Optional.empty());
    }
  }

  private final Logger logger = LoggerFactory.getLogger(CoreMemoryClient.class);
  private final ComponentClient componentClient;
  private final MemorySettings memorySettings;

  public CoreMemoryClient(ComponentClient componentClient, Config memoryConfig) {
    this.componentClient = componentClient;
    this.memorySettings = memoryConfig.getBoolean("enabled") ? MemorySettings.enabled() : MemorySettings.disabled();
  }

  public CoreMemoryClient(ComponentClient componentClient, MemorySettings memorySettings) {
    this.componentClient = componentClient;
    this.memorySettings = memorySettings;
  }

  public void addInteraction(String sessionId,
                             String componentId,
                             ConversationMessage.UserMessage userMessage,
                             ConversationMessage.AiMessage aiMessage) {
    if (memorySettings.write()) {
      logger.debug("Adding interaction to sessionId [{}]", sessionId);
      componentClient.forEventSourcedEntity(sessionId)
          .method(ConversationMemory::addInteraction)
          .invoke(new ConversationMemory.AddInteractionCmd(componentId, userMessage, aiMessage));
    } else {
      logger.debug("Memory writing is disabled, interaction not added to sessionId [{}]", sessionId);
    }
  }

  @Override
  public ConversationHistory getHistory(String sessionId) {
    if (memorySettings.read()) {
      var history = componentClient.forEventSourcedEntity(sessionId)
          .method(ConversationMemory::getHistory)
          .invoke(new ConversationMemory.GetHistoryCmd(memorySettings.historyLimit));
      logger.debug("History retrieved for sessionId [{}], size [{}]", sessionId, history.messages().size());
      return history;
    } else {
      logger.debug("Memory reading is disabled, history not retrieved for sessionId [{}]", sessionId);
      return ConversationHistory.EMPTY;
    }
  }
}
