/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.Optional;

public class SessionMemoryEventConverter {

  public static Optional<SessionMessage> convert(SessionMemoryEntity.Event event) {
    return switch (event) {
      case SessionMemoryEntity.Event.LimitedWindowSet __ -> Optional.empty();
      case SessionMemoryEntity.Event.HistoryCleared __ -> Optional.empty();
      case SessionMemoryEntity.Event.Deleted __ -> Optional.empty();

      case SessionMemoryEntity.Event.UserMessageAdded userMsg ->
          Optional.of(
              new SessionMessage.UserMessage(
                  userMsg.timestamp(), userMsg.message(), userMsg.componentId()));

      case SessionMemoryEntity.Event.MultimodalUserMessageAdded multimodalUserMsg ->
          Optional.of(
              new SessionMessage.MultimodalUserMessage(
                  multimodalUserMsg.timestamp(),
                  multimodalUserMsg.contents(),
                  multimodalUserMsg.componentId()));

      case SessionMemoryEntity.Event.AiMessageAdded aiMsg ->
          Optional.of(
              new SessionMessage.AiMessage(
                  aiMsg.timestamp(),
                  aiMsg.message(),
                  aiMsg.componentId(),
                  aiMsg.toolCallRequests(),
                  aiMsg.thinking(),
                  aiMsg.tokenUsage().orElse(SessionMessage.TokenUsage.EMPTY),
                  aiMsg.attributes()));

      case SessionMemoryEntity.Event.ToolResponseMessageAdded toolMsg ->
          Optional.of(
              new SessionMessage.ToolCallResponse(
                  toolMsg.timestamp(),
                  toolMsg.componentId(),
                  toolMsg.id(),
                  toolMsg.name(),
                  toolMsg.content()));
    };
  }
}
