/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.japi.Pair;
import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskDefinition;
import akka.javasdk.agent.task.TaskTemplate;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.ComponentId;
import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@code ModelProvider} implementation for testing purposes that does not use a real AI model. It
 * allows defining mock responses based on input predicates.
 */
public final class TestModelProvider implements ModelProvider.Custom {

  @Override
  public String modelName() {
    return "test-model";
  }

  /**
   * Represents an AI response, which can include a message and/or list of tool invocation requests.
   */
  public record AiResponse(
      String message,
      List<ToolInvocationRequest> toolRequests,
      Optional<Agent.TokenUsage> tokenUsage) {

    /** Constructs an AI response with only a message. */
    public AiResponse(String message) {
      this(message, List.of(), Optional.empty());
    }

    /** Constructs an AI response with a message and a single tool request. */
    public AiResponse(String message, ToolInvocationRequest toolRequest) {
      this(message, List.of(toolRequest), Optional.empty());
    }

    /** Constructs an AI response with only a tool request. */
    public AiResponse(ToolInvocationRequest toolRequest) {
      this("", List.of(toolRequest), Optional.empty());
    }

    /** Constructs an AI response with a list of tool invocation requests. */
    public AiResponse(List<ToolInvocationRequest> toolRequests) {
      this("", toolRequests, Optional.empty());
    }
  }

  /** Represents a tool invocation request with a name and arguments. */
  public record ToolInvocationRequest(String name, String arguments) {}

  /**
   * Represents an input message. Can be a user input message {@link UserMessage} or the result of a
   * tool invocation {@link ToolResult}.
   */
  public sealed interface InputMessage {
    String content();
  }

  /** Represents a user message. */
  public record UserMessage(List<MessageContent> contents) implements InputMessage {

    public UserMessage(String content) {
      this(List.of(MessageContent.TextMessageContent.from(content)));
    }

    @Override
    public String content() {
      if (isTextOnly()) {
        return text();
      } else {
        throw new IllegalStateException("This is not text only user message");
      }
    }

    public boolean isTextOnly() {
      return contents.size() == 1
          && contents.getFirst() instanceof MessageContent.TextMessageContent;
    }

    public String text() {
      return ((MessageContent.TextMessageContent) contents.getFirst()).text();
    }
  }

  /** Represents a tool result. This is used to simulate a response from a tool invocation. */
  public record ToolResult(String name, String content) implements InputMessage {}

  private List<Pair<Predicate<InputMessage>, Function<InputMessage, AiResponse>>>
      responsePredicates = new ArrayList<>();

  private Function<List<InputMessage>, InputMessage> messageSelector = List::getLast;

  public static class MissingModelResponseException extends RuntimeException {}

  private final Function<InputMessage, AiResponse> catchMissingResponse =
      msg -> {
        throw new MissingModelResponseException();
      };

  private void addPredicateOnly(Predicate<InputMessage> predicate) {
    responsePredicates.addFirst(new Pair<>(predicate, catchMissingResponse));
  }

  private void addResponse(
      Predicate<InputMessage> predicate, Function<InputMessage, AiResponse> response) {

    var catchMissingResponse = new Pair<>(predicate, this.catchMissingResponse);

    // Remove any existing entry with the same predicate that maps to catchMissingResponse
    responsePredicates.removeIf(pair -> pair.equals(catchMissingResponse));

    // Add the new predicate-response mapping
    responsePredicates.addFirst(new Pair<>(predicate, response));
  }

  /** Base class for building reply configurations for specific input predicates. */
  public static sealed class WhenClause {

    final TestModelProvider provider;
    final Predicate<InputMessage> predicate;

    public WhenClause(TestModelProvider provider, Predicate<InputMessage> predicate) {
      this.provider = provider;
      this.predicate = predicate;
      provider.addPredicateOnly(predicate);
    }

    /** Reply with a simple message for matching requests. */
    public void reply(String message) {
      reply(new AiResponse(message));
    }

