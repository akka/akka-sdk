/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import akka.javasdk.agent.SessionHistory;
import akka.javasdk.agent.SessionMemory;
import akka.javasdk.agent.SessionMemoryInterceptor;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.MultimodalUserMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.SessionMessage.UserMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class InterceptingSessionMemoryTest {

  /** Records every call made to it for inspection in assertions. */
  static class RecordingSessionMemory implements SessionMemory {
    final List<String> calls = new ArrayList<>();
    List<SessionMessage> lastMessages = List.of();

    @Override
    public void addInteraction(
        String sessionId, UserMessage userMessage, List<SessionMessage> messages) {
      calls.add("text:" + sessionId + ":[" + userMessage.text() + "]");
      lastMessages = messages;
    }

    @Override
    public void addInteraction(
        String sessionId, MultimodalUserMessage userMessage, List<SessionMessage> messages) {
      var texts =
          userMessage.contents().stream()
              .map(c -> ((SessionMessage.MessageContent.TextMessageContent) c).text())
              .collect(Collectors.joining("|"));
      calls.add("multimodal:" + sessionId + ":[" + texts + "]");
      lastMessages = messages;
    }

    @Override
    public SessionHistory getHistory(String sessionId) {
      calls.add("getHistory:" + sessionId);
      return SessionHistory.EMPTY;
    }
  }

  private UserMessage userMessage(String text) {
    return new UserMessage(Instant.EPOCH, text, "test-component");
  }

  private MultimodalUserMessage multimodalMessage() {
    return new MultimodalUserMessage(
        Instant.EPOCH,
        List.of(new SessionMessage.MessageContent.TextMessageContent("hi")),
        "test-component");
  }

  @Test
  public void identityInterceptorPassesThroughBothOverloads() {
    var delegate = new RecordingSessionMemory();
    var interceptor = new SessionMemoryInterceptor() {};
    var memory = new InterceptingSessionMemory(delegate, interceptor);

    memory.addInteraction("s1", userMessage("hello"), List.of());
    memory.addInteraction("s2", multimodalMessage(), List.of());

    assertEquals(List.of("text:s1:[hello]", "multimodal:s2:[hi]"), delegate.calls);
  }

  @Test
  public void textTransformIsReflectedInDelegate() {
    var delegate = new RecordingSessionMemory();
    var interceptor =
        new SessionMemoryInterceptor() {
          @Override
          public UserMessage beforeWrite(String sessionId, UserMessage userMessage) {
            return new UserMessage(userMessage.timestamp(), "redacted", userMessage.componentId());
          }
        };
    var memory = new InterceptingSessionMemory(delegate, interceptor);

    memory.addInteraction("s1", userMessage("secret"), List.of());
    memory.addInteraction("s1", multimodalMessage(), List.of());

    assertEquals(List.of("text:s1:[redacted]", "multimodal:s1:[hi]"), delegate.calls);
  }

  @Test
  public void multimodalTransformIsReflectedInDelegate() {
    var delegate = new RecordingSessionMemory();
    var interceptor =
        new SessionMemoryInterceptor() {
          @Override
          public MultimodalUserMessage beforeWrite(
              String sessionId, MultimodalUserMessage userMessage) {
            return new MultimodalUserMessage(
                userMessage.timestamp(),
                List.of(
                    new SessionMessage.MessageContent.TextMessageContent("a"),
                    new SessionMessage.MessageContent.TextMessageContent("b")),
                userMessage.componentId());
          }
        };
    var memory = new InterceptingSessionMemory(delegate, interceptor);

    memory.addInteraction("s1", userMessage("hi"), List.of());
    memory.addInteraction("s2", multimodalMessage(), List.of());

    assertEquals(List.of("text:s1:[hi]", "multimodal:s2:[a|b]"), delegate.calls);
  }

  @Test
  public void aiMessageTransformInMessagesListIsReflectedInDelegate() {
    var delegate = new RecordingSessionMemory();
    var interceptor =
        new SessionMemoryInterceptor() {
          @Override
          public AiMessage beforeWrite(String sessionId, AiMessage aiMessage) {
            // strip thinking
            return new AiMessage(
                aiMessage.timestamp(),
                aiMessage.text(),
                aiMessage.componentId(),
                aiMessage.toolCallRequests(),
                Optional.empty(), // remove thinking
                aiMessage.tokenUsage(),
                aiMessage.attributes());
          }
        };
    var memory = new InterceptingSessionMemory(delegate, interceptor);

    var ai =
        new AiMessage(
            Instant.EPOCH, "answer", "agent", List.of(), Optional.of("internal thoughts"));
    memory.addInteraction("s1", userMessage("hi"), List.of(ai));

    assertEquals(1, delegate.lastMessages.size());
    var seen = (AiMessage) delegate.lastMessages.get(0);
    assertEquals(Optional.empty(), seen.thinking());
    assertEquals("answer", seen.text());
  }

  @Test
  public void toolCallResponseTransformInMessagesListIsReflectedInDelegate() {
    var delegate = new RecordingSessionMemory();
    var interceptor =
        new SessionMemoryInterceptor() {
          @Override
          public ToolCallResponse beforeWrite(String sessionId, ToolCallResponse tcr) {
            return new ToolCallResponse(
                tcr.timestamp(), tcr.componentId(), tcr.id(), tcr.name(), "this is a short one");
          }
        };
    var memory = new InterceptingSessionMemory(delegate, interceptor);

    var tcr =
        new ToolCallResponse(Instant.EPOCH, "agent", "id1", "search", "this is a long response");
    memory.addInteraction("s1", userMessage("hi"), List.of(tcr));

    assertEquals(1, delegate.lastMessages.size());
    var seen = (ToolCallResponse) delegate.lastMessages.get(0);
    assertEquals("this is a short one", seen.text());
  }
}
