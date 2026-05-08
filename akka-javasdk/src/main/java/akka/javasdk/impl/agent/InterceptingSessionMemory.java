/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.annotation.InternalApi;
import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMemory;
import akka.javasdk.agent.SessionMemoryInterceptor;
import akka.javasdk.agent.SessionMessage;
import java.util.List;
import java.util.Objects;

/** INTERNAL USE Not for user extension or instantiation */
@InternalApi
public final class InterceptingSessionMemory implements SessionMemory {

  private final SessionMemory delegate;
  private final SessionMemoryInterceptor interceptor;

  public InterceptingSessionMemory(SessionMemory delegate, SessionMemoryInterceptor interceptor) {
    this.delegate = delegate;
    this.interceptor = interceptor;
  }

  @Override
  public void addInteraction(
      String sessionId, SessionMessage.UserMessage userMessage, List<SessionMessage> messages) {
    delegate.addInteraction(
        sessionId,
        requireNonNullResult(interceptor.beforeWrite(sessionId, userMessage), "UserMessage"),
        transformMessages(sessionId, messages));
  }

  @Override
  public void addInteraction(
      String sessionId,
      SessionMessage.MultimodalUserMessage userMessage,
      List<SessionMessage> messages) {
    delegate.addInteraction(
        sessionId,
        requireNonNullResult(
            interceptor.beforeWrite(sessionId, userMessage), "MultimodalUserMessage"),
        transformMessages(sessionId, messages));
  }

  @Override
  public SessionHistory getHistory(String sessionId) {
    return delegate.getHistory(sessionId);
  }

  private List<SessionMessage> transformMessages(String sessionId, List<SessionMessage> messages) {
    return messages.stream().map(m -> dispatch(sessionId, m)).toList();
  }

  private SessionMessage dispatch(String sessionId, SessionMessage message) {
    return switch (message) {
      case SessionMessage.AiMessage ai ->
          requireNonNullResult(interceptor.beforeWrite(sessionId, ai), "AiMessage");
      case SessionMessage.ToolCallResponse tcr ->
          requireNonNullResult(interceptor.beforeWrite(sessionId, tcr), "ToolCallResponse");

      // UserMessage and MultimodalUserMessage are never present in the messages list — the user
      // message is passed as a separate argument to addInteraction. These cases exist only to
      // satisfy exhaustiveness on the sealed SessionMessage interface; pass through unchanged.
      case SessionMessage.UserMessage um -> um;
      case SessionMessage.MultimodalUserMessage mum -> mum;
    };
  }

  private static <T extends SessionMessage> T requireNonNullResult(T result, String messageType) {
    return Objects.requireNonNull(
        result, () -> "SessionMemoryInterceptor.beforeWrite(" + messageType + ") returned null");
  }
}
