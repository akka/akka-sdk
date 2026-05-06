package com.example.application;

import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMemory;
import akka.javasdk.agent.SessionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CustomSessionMemoryProvider {

  public static MemoryProvider memoryProvider() {

    class SessionMemoryWrapper implements SessionMemory {

      private final SessionMemory delegate;

      private final Logger logger = LoggerFactory.getLogger(SessionMemoryWrapper.class);

      SessionMemoryWrapper(SessionMemory delegate) {
        this.delegate = delegate;
      }

      @Override
      public void addInteraction(String sessionId, SessionMessage.UserMessage userMessage, List<SessionMessage> messages) {
        logger.info("inside new SessionMemory: [{}] - [{}]", sessionId, userMessage);
        delegate.addInteraction(sessionId, userMessage, messages);
      }

      @Override
      public void addInteraction(String sessionId, SessionMessage.MultimodalUserMessage userMessage, List<SessionMessage> messages) {
        delegate.addInteraction(sessionId, userMessage, messages);
      }

      @Override
      public SessionHistory getHistory(String sessionId) {
        return delegate.getHistory(sessionId);
      }
    }

    return MemoryProvider.composite(SessionMemoryWrapper::new);
  }
}
