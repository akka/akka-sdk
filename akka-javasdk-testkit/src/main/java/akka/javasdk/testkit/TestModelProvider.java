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
 * A {@code ModelProvider} implementation for testing purposes that does not use a real AI model.
 * It allows defining mock responses based on input predicates.
 */
public class TestModelProvider implements ModelProvider.Custom {

  /**
   * Represents an AI response, which can include a message and/or list of tool invocation requests.
   */
  public record AiResponse(String message, List<ToolInvocationRequest> toolRequests) {

    /**
     * Constructs an AI response with only a message.
     */
    public AiResponse(String message) {
      this(message, List.of());
    }

    /**
     * Constructs an AI response with a message and a single tool request.
     */
    public AiResponse(String message, ToolInvocationRequest toolRequest) {
      this(message, List.of(toolRequest));
    }

    /**
     * Constructs an AI response with only a tool request.
     */
    public AiResponse(ToolInvocationRequest toolRequest) {
      this("", List.of(toolRequest));
    }

    /**
     * Constructs an AI response with a list of tool invocation requests.
     */
    public AiResponse(List<ToolInvocationRequest> toolRequests) {
      this("", toolRequests);
    }
  }

  /**
   * Represents a tool invocation request with a name and arguments.
   */
  public record ToolInvocationRequest(String name, String arguments) {
  }

  /**
   * Represents an input message.
   * Can be a user input message {@link UserMessage} or the result of a tool invocation {@link ToolResult}.
   */
  public sealed interface InputMessage {
    String content();
  }

  /**
   * Represents a user message.
   */
  public record UserMessage(String content) implements InputMessage {}

  /**
   * Represents a tool result.
   * This is used to simulate a response from a tool invocation.
   */
  public record ToolResult(String name, String content) implements InputMessage {}

  private List<Pair<Predicate<InputMessage>, Function<InputMessage, AiResponse>>> responsePredicates = new ArrayList<>();

  /**
   * Base class for building reply configurations for specific input predicates.
   */
  public static class BaseReplyBuilder {

    protected final TestModelProvider provider;
    protected final Predicate<InputMessage> predicate;

    public BaseReplyBuilder(TestModelProvider provider, Predicate<InputMessage> predicate) {
      this.provider = provider;
      this.predicate = predicate;
    }

    /**
     * Reply with a simple message for matching requests.
     */
    public void reply(String message) {
      reply(new AiResponse(message));
    }

    /**
     * Reply with a tool invocation request for matching requests.
     */
    public void reply(ToolInvocationRequest request) {
      reply(new AiResponse(request));
    }

    /**
     * Reply with a list of tool invocation requests for matching requests.
     */
    public void reply(List<ToolInvocationRequest> requests) {
      reply(new AiResponse(requests));
    }

    /**
     * Reply with a custom AI response for matching requests.
     */
    public void reply(AiResponse response) {
      provider.responsePredicates.add(new Pair<>(predicate, msg -> response));
    }

    /**
     * Reply with a runtime exception for matching requests.
     */
    public void failWith(RuntimeException error) {
      provider.responsePredicates.add(new Pair<>(predicate, msg -> { throw error; }));
    }
  }

  /**
   * Specialized reply builder for handling tool result messages.
   */
  public static class ToolResultReplyBuilder extends BaseReplyBuilder {

    public ToolResultReplyBuilder(TestModelProvider provider, Predicate<InputMessage> predicate) {
      super(provider, predicate);
    }

    /**
     * Configures a reply with a custom handler for tool result messages.
     */
    public void thenReply(Function<ToolResult, AiResponse> handler) {
      // safe to cast because messagePredicate ensures the type
      Function<InputMessage, AiResponse> castedHandler =
        (input) -> handler.apply((ToolResult) input);
      provider.responsePredicates.add(new Pair<>(predicate, castedHandler));
    }
  }

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

  /**
   * Tokenizes a string into words using delimiters.
   */
  List<String> tokenize(String text) {
    return Stream.of(text.splitWithDelimiters("[ \\.,?!;:]", -1))
        .filter(t -> !t.isEmpty()).toList();
  }

  /**
   * Extracts the last input message from a chat request.
   */
  private InputMessage getLastInputMessage(ChatRequest chatRequest) {
    return Optional.ofNullable(chatRequest.messages().getLast())
        .filter(chatMessage -> chatMessage instanceof dev.langchain4j.data.message.UserMessage || chatMessage instanceof ToolExecutionResultMessage)
        .map(chatMessage ->  {
          if (chatMessage instanceof dev.langchain4j.data.message.UserMessage userMessage) {
            return new UserMessage(userMessage.singleText());
          } else {
            ToolExecutionResultMessage result = (ToolExecutionResultMessage) chatMessage;
            return new ToolResult(result.toolName(), result.text());
          }
        })
        .orElseThrow(() -> new RuntimeException("No input message found"));
  }

  /**
   * Retrieves the AI response for a given input message based on the defined predicates.
   */
  private AiResponse getResponse(InputMessage inputMessage) {
      return responsePredicates.stream()
        .filter(pair -> pair.first().test(inputMessage))
        .findFirst()
        .map(pair -> pair.second().apply(inputMessage))
        .orElseThrow(() -> new IllegalArgumentException("No response defined in TestModelProvider for [" + inputMessage + "]"));
  }

  /**
   * Constructs a ChatResponse object from an AI response.
   */
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
   * Configures a fixed response for all input messages.
   */
  public void fixedResponse(String response) {
    whenMessage(msg -> true).reply(response);
  }

  /**
   * Configures the {@code TestModelProvider} to respond when the given string predicate matches an input message.
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public BaseReplyBuilder whenMessage(Predicate<String> predicate) {

    Predicate<InputMessage> messagePredicate = (value) -> predicate.test(value.content());

    return new BaseReplyBuilder(this, messagePredicate);
  }

  /**
   * Configures the {@code TestModelProvider} to respond when the given UserMessage predicate matches an input message.
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public BaseReplyBuilder whenUserMessage(Predicate<UserMessage> predicate) {

    Predicate<InputMessage> messagePredicate =
        (value) -> value instanceof UserMessage userQuestion && predicate.test(userQuestion);

    return new BaseReplyBuilder(this, messagePredicate);
  }

  /**
   * Configures the {@code TestModelProvider} to respond when the given ToolResult predicate matches a tool result.
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public ToolResultReplyBuilder whenToolResult(Predicate<ToolResult> predicate) {

    Predicate<InputMessage> messagePredicate =
        (value) -> value instanceof ToolResult toolResult && predicate.test(toolResult);

    return new ToolResultReplyBuilder(this, messagePredicate);
  }

  /**
   * Resets all previously added response configurations.
   */
  public void reset() {
    responsePredicates = new ArrayList<>();
  }
}