/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.annotations.TypeName;

public sealed interface MessageInboxEvent {

  @TypeName("akka-inbox-message-received")
  record MessageReceived(String from, String content) implements MessageInboxEvent {}

  @TypeName("akka-inbox-messages-read")
  record MessagesMarkedRead(int upToIndex) implements MessageInboxEvent {}
}
