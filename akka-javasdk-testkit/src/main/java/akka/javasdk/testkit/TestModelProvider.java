/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.japi.Pair;
import akka.javasdk.agent.ModelProvider;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A {@code ModelProvider} for tests that don't use a real AI model.
 */
public class TestModelProvider implements ModelProvider.Custom {


  public record AiResponse(String message, List<ToolInvocationRequest> toolRequests) {

    public AiResponse(String message) {
      this(message, List.of());
    }

    public AiResponse(String message, ToolInvocationRequest toolRequest) {
      this(message, List.of(toolRequest));
    }

    public AiResponse(ToolInvocationRequest toolRequest) {
      this("", List.of(toolRequest));
    }

    public AiResponse(List<ToolInvocationRequest> toolRequests) {
      this("", toolRequests);
    }
  }
  public record ToolInvocationRequest(String name, String arguments) {
  }

  sealed interface InputMessage {
    String content();
  }
  public record UserQuestion(String content) implements InputMessage {}
  public record ToolResult(String name, String content) implements InputMessage {}

  private List<Pair<Predicate<InputMessage>, Function<InputMessage, AiResponse>>> responsePredicates = new ArrayList<>();


  @Override
  public Object createChatModel() {
    return new ChatModel() {
      @Override
      public ChatResponse doChat(ChatRequest chatRequest) {
        var inputMessageText = getLastInputMessage(chatRequest);
        var textResponse = getResponse(inputMessageText);
        return chatResponse(textResponse);
      }

    };
  }

  @Override
  public Object createStreamingChatModel() {
    return new StreamingChatModel() {
      @Override
      public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        var inputMessage = getLastInputMessage(chatRequest);
        var response = getResponse(inputMessage);

        if (response.message != null) {
          var tokens = tokenize(response.message);
          tokens.forEach(handler::onPartialResponse);
        }

        handler.onCompleteResponse(chatResponse(response));
      }
    };
  }

  // fake tokenization by word
  List<String> tokenize(String text) {
    return Stream.of(text.splitWithDelimiters("[ \\.,?!;:]", -1))
        .filter(t -> !t.isEmpty()).toList();
  }

  private InputMessage getLastInputMessage(ChatRequest chatRequest) {
    return Optional.ofNullable(chatRequest.messages().getLast())
        .filter(chatMessage -> chatMessage instanceof dev.langchain4j.data.message.UserMessage || chatMessage instanceof ToolExecutionResultMessage)
        .map(chatMessage ->  {
          if (chatMessage instanceof dev.langchain4j.data.message.UserMessage userMessage) {
            return new UserQuestion(userMessage.singleText());
          } else {
            ToolExecutionResultMessage result = (ToolExecutionResultMessage) chatMessage;
            return new ToolResult(result.toolName(), result.text());
          }
        })
        .orElseThrow(() -> new RuntimeException("No input message found"));
  }

  private AiResponse getResponse(InputMessage inputMessage) {
      return responsePredicates.stream()
        .filter(pair -> pair.first().test(inputMessage))
        .findFirst()
        .map(pair -> pair.second().apply(inputMessage))
        .orElseThrow(() -> new IllegalArgumentException("No response defined in TestModelProvider for [" + inputMessage + "]"));
  }

  private ChatResponse chatResponse(AiResponse response) {

    var builder = new ChatResponse.Builder().modelName("test-model");

    if (!response.toolRequests.isEmpty()) {
      var requests =
        response.toolRequests.stream().map(req ->
          ToolExecutionRequest.builder()
            .id(UUID.randomUUID().toString())
            .name(req.name)
            .arguments(req.arguments)
            .build()
        ).toList();

      var aiMessage = AiMessage.from(response.message, requests);

      return builder
        .aiMessage(aiMessage)
        .finishReason(FinishReason.TOOL_EXECUTION)
        .build();

    } else {
      return builder
        .finishReason(FinishReason.STOP).aiMessage(new AiMessage(response.message))
        .build();
    }

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
    Predicate<InputMessage> messagePredicate = (value) -> predicate.test(value.content());
    responsePredicates.add(new Pair<>(messagePredicate, msg -> new AiResponse(response)));
  }

  public void mockResponse(Predicate<String> predicate, AiResponse response) {
    Predicate<InputMessage> messagePredicate = (value) -> predicate.test(value.content());
    responsePredicates.add(new Pair<>(messagePredicate, msg -> response));
  }

  public void mockResponseToToolResult(Predicate<ToolResult> predicate, Function<ToolResult, AiResponse> handler) {
    Predicate<InputMessage> messagePredicate = (value) -> {
      if (value instanceof ToolResult response)
        return predicate.test(response);
      else return false;
    };

    // safe to cast because messagePredicate is protecting it
    Function<InputMessage, AiResponse>  castedHandler =
      (input) -> handler.apply((ToolResult) input);

    responsePredicates.add(new Pair<>(messagePredicate, castedHandler));
  }


  /**
   * Remove previously added responses.
   */
  public void reset() {
    responsePredicates = new ArrayList<>();
  }
}
