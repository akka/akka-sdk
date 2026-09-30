/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.SessionMemoryEntity.Event;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.agent.SessionMessageConverter;
import akka.javasdk.impl.serialization.Serializer;
import akka.runtime.sdk.spi.BytesPayload;
import akka.util.ByteString;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reads session memory message events in the JSON form they have without the {@code sanitized}
 * field, as events written before the field existed.
 */
public class SessionMemoryEventCompatibilityTest {

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

  private static Object fromJson(String typeName, String json) {
    return serializer.fromBytes(
        new BytesPayload(ByteString.fromString(json), "json.akka.io/" + typeName));
  }

  @Test
  public void shouldReadAUserMessageEventWithoutTheFlagAsNotSanitized() {
    var event =
        (Event.UserMessageAdded)
            fromJson(
                "akka-memory-user-message-added",
                """
                {"timestamp":"2026-01-01T00:00:00Z","componentId":"agent","message":"hello",\
                "sizeInBytes":5}\
                """);

    assertThat(event.message()).isEqualTo("hello");
    assertThat(event.sanitized()).isFalse();
    assertThat(((SessionMessage.UserMessage) SessionMessageConverter.apply(event)).sanitized())
        .isFalse();
  }

  @Test
  public void shouldReadAMultimodalUserMessageEventWithoutTheFlagAsNotSanitized() {
    var event =
        (Event.MultimodalUserMessageAdded)
            fromJson(
                "akka-memory-multimodal-user-message-added",
                """
                {"timestamp":"2026-01-01T00:00:00Z","componentId":"agent",\
                "contents":[{"type":"T","text":"look"},\
                {"type":"IU","uri":"https://example.com/cat.png","detailLevel":"AUTO",\
                "mimeType":"image/png"}],"sizeInBytes":36}\
                """);

    assertThat(event.contents()).hasSize(2);
    assertThat(event.sanitized()).isFalse();
    assertThat(
            ((SessionMessage.MultimodalUserMessage) SessionMessageConverter.apply(event))
                .sanitized())
        .isFalse();
  }

  @Test
  public void shouldReadAnAiMessageEventWithoutTheFlagAsNotSanitized() {
    var event =
        (Event.AiMessageAdded)
            fromJson(
                "akka-memory-ai-message-added",
                """
                {"timestamp":"2026-01-01T00:00:00Z","componentId":"agent","message":"hi",\
                "sizeInBytes":2,"historySizeInBytes":7,\
                "toolCallRequests":[{"id":"call-1","name":"search","arguments":"{}"}],\
                "thinking":null,"tokenUsage":{"inputTokens":1,"outputTokens":2},\
                "attributes":{}}\
                """);

    assertThat(event.message()).isEqualTo("hi");
    assertThat(event.toolCallRequests()).hasSize(1);
    assertThat(event.sanitized()).isFalse();
    assertThat(((SessionMessage.AiMessage) SessionMessageConverter.apply(event)).sanitized())
        .isFalse();
  }

  @Test
  public void shouldReadAToolResponseEventWithoutTheFlagAsNotSanitized() {
    var event =
        (Event.ToolResponseMessageAdded)
            fromJson(
                "akka-memory-tool-response-message-added",
                """
                {"timestamp":"2026-01-01T00:00:00Z","componentId":"agent","id":"call-1",\
                "name":"search","content":"result","sizeInBytes":6}\
                """);

    assertThat(event.content()).isEqualTo("result");
    assertThat(event.sanitized()).isFalse();
    assertThat(((SessionMessage.ToolCallResponse) SessionMessageConverter.apply(event)).sanitized())
        .isFalse();
  }

  @Test
  public void shouldReadAMultimodalToolResponseEventWithoutTheFlagAsNotSanitized() {
    var event =
        (Event.MultimodalToolResponseMessageAdded)
            fromJson(
                "akka-memory-multimodal-tool-response-message-added",
                """
                {"timestamp":"2026-01-01T00:00:00Z","componentId":"agent","id":"call-1",\
                "name":"render","contents":[{"type":"PU","uri":"https://example.com/doc.pdf"}],\
                "sizeInBytes":27}\
                """);

    assertThat(event.contents()).hasSize(1);
    assertThat(event.sanitized()).isFalse();
    assertThat(
            ((SessionMessage.MultimodalToolCallResponse) SessionMessageConverter.apply(event))
                .sanitized())
        .isFalse();
  }

  /** {@link Event.UserMessageAdded} without the {@code sanitized} component. */
  record UserMessageAddedWithoutFlag(
      Instant timestamp, String componentId, String message, int sizeInBytes) {}

  @Test
  public void shouldIgnoreTheFlagWhenReadIntoAnEventWithoutIt() {
    var payload =
        serializer.toBytes(
            new Event.UserMessageAdded(
                Instant.parse("2026-01-01T00:00:00Z"), "agent", "hello", 5, true));
    assertThat(payload.bytes().utf8String()).contains("\"sanitized\":true");

    var event = serializer.fromBytes(UserMessageAddedWithoutFlag.class, payload);

    assertThat(event.message()).isEqualTo("hello");
    assertThat(event.sizeInBytes()).isEqualTo(5);
  }
}
