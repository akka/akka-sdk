/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
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


  public static class MissingModelResponseException extends RuntimeException {
  }

  final private Function<InputMessage, AiResponse> catchMissingResponse = msg -> {
    throw new MissingModelResponseException();
  };

  private void addPredicateOnly(Predicate<InputMessage> predicate) {
    responsePredicates.add(new Pair<>(predicate, catchMissingResponse));
  }
    
  private void addResponse(Predicate<InputMessage> predicate, Function<InputMessage, AiResponse> response) {

    var catchMissingResponse = new Pair<>(predicate, this.catchMissingResponse);

    // Remove any existing entry with the same predicate that maps to catchMissingResponse
    responsePredicates.removeIf(pair -> pair.equals(catchMissingResponse));

    // Add the new predicate-response mapping
    responsePredicates.add(new Pair<>(predicate, response));
  }


  /**
   * Base class for building reply configurations for specific input predicates.
   */
  public static class WhenClause {

    protected final TestModelProvider provider;
    protected final Predicate<InputMessage> predicate;

    public WhenClause(TestModelProvider provider, Predicate<InputMessage> predicate) {
      this.provider = provider;
      this.predicate = predicate;
      provider.addPredicateOnly(predicate);
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
      provider.addResponse(predicate, msg -> response);
    }

    /**
     * Reply with a runtime exception for matching requests.
     */
    public void failWith(RuntimeException error) {
      provider.addResponse(predicate, msg -> { throw error; });
    }
  }


  /**
   * Specialized reply builder for handling tool result messages.
   */
  public static class WhenToolReplyClause extends WhenClause  {

    public WhenToolReplyClause(TestModelProvider provider, Predicate<InputMessage> predicate) {
      super(provider, predicate);
    }

    /**
     * Configures a reply with a custom handler for a tool result message.
     */
    public void thenReply(Function<ToolResult, AiResponse> handler) {
      // safe to cast because predicate ensures the type
      Function<InputMessage, AiResponse> castedHandler =
        (input) -> handler.apply((ToolResult) input);
      provider.addResponse(predicate, castedHandler);
    }

  }

  @Override
  public Object createChatModel() {
    return new ChatModel() {
      @Override
      public ChatResponse doChat(ChatRequest chatRequest) {
        var inputMessage = getLastInputMessage(chatRequest);
        var textResponse = getResponse(inputMessage);
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
        .map(pair -> {
            try {
               return pair.second().apply(inputMessage);
            } catch (MissingModelResponseException e) {
              throw new IllegalArgumentException("A matching predicate was defined for [" + inputMessage + "]," +
                " but no reply was defined for it. Please use reply(...), thenReply(...) or failWith(...) methods to " +
                "provide a reply for this input message.", e);
            }
          }
        )
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
   * Configures to respond when the passed string predicate matches the content of an {@link InputMessage}.
   * The predicate is applied to content of {@link UserMessage} or {@link ToolResult}.
   * <p>
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenClause whenMessage(Predicate<String> predicate) {

    Predicate<InputMessage> messagePredicate = (value) -> predicate.test(value.content());

    return new WhenClause(this, messagePredicate);
  }

  /**
   * Configures to respond when the content of an {@link InputMessage} is an exact match of the passed {@code message}.
   * <p>
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenClause whenMessage(String message) {
    Predicate<InputMessage> messagePredicate = (value) -> message.equals(value.content());

    return new WhenClause(this, messagePredicate);
  }

  /**
   * Configures to respond when the given predicate matches a {@link UserMessage}.
   * The predicate is applied to {@link UserMessage} instances only.
   * <p>
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenClause whenUserMessage(Predicate<UserMessage> predicate) {

    Predicate<InputMessage> messagePredicate =
        (value) -> value instanceof UserMessage uMsg && predicate.test(uMsg);

    return new WhenClause(this, messagePredicate);
  }

  /**
   * Configures to respond when an {@link UserMessage} is an exact match of {@code userMessage}.
   * <p>
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenClause whenUserMessage(UserMessage userMessage) {

    Predicate<InputMessage> messagePredicate =
      (value) -> value instanceof UserMessage uMsg && userMessage.equals(uMsg);

    return new WhenClause(this, messagePredicate);
  }


  /**
   * Configures to respond when the given predicate matches a {@link ToolResult}.
   * The predicate is applied to {@link ToolResult} instances only.
   * <p>
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenToolReplyClause whenToolResult(Predicate<ToolResult> predicate){

    Predicate<InputMessage> messagePredicate =
        (value) -> value instanceof ToolResult toolResult && predicate.test(toolResult);

    return new WhenToolReplyClause(this, messagePredicate);
  }

  /**
   * Configures to respond when an {@link ToolResult} is an exact match of {@code toolResult}.
   * <p>
   * Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenToolReplyClause whenToolResult(ToolResult toolResult){

    Predicate<InputMessage> messagePredicate =
      (value) -> value instanceof ToolResult tResult && toolResult.equals(tResult);

    return new WhenToolReplyClause(this, messagePredicate);
  }


  /**
   * Resets all previously added response configurations.
   */
  public void reset() {
    responsePredicates = new ArrayList<>();
  }
}