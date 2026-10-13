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
                ts, "agent", "call-1", "render", contents, 4, true),
            new Event.ToolResponseMessageAdded(
                ts, "agent", "call-1", "search", "failed", 6, false, true),
            new Event.MultimodalToolResponseMessageAdded(
                ts, "agent", "call-1", "render", contents, 4, true, true));

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

  // tool response events stored before the error flag existed have no field, read it as false
  @Test
  public void shouldReadAToolResponseEventWithoutTheErrorFlagAsNotAnError() {
    var json =
        """
        {"timestamp":"2026-01-01T00:00:00Z","componentId":"agent","id":"call-1","name":"search",\
        "content":"result","sizeInBytes":6,"sanitized":true}\
        """;

    var read =
        serializer.fromBytes(
            new BytesPayload(
                ByteString.fromString(json),
                "json.akka.io/akka-memory-tool-response-message-added"));

    assertThat(read)
        .isEqualTo(
            new Event.ToolResponseMessageAdded(
                Instant.parse("2026-01-01T00:00:00Z"),
                "agent",
                "call-1",
                "search",
                "result",
                6,
                true,
                false));
  }

  @Test
  public void shouldReadAMultimodalToolResponseEventWithoutTheErrorFlagAsNotAnError() {
    var json =
        """
        {"timestamp":"2026-01-01T00:00:00Z","componentId":"agent","id":"call-1","name":"render",\
        "contents":[{"type":"T","text":"text"}],"sizeInBytes":4,"sanitized":true}\
        """;

    var read =
        serializer.fromBytes(
            new BytesPayload(
                ByteString.fromString(json),
                "json.akka.io/akka-memory-multimodal-tool-response-message-added"));

    assertThat(read)
        .isEqualTo(
            new Event.MultimodalToolResponseMessageAdded(
                Instant.parse("2026-01-01T00:00:00Z"),
                "agent",
                "call-1",
                "render",
                List.of(new SessionMessage.MessageContent.TextMessageContent("text")),
                4,
                true,
                false));
  }

  // the stored field name must stay "isError" for events already written
  @Test
  public void shouldWriteTheErrorFlagOfEachToolResponseEventAsIsError() {
    var ts = Instant.parse("2026-01-01T00:00:00Z");
    var contents =
        List.<SessionMessage.MessageContent>of(
            new SessionMessage.MessageContent.TextMessageContent("text"));
    var events =
        List.<Event.Message>of(
            new Event.ToolResponseMessageAdded(
                ts, "agent", "call-1", "search", "failed", 6, false, true),
            new Event.MultimodalToolResponseMessageAdded(
                ts, "agent", "call-1", "render", contents, 4, false, true));

    for (var event : events) {
      var json = serializer.toBytes(event).bytes().utf8String();
      assertThat(json).contains("\"isError\":true").doesNotContain("\"error\"");
    }
  }

  // snapshots hold the messages, a tool response stored before the error flag existed has no field
  @Test
  public void shouldReadAStoredToolResponseWithoutTheErrorFlagAsNotAnError() throws Exception {
    var json =
        """
        {"@type":"TCR","timestamp":"2026-01-01T00:00:00Z","componentId":"agent","id":"call-1",\
        "name":"search","text":"result","sanitized":true}\
        """;

    var read = serializer.objectMapper().readValue(json, SessionMessage.class);

    assertThat(read)
        .isEqualTo(
            new SessionMessage.ToolCallResponse(
                Instant.parse("2026-01-01T00:00:00Z"),
                "agent",
                "call-1",
                "search",
                "result",
                true,
                false));
  }

  @Test
  public void shouldReadAStoredMultimodalToolResponseWithoutTheErrorFlagAsNotAnError()
      throws Exception {
    var json =
        """
        {"@type":"MTCR","timestamp":"2026-01-01T00:00:00Z","componentId":"agent","id":"call-1",\
        "name":"render","contents":[{"type":"T","text":"text"}],"sanitized":true}\
        """;

    var read = serializer.objectMapper().readValue(json, SessionMessage.class);

    assertThat(read)
        .isEqualTo(
            new SessionMessage.MultimodalToolCallResponse(
                Instant.parse("2026-01-01T00:00:00Z"),
                "agent",
                "call-1",
                "render",
                List.of(new SessionMessage.MessageContent.TextMessageContent("text")),
                true,
                false));
  }

  @Test
  public void shouldKeepTheErrorFlagOfAStoredToolResponse() throws Exception {
    var ts = Instant.parse("2026-01-01T00:00:00Z");
    var contents =
        List.<SessionMessage.MessageContent>of(
            new SessionMessage.MessageContent.TextMessageContent("text"));
    var messages =
        List.<SessionMessage>of(
            new SessionMessage.ToolCallResponse(
                ts, "agent", "call-1", "search", "failed", true, true),
            new SessionMessage.MultimodalToolCallResponse(
                ts, "agent", "call-1", "render", contents, false, true));

    for (var message : messages) {
      var json = serializer.objectMapper().writeValueAsString(message);
      assertThat(json).contains("\"isError\":true");
      assertThat(serializer.objectMapper().readValue(json, SessionMessage.class))
          .isEqualTo(message);
    }
  }
}