    /** Reply with a simple message for matching requests with defined token usage. */
    public void reply(String message, Agent.TokenUsage tokenUsage) {
      reply(new AiResponse(message, List.of(), Optional.of(tokenUsage)));
    }

    /** Reply with a tool invocation request for matching requests. */
    public void reply(ToolInvocationRequest request) {
      reply(new AiResponse(request));
    }

    /** Reply with a list of tool invocation requests for matching requests. */
    public void reply(List<ToolInvocationRequest> requests) {
      reply(new AiResponse(requests));
    }

    /** Reply with a custom AI response for matching requests. */
    public void reply(AiResponse response) {
      provider.addResponse(predicate, msg -> response);
    }

    /** Reply dynamically based on the input message. Evaluated on each matching request. */
    public void reply(Function<InputMessage, AiResponse> handler) {
      provider.addResponse(predicate, handler);
    }

    /** Reply with a runtime exception for matching requests. */
    public void failWith(RuntimeException error) {
      provider.addResponse(
          predicate,
          msg -> {
            throw error;
          });
    }
  }

  /** Specialized reply builder for handling tool result messages. */
  public static final class WhenToolReplyClause extends WhenClause {

    public WhenToolReplyClause(TestModelProvider provider, Predicate<InputMessage> predicate) {
      super(provider, predicate);
    }

    /** Configures a reply with a custom handler for a tool result message. */
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
        var inputMessage = selectInputMessage(chatRequest);
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
        var inputMessage = selectInputMessage(chatRequest);
        var response = getResponse(inputMessage);

        if (response.message != null) {
          var tokens = tokenize(response.message);
          tokens.forEach(handler::onPartialResponse);
        }

