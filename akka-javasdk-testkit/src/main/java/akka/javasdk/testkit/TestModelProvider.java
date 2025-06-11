/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.japi.Pair;
import akka.javasdk.agent.ModelProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A {@code ModelProvider} for tests that don't use a real AI model.
 */
public class TestModelProvider implements ModelProvider.Custom {

  private List<Pair<Predicate<String>, String>> responsePredicates = new ArrayList<>();


  @Override
  public Object createChatModel() {
    return new ChatModel() {
      @Override
      public ChatResponse doChat(ChatRequest chatRequest) {
        var userMessageText = getLastUserMessageText(chatRequest);
        var textResponse = getTextResponse(userMessageText);
        return chatResponse(textResponse);
      }

    };
  }

  @Override
  public Object createStreamingChatModel() {
    return new StreamingChatModel() {
      @Override
      public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        var userMessageText = getLastUserMessageText(chatRequest);
        var textResponse = getTextResponse(userMessageText);

        var tokens = tokenize(textResponse);
        tokens.forEach(handler::onPartialResponse);

        handler.onCompleteResponse(chatResponse(textResponse));
      }
    };
  }

  // fake tokenization by word
  List<String> tokenize(String text) {
    return Stream.of(text.splitWithDelimiters("[ \\.,?!;:]", -1))
        .filter(t -> !t.isEmpty()).toList();
  }

  private String getLastUserMessageText(ChatRequest chatRequest) {
    return Optional.ofNullable(chatRequest.messages().getLast())
        .filter(chatMessage -> chatMessage instanceof UserMessage)
        .map(userMessage -> (UserMessage) userMessage)
        .map(UserMessage::singleText)
        .orElseThrow(() -> new RuntimeException("No user message found"));
  }

  private String getTextResponse(String userMessageText) {
      return responsePredicates.stream()
        .filter(pair -> pair.first().test(userMessageText))
        .findFirst()
        .map(Pair::second)
        .orElseThrow(() -> new IllegalArgumentException("No response defined in TestModelProvider for [" + userMessageText + "]"));
  }

  private ChatResponse chatResponse(String response) {
    return new ChatResponse.Builder()
        .modelName("test-model")
        .finishReason(FinishReason.STOP)
        .aiMessage(new AiMessage(response))
        .build();
  }

  /**
   * Always return this response.
   */
  public void fixedResponse(String response) {
    mockResponse(__ -> true, response);
  }

  /**
   * Return this response for a given request that matches the predicate.
   */
  public void mockResponse(Predicate<String> predicate, String response) {
    responsePredicates.add(new Pair<>(predicate, response));
  }

  /**
   * Remove previously added responses.
   */
  public void reset() {
    responsePredicates = new ArrayList<>();
  }
}
