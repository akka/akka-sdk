package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.Agent.AgentReply;
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
            .invoke(new SessionMemoryEntity.GetHistoryCmd());

          AgentReply<CompactionAgent.Result> summaryReply = componentClient
            .forAgent()
            .inSession(sessionId)
            .method(CompactionAgent::summarizeSessionHistory) // <3>
            .withDetailedReply()
            .invoke(history);

          var now = Instant.now();
          var tokenUsage = new SessionMessage.TokenUsage(
            summaryReply.tokenUsage().inputTokens(),
            summaryReply.tokenUsage().outputTokens()
          );

          componentClient
            .forEventSourcedEntity(sessionId)
            .method(SessionMemoryEntity::compactHistory) // <4>
            .invoke(
              new SessionMemoryEntity.CompactionCmd(
                new SessionMessage.UserMessage(now, summaryReply.value().userMessage(), ""),
                new SessionMessage.AiMessage(
                  now,
                  summaryReply.value().aiMessage(),
                  "",
                  tokenUsage
                ), // <5>
                history.sequenceNumber() // <6>
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
