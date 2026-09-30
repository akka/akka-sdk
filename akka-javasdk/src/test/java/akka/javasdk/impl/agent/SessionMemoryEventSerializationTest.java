/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.SessionMemoryEntity.Event;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.impl.serialization.Serializer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class SessionMemoryEventSerializationTest {

  private static final Serializer serializer = new Serializer();

  static {
    List.of(
            Event.UserMessageAdded.class,
            Event.MultimodalUserMessageAdded.class,
            Event.AiMessageAdded.class,
            Event.ToolResponseMessageAdded.class,
            Event.MultimodalToolResponseMessageAdded.class)
        .forEach(serializer::registerTypeHints);
  }

  // each event record has a second constructor without the flag, Jackson must use the canonical one
  @Test
  public void shouldKeepTheFlagOfEachEventThroughSerialization() {
    var ts = Instant.parse("2026-01-01T00:00:00Z");
    var contents =
        List.<SessionMessage.MessageContent>of(
            new SessionMessage.MessageContent.TextMessageContent("text"));
    var events =
        List.<Event.Message>of(
            new Event.UserMessageAdded(ts, "agent", "hello", 5, true),
            new Event.MultimodalUserMessageAdded(ts, "agent", contents, 4, true),
            new Event.AiMessageAdded(
                ts,
                "agent",
                "hi",
                2,
                7,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                true),
            new Event.ToolResponseMessageAdded(ts, "agent", "call-1", "search", "result", 6, true),
            new Event.MultimodalToolResponseMessageAdded(
                ts, "agent", "call-1", "render", contents, 4, true));

    for (var event : events) {
      var read = serializer.fromBytes(serializer.toBytes(event));
      assertThat(read).isEqualTo(event);
    }
  }
}
