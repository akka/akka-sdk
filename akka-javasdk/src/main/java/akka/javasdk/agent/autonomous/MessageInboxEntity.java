/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import java.util.List;

/**
 * Per-agent message inbox. Stores messages from teammates, read by StrategyExecutor each iteration.
 */
@Component(id = "akka-message-inbox")
public final class MessageInboxEntity
    extends EventSourcedEntity<MessageInboxState, MessageInboxEvent> {

  @Override
  public MessageInboxState emptyState() {
    return MessageInboxState.empty();
  }

  public record SendRequest(String from, String content) {}

  public Effect<Done> send(SendRequest request) {
    return effects()
        .persist(new MessageInboxEvent.MessageReceived(request.from(), request.content()))
        .thenReply(__ -> done());
  }

  public ReadOnlyEffect<List<MessageInboxState.InboxMessage>> getUnread() {
    return effects().reply(currentState().unread());
  }

  public Effect<Done> markRead(int upToIndex) {
    if (upToIndex <= currentState().lastReadIndex()) {
      return effects().reply(done()); // already read up to this point
    }
    return effects()
        .persist(new MessageInboxEvent.MessagesMarkedRead(upToIndex))
        .thenReply(__ -> done());
  }

  @Override
  public MessageInboxState applyEvent(MessageInboxEvent event) {
    return switch (event) {
      case MessageInboxEvent.MessageReceived e -> currentState().withMessage(e.from(), e.content());
      case MessageInboxEvent.MessagesMarkedRead e -> currentState().withRead(e.upToIndex());
    };
  }
}
