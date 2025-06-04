package demo.multiagent.domain;

import akka.javasdk.annotations.TypeName;

import java.time.Instant;

public sealed interface SessionEvent {

  @TypeName("user-message-added")
  record UserMessageAdded(
    String query,
    Instant timeStamp)
    implements SessionEvent {
  }

  @TypeName("ai-message-added")
  record AiMessageAdded(
    String response,
    Instant timeStamp)
    implements SessionEvent {
  }

}
