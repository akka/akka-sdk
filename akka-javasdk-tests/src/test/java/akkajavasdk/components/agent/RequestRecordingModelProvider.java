/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import akka.javasdk.agent.ModelProvider;
import akka.javasdk.testkit.TestModelProvider;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Replies with a {@link TestModelProvider}, and records the messages of each request, history
 * included, which the {@link TestModelProvider} does not show.
 */
final class RequestRecordingModelProvider implements ModelProvider.Custom {

  private final TestModelProvider replies;
  private final Queue<List<ChatMessage>> requests = new ConcurrentLinkedQueue<>();

  RequestRecordingModelProvider(TestModelProvider replies) {
    this.replies = replies;
  }

  List<List<ChatMessage>> requests() {
    return List.copyOf(requests);
  }

  List<ChatMessage> lastRequest() {
    return requests().getLast();
  }

  void reset() {
    requests.clear();
  }

  static <T extends ChatMessage> List<T> messagesOf(List<ChatMessage> request, Class<T> type) {
    return request.stream().filter(type::isInstance).map(type::cast).toList();
  }

  @Override
  public String modelName() {
    return replies.modelName();
  }

  @Override
  public Object createChatModel() {
    var model = (ChatModel) replies.createChatModel();
    return new ChatModel() {
      @Override
      public ChatResponse doChat(ChatRequest request) {
        requests.add(request.messages());
        return model.doChat(request);
      }
    };
  }

  @Override
  public Object createStreamingChatModel() {
    var model = (StreamingChatModel) replies.createStreamingChatModel();
    return new StreamingChatModel() {
      @Override
      public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        requests.add(request.messages());
        model.doChat(request, handler);
      }
    };
  }
}
