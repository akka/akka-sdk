package com.example.application;

import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::consumer[]
// tag::compaction[]
@Component(id = "session-memory-consumer")
@Consume.FromEventSourcedEntity(SessionMemoryEntity.class)
public class SessionMemoryConsumer extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  // end::consumer[]
  private final ComponentClient componentClient;

  public SessionMemoryConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::consumer[]

  public Effect onSessionMemoryEvent(SessionMemoryEntity.Event event) {
    var sessionId = messageContext().eventSubject().get();

    switch (event) {
      case SessionMemoryEntity.Event.UserMessageAdded userMsg -> logger.info(
        "User message added to session {}: {}",
        sessionId,
        userMsg.message()
      );
      // ...
      // end::consumer[]
      case SessionMemoryEntity.Event.AiMessageAdded aiMsg -> {
        if (aiMsg.historySizeInBytes() > 100000) { // <1>
          var history = componentClient
            .forEventSourcedEntity(sessionId)
            .method(SessionMemoryEntity::getHistory) // <2>
            .invoke(new SessionMemoryEntity.GetHistoryCmd(Optional.empty()));

          var summary = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(CompactionAgent::summarizeSessionHistory) // <3>
            .invoke(history);

          var now = Instant.now();
          componentClient
            .forEventSourcedEntity(sessionId)
            .method(SessionMemoryEntity::compactHistory) // <4>
            .invoke(
              new SessionMemoryEntity.CompactionCmd(
                new SessionMessage.UserMessage(now, summary.userMessage(), ""),
                new SessionMessage.AiMessage(now, summary.aiMessage(), ""),
                history.sequenceNumber() // <5>
              )
            );
        }
      }
      // tag::consumer[]

      default -> logger.debug("Unhandled session memory event: {}", event);
    }

    return effects().done();
  }
}
// end::compaction[]
// end::consumer[]