        handler.onCompleteResponse(chatResponse(response));
      }
    };
  }

  /** Tokenizes a string into words using delimiters. */
  List<String> tokenize(String text) {
    return Stream.of(text.splitWithDelimiters("[ \\.,?!;:]", -1))
        .filter(t -> !t.isEmpty())
        .toList();
  }

  /**
   * Selects the input message to respond to, using the configured message selector applied to all
   * new input messages since the last AI response.
   */
  private InputMessage selectInputMessage(ChatRequest chatRequest) {
    var newMessages = getNewInputMessages(chatRequest);
    if (newMessages.isEmpty()) {
      throw new RuntimeException("No input message found");
    }
    return messageSelector.apply(newMessages);
  }

  /**
   * Extracts all input messages since the last AI response in the conversation. These are the new
   * messages for the current turn — tool results and user messages that the model should react to.
   */
  private List<InputMessage> getNewInputMessages(ChatRequest chatRequest) {
    var messages = chatRequest.messages();
    var result = new ArrayList<InputMessage>();
    // Walk backwards from the end, collecting user messages and tool results,
    // stopping at the last AI message (which was our previous response).
    for (int i = messages.size() - 1; i >= 0; i--) {
      var msg = messages.get(i);
      if (msg instanceof AiMessage) {
        break;
      }
      var converted = toInputMessage(msg);
      if (converted != null) {
        result.addFirst(converted);
      }
    }
    return result;
  }

  /** Converts a langchain4j chat message to an {@link InputMessage}, or null if not applicable. */
  private InputMessage toInputMessage(dev.langchain4j.data.message.ChatMessage chatMessage) {
    if (chatMessage instanceof dev.langchain4j.data.message.UserMessage userMessage) {
      List<MessageContent> contents =
          userMessage.contents().stream()
              .<MessageContent>map(
                  content ->
                      switch (content) {
                        case TextContent textContent ->
                            MessageContent.TextMessageContent.from(textContent.text());
                        case ImageContent imageContent -> {
                          if (imageContent.image().url() != null) {
                            try {
                              yield MessageContent.ImageMessageContent.fromUrl(
                                  imageContent.image().url().toURL(),
                                  toDetailLevel(imageContent.detailLevel()));
                            } catch (MalformedURLException e) {
                              throw new RuntimeException(
                                  "Can't transform " + imageContent.image().url() + " to URL", e);
                            }
                          } else {
                            throw new IllegalStateException(
                                "Not supported image content without url.");
                          }
                        }
                        default ->
                            throw new IllegalStateException(
                                "Not supported content type: " + content);
                      })
              .toList();
      return new UserMessage(contents);
    } else if (chatMessage instanceof ToolExecutionResultMessage toolResult) {
      return new ToolResult(toolResult.toolName(), toolResult.text());
    }
    return null;
  }

  private MessageContent.ImageMessageContent.DetailLevel toDetailLevel(
      ImageContent.DetailLevel detailLevel) {
    return switch (detailLevel) {
      case LOW -> MessageContent.ImageMessageContent.DetailLevel.LOW;
      case MEDIUM -> MessageContent.ImageMessageContent.DetailLevel.MEDIUM;
      case HIGH -> MessageContent.ImageMessageContent.DetailLevel.HIGH;
      case ULTRA_HIGH -> MessageContent.ImageMessageContent.DetailLevel.ULTRA_HIGH;
      case AUTO -> MessageContent.ImageMessageContent.DetailLevel.AUTO;
    };
  }

  /** Retrieves the AI response for a given input message based on the defined predicates. */
  private AiResponse getResponse(InputMessage inputMessage) {
    return responsePredicates.stream()
        .filter(pair -> pair.first().test(inputMessage))
        .findFirst()
        .map(
            pair -> {
              try {
                return pair.second().apply(inputMessage);
              } catch (MissingModelResponseException e) {
                throw new IllegalArgumentException(
                    "A matching predicate was defined for ["
                        + inputMessage
                        + "], but no reply was defined for it. Please use reply(...),"
                        + " thenReply(...) or failWith(...) methods to provide a reply for this"
                        + " input message.",
                    e);
              }
            })
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No response defined in TestModelProvider for [" + inputMessage + "]"));
  }

  /** Constructs a ChatResponse object from an AI response. */
  private ChatResponse chatResponse(AiResponse response) {

    var builder = new ChatResponse.Builder().modelName("test-model");

    response.tokenUsage.ifPresent(
        tokenUsage -> {
          builder.tokenUsage(
              new dev.langchain4j.model.output.TokenUsage(
                  tokenUsage.inputTokens(), tokenUsage.outputTokens()));
        });

    if (!response.toolRequests.isEmpty()) {
      var requests =
          response.toolRequests.stream()
              .map(
                  req ->
                      ToolExecutionRequest.builder()
                          .id(UUID.randomUUID().toString())
                          .name(req.name)
                          .arguments(req.arguments)
                          .build())
              .toList();

      var aiMessage = AiMessage.from(response.message, requests);

      return builder.aiMessage(aiMessage).finishReason(FinishReason.TOOL_EXECUTION).build();

    } else {
      return builder
          .finishReason(FinishReason.STOP)
          .aiMessage(new AiMessage(response.message))
          .build();
    }
  }

  /** Configures a fixed response for all input messages. */
  public void fixedResponse(String response) {
    new WhenClause(this, inputMessage -> true).reply(response);
  }

  /** Configures a fixed {@link AiResponse} for all input messages. */
  public void fixedResponse(AiResponse response) {
    new WhenClause(this, inputMessage -> true).reply(response);
  }

  /** Configures a fixed tool invocation response for all input messages. */
  public void fixedResponse(ToolInvocationRequest request) {
    new WhenClause(this, inputMessage -> true).reply(new AiResponse(request));
  }

  /** Configures a dynamic response for each input message. */
  public void fixedResponse(Function<InputMessage, AiResponse> handler) {
    new WhenClause(this, inputMessage -> true).reply(handler);
  }

  /**
   * Configures to respond when the passed string predicate matches the content of an {@link
   * InputMessage}. The predicate is applied to content of {@link UserMessage} or {@link
   * ToolResult}.
   *
   * <p>Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenClause whenMessage(Predicate<String> predicate) {

    Predicate<InputMessage> messagePredicate = (value) -> predicate.test(value.content());

    return new WhenClause(this, messagePredicate);
  }

  /**
   * Configures to respond when the content of an {@link InputMessage} is an exact match of the
   * passed {@code message}.
   *
   * <p>Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenClause whenMessage(String message) {
    Predicate<InputMessage> messagePredicate = (value) -> message.equals(value.content());

    return new WhenClause(this, messagePredicate);
  }

  /**
   * Configures to respond when the given predicate matches a {@link UserMessage}. The predicate is
   * applied to {@link UserMessage} instances only.
   *
   * <p>Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenClause whenUserMessage(Predicate<UserMessage> predicate) {

    Predicate<InputMessage> messagePredicate =
        (value) -> value instanceof UserMessage uMsg && predicate.test(uMsg);

    return new WhenClause(this, messagePredicate);
  }

  /**
   * Configures to respond when an {@link UserMessage} is an exact match of {@code userMessage}.
   *
   * <p>Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenClause whenUserMessage(UserMessage userMessage) {

    Predicate<InputMessage> messagePredicate =
        (value) -> value instanceof UserMessage uMsg && userMessage.equals(uMsg);

    return new WhenClause(this, messagePredicate);
  }

  /**
   * Configures to respond when the given predicate matches a {@link ToolResult}. The predicate is
   * applied to {@link ToolResult} instances only.
   *
   * <p>Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenToolReplyClause whenToolResult(Predicate<ToolResult> predicate) {

    Predicate<InputMessage> messagePredicate =
        (value) -> value instanceof ToolResult toolResult && predicate.test(toolResult);

    return new WhenToolReplyClause(this, messagePredicate);
  }

  /**
   * Configures to respond when an {@link ToolResult} is an exact match of {@code toolResult}.
   *
   * <p>Note that to finish the configuration, you must call one of the reply methods.
   */
  public WhenToolReplyClause whenToolResult(ToolResult toolResult) {

    Predicate<InputMessage> messagePredicate =
        (value) -> value instanceof ToolResult tResult && toolResult.equals(tResult);

    return new WhenToolReplyClause(this, messagePredicate);
  }

  /**
   * Configures a custom message selector that determines which input message from the current turn
   * is passed to response predicates. The selector receives all new input messages since the last
   * AI response (tool results and user messages) and returns the one to respond to.
   *
   * <p>The default selector picks the last message.
   */
  public TestModelProvider withMessageSelector(
      Function<List<InputMessage>, InputMessage> selector) {
    this.messageSelector = selector;
    return this;
  }

  /**
   * Factory methods for {@link ToolInvocationRequest} instances that correspond to the internal
   * tools exposed by the autonomous agent runtime to the LLM.
   */
  public static final class AutonomousAgentTools {

    private AutonomousAgentTools() {}

    // --- Tool name constants ---

    /** Tool name for completing a task. */
    public static final String COMPLETE_TASK = "complete_task";

    /** Tool name for failing a task. */
    public static final String FAIL_TASK = "fail_task";

    /** Tool name for creating a team. */
    public static final String CREATE_TEAM = "create_team";

    /** Tool name for getting team status. */
    public static final String GET_TEAM_STATUS = "get_team_status";

    /** Tool name for disbanding a team. */
    public static final String DISBAND_TEAM = "disband_team";

    /** Tool name for getting managed backlog status (lead side). */
    public static final String GET_MANAGED_BACKLOG_STATUS = "get_managed_backlog_status";

    /** Tool name for cancelling unclaimed tasks from backlog (lead side). */
    public static final String CANCEL_UNCLAIMED_TASKS_FROM_BACKLOG =
        "cancel_unclaimed_tasks_from_backlog";

    /** Tool name for getting backlog status (member side). */
    public static final String GET_BACKLOG_STATUS = "get_backlog_status";

    /** Tool name for claiming a task (member side). */
    public static final String CLAIM_TASK = "claim_task";

    /** Tool name for releasing a task (member side). */
    public static final String RELEASE_TASK = "release_task";

    /** Tool name for transferring a task (member side). */
    public static final String TRANSFER_TASK = "transfer_task";

    /** Tool name for sending a message. */
    public static final String SEND_MESSAGE = "send_message";

    /**
     * Returns the tool name for a {@code handoff_to_<agent>} tool, derived from the target agent's
     * component ID.
     */
    public static String handoffToToolName(Class<?> agentClass) {
      return "handoff_to_" + sanitize(componentId(agentClass));
    }

    /**
     * Returns the tool name for a {@code delegate_<task>_to_<agent>} tool, derived from the task
     * definition and target agent's component ID.
     */
    public static String delegateToToolName(TaskDefinition<?> task, Class<?> agentClass) {
      return "delegate_" + sanitize(task.name()) + "_to_" + sanitize(componentId(agentClass));
    }

    /**
     * Returns the tool name for a {@code create_<taskType>_task_for_backlog} tool, derived from the
     * task definition name.
     */
    public static String createTaskForBacklogToolName(TaskDefinition<?> task) {
      return "create_" + sanitize(task.name()) + "_task_for_backlog";
    }

    /**
     * Returns the tool name for a {@code send_<Method>_to_<agent>} tool, derived from the method
     * name and target agent's component ID.
     */
    public static String sendToToolName(Class<?> agentClass, String methodName) {
      var capitalizedMethod = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
      return "send_" + sanitize(capitalizedMethod) + "_to_" + sanitize(componentId(agentClass));
    }

    // --- Tool invocation request factory methods ---

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code complete_task} tool, with the given
     * result JSON. The JSON must conform to the task's result type schema.
     */
    public static ToolInvocationRequest completeTask(String resultJson) {
      return new ToolInvocationRequest(COMPLETE_TASK, resultJson);
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code complete_task} tool, serializing the
     * given result object to JSON.
     */
    public static ToolInvocationRequest completeTask(Object result) {
      try {
        return new ToolInvocationRequest(
            COMPLETE_TASK, JsonSupport.getObjectMapper().writeValueAsString(result));
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Failed to serialize task result to JSON", e);
      }
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code fail_task} tool.
     *
     * @param reason the failure reason
     */
    public static ToolInvocationRequest failTask(String reason) {
      return new ToolInvocationRequest("fail_task", "{\"reason\":" + toJsonString(reason) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for a {@code handoff_to_<agent>} tool, deriving the
     * tool name from the target agent's component ID.
     *
     * @param agentClass the agent class to hand off to (must carry {@code @Component} or
     *     {@code @ComponentId})
     * @param context context passed to the receiving agent
     */
    public static ToolInvocationRequest handoffTo(Class<?> agentClass, String context) {
      var toolName = "handoff_to_" + sanitize(componentId(agentClass));
      return new ToolInvocationRequest(toolName, "{\"context\":" + toJsonString(context) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for a {@code delegate_<task>_to_<agent>} tool,
     * deriving the tool name from the task definition and the target agent's component ID.
     *
     * @param task the task being delegated
     * @param agentClass the agent class to delegate to (must carry {@code @Component} or
     *     {@code @ComponentId})
     * @param instructions instructions passed to the delegated agent
     */
    public static ToolInvocationRequest delegateTo(
        Task<?> task, Class<?> agentClass, String instructions) {
      var toolName =
          "delegate_" + sanitize(task.name()) + "_to_" + sanitize(componentId(agentClass));
      return new ToolInvocationRequest(
          toolName, "{\"instructions\":" + toJsonString(instructions) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for a {@code delegate_<task>_to_<agent>} tool,
     * filling template parameters for a {@link TaskTemplate}.
     *
     * @param task the task template being delegated
     * @param agentClass the agent class to delegate to (must carry {@code @Component} or
     *     {@code @ComponentId})
     * @param templateParams values for the template parameters
     */
    public static ToolInvocationRequest delegateTo(
        TaskTemplate<?> task, Class<?> agentClass, Map<String, String> templateParams) {
      var toolName =
          "delegate_" + sanitize(task.name()) + "_to_" + sanitize(componentId(agentClass));
      return new ToolInvocationRequest(toolName, templateParamsToJson(templateParams));
    }

    /**
     * Creates a {@link ToolInvocationRequest} for a {@code send_<Method>_to_<agent>} tool, used
     * when an autonomous agent delegates to a request-based agent. The method name has its first
     * letter capitalized to match the runtime naming convention.
     *
     * @param agentClass the request-based agent class (must carry {@code @Component} or
     *     {@code @ComponentId})
     * @param methodName the exact name of the agent method to invoke
     * @param argsJson the JSON arguments to pass (must match the method's parameter type)
     */
    public static ToolInvocationRequest sendTo(
        Class<?> agentClass, String methodName, String argsJson) {
      var capitalizedMethod = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
      var toolName =
          "send_" + sanitize(capitalizedMethod) + "_to_" + sanitize(componentId(agentClass));
      return new ToolInvocationRequest(toolName, argsJson);
    }

    // --- Team capability ---

    /** Specifies a team member type and count for a {@code create_team} tool invocation. */
    public record TeamMemberSpec(Class<?> agentClass, int count) {
      public TeamMemberSpec(Class<?> agentClass) {
        this(agentClass, 1);
      }
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code create_team} tool.
     *
     * @param members the team member specifications (type derived from component ID, with count)
     */
    public static ToolInvocationRequest createTeam(TeamMemberSpec... members) {
      var membersJson =
          java.util.Arrays.stream(members)
              .map(
                  m ->
                      "{\"type\":"
                          + toJsonString(componentId(m.agentClass))
                          + ",\"count\":"
                          + m.count
                          + "}")
              .collect(java.util.stream.Collectors.joining(",", "[", "]"));
      return new ToolInvocationRequest("create_team", "{\"members\":" + membersJson + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code get_team_status} tool. The {@code
     * team_id} is optional when there is only one active team.
     */
    public static ToolInvocationRequest getTeamStatus() {
      return new ToolInvocationRequest("get_team_status", "{}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code get_team_status} tool with a specific
     * team ID. Required when multiple teams are active.
     */
    public static ToolInvocationRequest getTeamStatus(String teamId) {
      return new ToolInvocationRequest(
          "get_team_status", "{\"team_id\":" + toJsonString(teamId) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code disband_team} tool. The {@code
     * team_id} is optional when there is only one active team.
     */
    public static ToolInvocationRequest disbandTeam() {
      return new ToolInvocationRequest("disband_team", "{}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code disband_team} tool with a specific
     * team ID. Required when multiple teams are active.
     */
    public static ToolInvocationRequest disbandTeam(String teamId) {
      return new ToolInvocationRequest(
          "disband_team", "{\"team_id\":" + toJsonString(teamId) + "}");
    }

    // --- Backlog capability (managed/orchestrator side) ---

    /**
     * Creates a {@link ToolInvocationRequest} for a {@code create_<taskType>_task_for_backlog}
     * tool. The {@code backlog_id} is auto-resolved when there is only one backlog.
     *
     * <p>For {@link TaskTemplate} definitions, use the overload that accepts a {@code Map} of
     * template parameter values instead.
     *
     * @param task the task definition to create
     * @param instructions instructions for the task
     */
    public static ToolInvocationRequest createTaskForBacklog(Task<?> task, String instructions) {
      var toolName = "create_" + sanitize(task.name()) + "_task_for_backlog";
      return new ToolInvocationRequest(
          toolName, "{\"instructions\":" + toJsonString(instructions) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for a {@code create_<taskType>_task_for_backlog}
     * tool, filling template parameters for a {@link TaskTemplate}. The {@code backlog_id} is
     * auto-resolved when there is only one backlog.
     *
     * @param task the task template to create
     * @param templateParams values for the template parameters (e.g. "feature", "requirements")
     */
    public static ToolInvocationRequest createTaskForBacklog(
        TaskTemplate<?> task, Map<String, String> templateParams) {
      var toolName = "create_" + sanitize(task.name()) + "_task_for_backlog";
      return new ToolInvocationRequest(toolName, templateParamsToJson(templateParams));
    }

    /**
     * Creates a {@link ToolInvocationRequest} for a {@code create_<taskType>_task_for_backlog} tool
     * with a specific backlog ID. Required when multiple backlogs exist.
     *
     * @param task the task definition to create
     * @param backlogId the backlog ID
     * @param instructions instructions for the task
     */
    public static ToolInvocationRequest createTaskForBacklog(
        Task<?> task, String backlogId, String instructions) {
      var toolName = "create_" + sanitize(task.name()) + "_task_for_backlog";
      return new ToolInvocationRequest(
          toolName,
          "{\"backlog_id\":"
              + toJsonString(backlogId)
              + ",\"instructions\":"
              + toJsonString(instructions)
              + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for a {@code create_<taskType>_task_for_backlog} tool
     * with a specific backlog ID, filling template parameters for a {@link TaskTemplate}.
     *
     * @param task the task template to create
     * @param backlogId the backlog ID
     * @param templateParams values for the template parameters
     */
    public static ToolInvocationRequest createTaskForBacklog(
        TaskTemplate<?> task, String backlogId, Map<String, String> templateParams) {
      var toolName = "create_" + sanitize(task.name()) + "_task_for_backlog";
      var params = new java.util.LinkedHashMap<>(templateParams);
      return new ToolInvocationRequest(
          toolName,
          "{\"backlog_id\":"
              + toJsonString(backlogId)
              + ","
              + templateParamsToJson(params).substring(1));
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code get_managed_backlog_status} tool. The
     * {@code backlog_id} is auto-resolved when there is only one backlog.
     */
    public static ToolInvocationRequest getManagedBacklogStatus() {
      return new ToolInvocationRequest("get_managed_backlog_status", "{}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code get_managed_backlog_status} tool with
     * a specific backlog ID. Required when multiple backlogs exist.
     */
    public static ToolInvocationRequest getManagedBacklogStatus(String backlogId) {
      return new ToolInvocationRequest(
          "get_managed_backlog_status", "{\"backlog_id\":" + toJsonString(backlogId) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code cancel_unclaimed_tasks_from_backlog}
     * tool. The {@code backlog_id} is auto-resolved when there is only one backlog.
     */
    public static ToolInvocationRequest cancelUnclaimedTasksFromBacklog() {
      return new ToolInvocationRequest("cancel_unclaimed_tasks_from_backlog", "{}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code cancel_unclaimed_tasks_from_backlog}
     * tool with a specific backlog ID. Required when multiple backlogs exist.
     */
    public static ToolInvocationRequest cancelUnclaimedTasksFromBacklog(String backlogId) {
      return new ToolInvocationRequest(
          "cancel_unclaimed_tasks_from_backlog",
          "{\"backlog_id\":" + toJsonString(backlogId) + "}");
    }

    // --- Backlog capability (worker/consumer side) ---

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code get_backlog_status} tool. The {@code
     * backlog_id} is auto-resolved when there is only one backlog.
     */
    public static ToolInvocationRequest getBacklogStatus() {
      return new ToolInvocationRequest("get_backlog_status", "{}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code get_backlog_status} tool with a
     * specific backlog ID. Required when multiple backlogs exist.
     */
    public static ToolInvocationRequest getBacklogStatus(String backlogId) {
      return new ToolInvocationRequest(
          "get_backlog_status", "{\"backlog_id\":" + toJsonString(backlogId) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code claim_task} tool.
     *
     * @param taskId the ID of the task to claim
     */
    public static ToolInvocationRequest claimTask(String taskId) {
      return new ToolInvocationRequest("claim_task", "{\"task_id\":" + toJsonString(taskId) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code claim_task} tool with a specific
     * backlog ID. Required when multiple backlogs exist.
     *
     * @param backlogId the backlog ID
     * @param taskId the ID of the task to claim
     */
    public static ToolInvocationRequest claimTask(String backlogId, String taskId) {
      return new ToolInvocationRequest(
          "claim_task",
          "{\"backlog_id\":"
              + toJsonString(backlogId)
              + ",\"task_id\":"
              + toJsonString(taskId)
              + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code release_task} tool.
     *
     * @param taskId the ID of the task to release
     */
    public static ToolInvocationRequest releaseTask(String taskId) {
      return new ToolInvocationRequest(
          "release_task", "{\"task_id\":" + toJsonString(taskId) + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code release_task} tool with a specific
     * backlog ID. Required when multiple backlogs exist.
     *
     * @param backlogId the backlog ID
     * @param taskId the ID of the task to release
     */
    public static ToolInvocationRequest releaseTask(String backlogId, String taskId) {
      return new ToolInvocationRequest(
          "release_task",
          "{\"backlog_id\":"
              + toJsonString(backlogId)
              + ",\"task_id\":"
              + toJsonString(taskId)
              + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code transfer_task} tool.
     *
     * @param taskId the ID of the task to transfer
     * @param transferredTo identifier of the agent to transfer the task to
     */
    public static ToolInvocationRequest transferTask(String taskId, String transferredTo) {
      return new ToolInvocationRequest(
          "transfer_task",
          "{\"task_id\":"
              + toJsonString(taskId)
              + ",\"transferred_to\":"
              + toJsonString(transferredTo)
              + "}");
    }

    /**
     * Creates a {@link ToolInvocationRequest} for the {@code transfer_task} tool with a specific
     * backlog ID. Required when multiple backlogs exist.
     *
     * @param backlogId the backlog ID
     * @param taskId the ID of the task to transfer
     * @param transferredTo identifier of the agent to transfer the task to
     */
    public static ToolInvocationRequest transferTask(
        String backlogId, String taskId, String transferredTo) {
      return new ToolInvocationRequest(
          "transfer_task",
          "{\"backlog_id\":"
              + toJsonString(backlogId)
              + ",\"task_id\":"
              + toJsonString(taskId)
              + ",\"transferred_to\":"
              + toJsonString(transferredTo)
              + "}");
    }

    // --- Messaging capability ---

    /**
     * Creates a {@link ToolInvocationRequest} for the fixed {@code send_message} tool.
     *
     * @param message the message content to send
     */
    public static ToolInvocationRequest sendMessage(String message) {
      return new ToolInvocationRequest(
          "send_message", "{\"message\":" + toJsonString(message) + "}");
    }

    private static String componentId(Class<?> agentClass) {
      var component = agentClass.getAnnotation(Component.class);
      if (component != null && !component.id().isEmpty()) return component.id();
      var componentId = agentClass.getAnnotation(ComponentId.class);
      if (componentId != null && !componentId.value().isEmpty()) return componentId.value();
      throw new IllegalArgumentException(
          agentClass.getName() + " is not annotated with @Component(id = ...) or @ComponentId");
    }

    private static String sanitize(String name) {
      return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String toJsonString(String value) {
      return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String templateParamsToJson(Map<String, String> params) {
      return params.entrySet().stream()
          .map(e -> toJsonString(e.getKey()) + ":" + toJsonString(e.getValue()))
          .collect(Collectors.joining(",", "{", "}"));
    }
  }

  /** Resets all previously added response configurations. */
  public void reset() {
    responsePredicates = new ArrayList<>();
    messageSelector = List::getLast;
  }
}
