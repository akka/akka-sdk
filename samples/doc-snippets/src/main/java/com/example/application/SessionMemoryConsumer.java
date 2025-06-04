package com.example.application;

import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::consumer[]
@ComponentId("session-memory-consumer")
@Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
public class SessionMemoryConsumer extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public Effect onSessionMemoryEvent(SessionMemoryEntity.Event event) {
    var sessionId = messageContext().eventSubject().get();

    switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded userMsg ->
        logger.info("User message added to session {}: {}",
            sessionId, userMsg.message());
      // ...

      default -> logger.debug("Unhandled session memory event: {}", event);
    }

    return effects().done();
  }
}
// end::consumer[]
