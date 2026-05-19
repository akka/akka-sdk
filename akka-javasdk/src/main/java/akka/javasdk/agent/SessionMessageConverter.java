/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

public class SessionMessageConverter {

  public static SessionMessage apply(SessionMemoryEntity.Event.MultimodalUserMessageAdded event) {
    return new SessionMessage.MultimodalUserMessage(
        event.timestamp(), event.contents(), event.componentId());
  }

  public static SessionMessage apply(SessionMemoryEntity.Event.UserMessageAdded event) {
    return new SessionMessage.UserMessage(event.timestamp(), event.message(), event.componentId());
  }

  public static SessionMessage apply(SessionMemoryEntity.Event.AiMessageAdded event) {
    return new SessionMessage.AiMessage(
        event.timestamp(),
        event.message(),
        event.componentId(),
        event.toolCallRequests(),
        event.thinking(),
        event.tokenUsage().orElse(SessionMessage.TokenUsage.EMPTY),
        event.attributes());
  }

  public static SessionMessage apply(SessionMemoryEntity.Event.ToolResponseMessageAdded event) {
    return new SessionMessage.ToolCallResponse(
        event.timestamp(), event.componentId(), event.id(), event.name(), event.content());
  }

  public static SessionMessage apply(SessionMemoryEntity.Event.Message event) {
    return switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded userMsg -> apply(userMsg);

      case SessionMemoryEntity.Event.MultimodalUserMessageAdded multimodalUserMsg ->
          apply(multimodalUserMsg);

      case SessionMemoryEntity.Event.AiMessageAdded aiMsg -> apply(aiMsg);

      case SessionMemoryEntity.Event.ToolResponseMessageAdded toolMsg -> apply(toolMsg);
    };
  }
}
