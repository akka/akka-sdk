/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import java.util.ArrayList;
import java.util.List;

/** State of a per-agent message inbox. */
public record MessageInboxState(List<InboxMessage> messages, int lastReadIndex) {

  public record InboxMessage(String from, String content) {}

  public static MessageInboxState empty() {
    return new MessageInboxState(List.of(), 0);
  }

  public MessageInboxState withMessage(String from, String content) {
    var updated = new ArrayList<>(messages);
    updated.add(new InboxMessage(from, content));
    return new MessageInboxState(updated, lastReadIndex);
  }

  public MessageInboxState withRead(int upToIndex) {
    return new MessageInboxState(messages, upToIndex);
  }

  public List<InboxMessage> unread() {
    if (lastReadIndex >= messages.size()) {
      return List.of();
    }
    return List.copyOf(messages.subList(lastReadIndex, messages.size()));
  }
}
