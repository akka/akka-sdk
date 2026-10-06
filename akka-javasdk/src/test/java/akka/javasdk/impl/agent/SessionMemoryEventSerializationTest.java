/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.SessionMemoryEntity.Event;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.impl.serialization.Serializer;
import akka.runtime.sdk.spi.BytesPayload;
import akka.util.ByteString;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SessionMemoryEventSerializationTest {

  private static final Serializer serializer = new Serializer();

  static {
    List.of(
            Event.UserMessageAdded.class,
            Event.MultimodalUserMessageAdded.class,
            Event.ToolResponseMessageAdded.class,
            Event.MultimodalToolResponseMessageAdded.class)
        .forEach(serializer::registerTypeHints);
  }

  // each event record with the flag has a second constructor without it, Jackson must use the
  // canonical one
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
            new Event.ToolResponseMessageAdded(ts, "agent", "call-1", "search", "result", 6, true),
            new Event.MultimodalToolResponseMessageAdded(
                ts, "agent", "call-1", "render", contents, 4, true));

    for (var event : events) {
      var read = serializer.fromBytes(serializer.toBytes(event));
      assertThat(read).isEqualTo(event);
    }
  }

  // events stored before the flag existed have no field, the mapper must read it as false
  @Test
  public void shouldReadAnEventWithoutTheFlagAsNotSanitized() {
    var json =
        """
        {"timestamp":"2026-01-01T00:00:00Z","componentId":"agent","message":"hello","sizeInBytes":5}\
        """;

    var read =
        serializer.fromBytes(
            new BytesPayload(
                ByteString.fromString(json), "json.akka.io/akka-memory-user-message-added"));

    assertThat(read)
        .isEqualTo(
            new Event.UserMessageAdded(
                Instant.parse("2026-01-01T00:00:00Z"), "agent", "hello", 5, false));
  }
}
