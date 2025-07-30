/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMemory;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.client.ComponentClient;
import com.typesafe.config.Config;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** INTERNAL USE Not for user extension or instantiation */
@InternalApi
public final class SessionMemoryClient implements SessionMemory {

  public record MemorySettings(boolean read, boolean write, Optional<Integer> historyLimit) {
    static MemorySettings disabled() {
      return new MemorySettings(false, false, Optional.empty());
    }

    static MemorySettings enabled() {
      return new MemorySettings(true, true, Optional.empty());
    }
  }

  private final Logger logger = LoggerFactory.getLogger(SessionMemoryClient.class);
  private final ComponentClient componentClient;
  private final MemorySettings memorySettings;

  public SessionMemoryClient(ComponentClient componentClient, Config memoryConfig) {
    this.componentClient = componentClient;
    this.memorySettings =
        memoryConfig.getBoolean("enabled") ? MemorySettings.enabled() : MemorySettings.disabled();
  }

  public SessionMemoryClient(ComponentClient componentClient, MemorySettings memorySettings) {
    this.componentClient = componentClient;
    this.memorySettings = memorySettings;
  }

  @Override
  public void addInteraction(
      String sessionId, SessionMessage.UserMessage userMessage, List<SessionMessage> messages) {
    if (memorySettings.write()) {
      logger.debug("Adding interaction to sessionId [{}]", sessionId);
      componentClient
          .forEventSourcedEntity(sessionId)
          .method(SessionMemoryEntity::addInteraction)
          .invoke(new SessionMemoryEntity.AddInteractionCmd(userMessage, messages));
    } else {
      logger.debug(
          "Memory writing is disabled, interaction not added to sessionId [{}]", sessionId);
    }
  }

  @Override
  public SessionHistory getHistory(String sessionId) {
    if (memorySettings.read()) {
      var history =
          componentClient
              .forEventSourcedEntity(sessionId)
              .method(SessionMemoryEntity::getHistory)
              .invoke(new SessionMemoryEntity.GetHistoryCmd(memorySettings.historyLimit));
      logger.debug(
          "History retrieved for sessionId [{}], size [{}]", sessionId, history.messages().size());
      return history;
    } else {
      logger.debug(
          "Memory reading is disabled, history not retrieved for sessionId [{}]", sessionId);
      return SessionHistory.EMPTY;
    }
  }
}
