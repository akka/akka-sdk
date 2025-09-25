/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.InternalApi;
import akka.javasdk.CommandException;
import akka.javasdk.DependencyProvider;
import akka.javasdk.Metadata;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.agent.AgentStreamEffectImpl;
import akka.javasdk.impl.agent.BaseAgentEffectBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * An AI agent component that interacts with an AI model, such as a large language model (LLM), to
 * perform specific tasks.
 *
 * <p>An Agent is typically backed by a large language model and maintains contextual history in a
 * session memory, which may be shared between multiple agents collaborating on the same goal. It
 * can provide function tools and call them as requested by the model.
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li><strong>Session-based:</strong> Participates in a session with contextual memory
 *   <li><strong>Memory Management:</strong> Automatically stores user and AI messages for context
 *   <li><strong>Function Tools:</strong> Can be extended with custom tools for the model to invoke
 *   <li><strong>Model Integration:</strong> Supports multiple AI model providers (OpenAI,
 *       Anthropic, etc.)
 *   <li><strong>Streaming Support:</strong> Can stream responses token by token for real-time UX
 * </ul>
 *
 * <p><strong>Session Memory:</strong> The agent maintains contextual history in session memory,
 * identified by a session id accessible via {@link Agent#context()}. This memory is persistent and
 * shared between agents using the same session id.
 *
 * <p><strong>Component Identification:</strong> The agent must be annotated with {@link
 * akka.javasdk.annotations.ComponentId} to provide a unique identifier for the component class. For
 * multi-agent systems, use {@link akka.javasdk.annotations.AgentDescription} to provide metadata
 * for the {@link AgentRegistry}.
 *
 * <p><strong>Calling Agents:</strong> Agents are typically called from workflows, endpoints, or
 * consumers using the ComponentClient:
 *
 * <pre>{@code
 * String response = componentClient
 *     .forAgent()
 *     .inSession(sessionId)
 *     .method(MyAgent::query)
 *     .invoke("What is the weather like?");
 * }</pre>
 *
 * <p>For reliable execution with error handling and retries, consider calling agents from a {@link
 * akka.javasdk.workflow.Workflow}.
 */
public abstract class Agent {

  private volatile Optional<AgentContext> context = Optional.empty();

  /**
   * Additional context and metadata for a command handler.
   *
   * <p>It will throw an exception if accessed from constructor.
   */
  protected final AgentContext context() {
    return context("AgentContext is only available when handling a command.");
  }

  private AgentContext context(String errorMessage) {
    return context.orElseThrow(() -> new IllegalStateException(errorMessage));
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetContext(Optional<AgentContext> context) {
    this.context = context;
  }

  public final Effect.Builder effects() {
    return new BaseAgentEffectBuilder<>();
  }

  public final StreamEffect.Builder streamEffects() {
    return new AgentStreamEffectImpl();
  }

  /**
   * An Effect is a description of what the runtime needs to do after the command is handled. You
   * can think of it as a set of instructions you are passing to the runtime, which will process the
   * instructions on your behalf.
   *
   * <p>Each component defines its own effects, which are a set of predefined operations that match
   * the capabilities of that component.
   *
   * <p>An Agent Effect can:
   *
   * <p>
   *
   * <ul>
   *   <li>Make a request to the model and return the transformed response.
   * </ul>
   *
   * @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /** Construct the effect that is returned by the message handler. */
    interface Builder {

      /**
       * Define the AI model (LLM) to use. If undefined, the model is defined by the default
       * configuration in {@code akka.javasdk.agent.model-provider}
       */
      Builder model(ModelProvider provider);

      /**
       * Provides system-level instructions to the AI model that defines its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role,
       * constraints, and operational parameters. It is processed before user messages and helps
       * maintain consistent behavior throughout the interaction.
       */
      Builder systemMessage(String message);

      /**
       * Adds one or more tool instances or classes that the AI model can use.
       *
       * <p>Each argument can be either an object instance or a {@link Class} object. If a {@link
       * Class} is provided, it will be instantiated at runtime using the configured {@link
       * DependencyProvider}.
       *
       * <p>Each instance or class must have at least one public method annotated with {@link
       * FunctionTool}. If no such method is found, an {@link IllegalArgumentException} will be
       * thrown. These methods will be available as tools for the AI model to invoke.
       *
       * @return this builder for method chaining
       */
      Builder tools(Object tool, Object... otherTools);

      /**
       * Adds one or more tool instances or classes that the AI model can use.
       *
       * <p>Each element in the list can be either an object instance or a {@link Class} object. If
       * a {@link Class} is provided, it will be instantiated at runtime using the configured {@link
       * DependencyProvider}.
       *
       * <p>Each instance or class must have at least one public method annotated with {@link
       * FunctionTool}. If no such method is found, an {@link IllegalArgumentException} will be
       * thrown. These methods will be available as tools for the AI model to invoke.
       *
       * @param toolInstancesOrClasses one or more objects or classes exposing tool methods
       * @return this builder for method chaining
       */
      Builder tools(List<Object> toolInstancesOrClasses);

      /**
       * Create a system message from a template. Call @{@link PromptTemplate} before to initiate or
       * update template value.
       *
       * <p>Provides system-level instructions to the AI model that defines its behavior and
       * context. The system message acts as a foundational prompt that establishes the AI's role,
       * constraints, and operational parameters. It is processed before user messages and helps
       * maintain consistent behavior throughout the interaction.
       *
       * @param templateId the id of the template to use
       */
      Builder systemMessageFromTemplate(String templateId);

      /**
       * Create a system message from a template. Call @{@link PromptTemplate} before to initiate or
       * update template value. Provide arguments that will be applied to the template using Java
       * {@link String#formatted} method.
       *
       * <p>Provides system-level instructions to the AI model that defines its behavior and
       * context. The system message acts as a foundational prompt that establishes the AI's role,
       * constraints, and operational parameters. It is processed before user messages and helps
       * maintain consistent behavior throughout the interaction.
       *
       * @param templateId the id of the template to use
       * @param args the arguments to apply to the template
       */
      Builder systemMessageFromTemplate(String templateId, Object... args);

      Builder memory(MemoryProvider provider);

      /**
       * Adds tools from one or more remote MCP servers.
       *
       * <p>Construct instances using {@link RemoteMcpTools#fromServer(String)}
       */
      Builder mcpTools(RemoteMcpTools tools, RemoteMcpTools... moreTools);

      /**
       * Adds tools from one or more remote MCP servers.
       *
       * <p>Construct instances using {@link RemoteMcpTools#fromServer(String)}
       */
      Builder mcpTools(List<RemoteMcpTools> tools);

      /**
       * The user message to the AI model. This message represents the specific query, instruction,
       * or input that will be processed by the model to generate a response.
       */
      OnSuccessBuilder userMessage(String message);

      /**
       * Create a message reply without calling the model.
       *
       * @param message The payload of the reply.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> Agent.Effect<T> reply(T message);

      /**
       * Create a message reply without calling the model.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> Agent.Effect<T> reply(T message, Metadata metadata);

      /**
       * Create an error reply without calling the model. A short version of {{@code
       * effects().error(new CommandException(message))}}.
       *
       * @param message The error message.
       * @param <T> The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <T> Agent.Effect<T> error(String message);

      /**
       * Create an error reply. {@link CommandException} will be serialized and sent to the client.
       * It's possible to catch it with try-catch statement or {@link CompletionStage} API when
       * using async {@link ComponentClient} API.
       *
       * @param commandException The command exception to be returned.
       * @param <T> The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <T> Agent.Effect<T> error(CommandException commandException);
    }

    interface OnSuccessBuilder {

      /**
       * Reply with the response from the model.
       *
       * @return A message reply.
       */
      Agent.Effect<String> thenReply();

      /**
       * Reply with the response from the model.
       *
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.Effect<String> thenReply(Metadata metadata);

      /**
       * Parse the response from the model into a structured response of a given responseType. The
       * system message must have instruction or example of how the JSON should be structured.
       * Alternatively, or as a compliment, the JSON schema of the {@code responseType} can be
       * included automatically in the request by using {@link #responseConformsTo}.
       *
       * @param responseType The structured response type.
       * @see #responseConformsTo
       */
      <T> MappingResponseBuilder<T> responseAs(Class<T> responseType);

      /**
       * Parse the response from the model into a structured response of a given responseType. The
       * JSON schema of the {@code responseType} is included in the model request. At least OpenAI
       * and Google Gemini support this structured model output feature. For other models that don't
       * support it, you have to give more detailed instructions about the expected output format in
       * the system message.
       *
       * @param responseType The structured response type.
       * @see #responseAs
       */
      <T> MappingResponseBuilder<T> responseConformsTo(Class<T> responseType);

      /** Map the String response from the model into a different response type. */
      <T> MappingResponseBuilder<T> map(Function<String, T> mapper);

      /**
       * Handle failures that occur during model processing. This method allows recovery from
       * various types of exceptions including:
       *
       * <ul>
       *   <li>{@link ModelException} - General model processing failures
       *   <li>{@link RateLimitException} - API rate limiting exceeded
       *   <li>{@link ModelTimeoutException} - Model request timeout
       *   <li>{@link UnsupportedFeatureException} - Unsupported model features
       *   <li>{@link InternalServerException} - Internal service errors
       *   <li>{@link JsonParsingException} - Response parsing failures
       *   <li>{@link ToolCallLimitReachedException} - Tool call limit exceeded
       *   <li>{@link ToolCallExecutionException} - Function tool execution errors
       *   <li>{@link McpToolCallExecutionException} - MCP tool execution errors
       * </ul>
       */
      FailureBuilder<String> onFailure(Function<Throwable, String> exceptionHandler);
    }

    interface MappingResponseBuilder<Result> {

      /**
       * Reply with the response from the model.
       *
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply();

      /**
       * Reply with the response from the model.
       *
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply(Metadata metadata);

      /** Map the response from the model into a different response type. */
      <T> MappingFailureBuilder<T> map(Function<Result, T> mapper);

      /**
       * Handle failures that occur during model processing. This method allows recovery from
       * various types of exceptions including:
       *
       * <ul>
       *   <li>{@link ModelException} - General model processing failures
       *   <li>{@link RateLimitException} - API rate limiting exceeded
       *   <li>{@link ModelTimeoutException} - Model request timeout
       *   <li>{@link UnsupportedFeatureException} - Unsupported model features
       *   <li>{@link InternalServerException} - Internal service errors
       *   <li>{@link JsonParsingException} - Response parsing failures
       *   <li>{@link ToolCallLimitReachedException} - Tool call limit exceeded
       *   <li>{@link ToolCallExecutionException} - Function tool execution errors
       *   <li>{@link McpToolCallExecutionException} - MCP tool execution errors
       * </ul>
       */
      FailureBuilder<Result> onFailure(Function<Throwable, Result> exceptionHandler);
    }

    interface MappingFailureBuilder<Result> {

      /**
       * Reply with the response from the model.
       *
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply();

      /**
       * Reply with the response from the model.
       *
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply(Metadata metadata);

      /**
       * Handle failures that occur during model processing. This method allows recovery from
       * various types of exceptions including:
       *
       * <ul>
       *   <li>{@link ModelException} - General model processing failures
       *   <li>{@link RateLimitException} - API rate limiting exceeded
       *   <li>{@link ModelTimeoutException} - Model request timeout
       *   <li>{@link UnsupportedFeatureException} - Unsupported model features
       *   <li>{@link InternalServerException} - Internal service errors
       *   <li>{@link JsonParsingException} - Response parsing failures
       *   <li>{@link ToolCallLimitReachedException} - Tool call limit exceeded
       *   <li>{@link ToolCallExecutionException} - Function tool execution errors
       *   <li>{@link McpToolCallExecutionException} - MCP tool execution errors
       * </ul>
       */
      FailureBuilder<Result> onFailure(Function<Throwable, Result> exceptionHandler);
    }

    interface FailureBuilder<Result> {
      /**
       * Reply with the response from the model.
       *
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply();

      /**
       * Reply with the response from the model.
       *
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply(Metadata metadata);
    }
  }

  public interface StreamEffect {

    /** Construct the effect for token streaming that is returned by the message handler. */
    interface Builder {

      /**
       * Define the AI model (LLM) to use. If undefined, the model is defined by the default
       * configuration in {@code akka.javasdk.agent.model-provider}
       */
      Builder model(ModelProvider provider);

      /**
       * Provides system-level instructions to the AI model that defines its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role,
       * constraints, and operational parameters. It is processed before user messages and helps
       * maintain consistent behavior throughout the interaction.
       */
      Builder systemMessage(String message);

      /**
       * Create a system message from a template. Call @{@link PromptTemplate} before to initiate or
       * update template value.
       *
       * <p>Provides system-level instructions to the AI model that defines its behavior and
       * context. The system message acts as a foundational prompt that establishes the AI's role,
       * constraints, and operational parameters. It is processed before user messages and helps
       * maintain consistent behavior throughout the interaction.
       *
       * @param templateId the id of the template to use
       */
      Builder systemMessageFromTemplate(String templateId);

      /**
       * Create a system message from a template. Call @{@link PromptTemplate} before to initiate or
       * update template value. Provide arguments that will be applied to the template using Java
       * {@link String#formatted} method.
       *
       * <p>Provides system-level instructions to the AI model that defines its behavior and
       * context. The system message acts as a foundational prompt that establishes the AI's role,
       * constraints, and operational parameters. It is processed before user messages and helps
       * maintain consistent behavior throughout the interaction.
       *
       * @param templateId the id of the template to use
       * @param args the arguments to apply to the template
       */
      Builder systemMessageFromTemplate(String templateId, Object... args);

      OnSuccessBuilder userMessage(String message);

      /**
       * Adds one or more tool instances or classes that the AI model can use.
       *
       * <p>Each argument can be either an object instance or a {@link Class} object. If a {@link
       * Class} is provided, it will be instantiated at runtime using the configured {@link
       * DependencyProvider}.
       *
       * <p>Each instance or class must have at least one public method annotated with {@link
       * FunctionTool}. If no such method is found, an {@link IllegalArgumentException} will be
       * thrown. These methods will be available as tools for the AI model to invoke.
       *
       * @return this builder for method chaining
       */
      Builder tools(Object tool, Object... otherTools);

      /**
       * Adds one or more tool instances or classes that the AI model can use.
       *
       * <p>Each element in the list can be either an object instance or a {@link Class} object. If
       * a {@link Class} is provided, it will be instantiated at runtime using the configured {@link
       * DependencyProvider}.
       *
       * <p>Each instance or class must have at least one public method annotated with {@link
       * FunctionTool}. If no such method is found, an {@link IllegalArgumentException} will be
       * thrown. These methods will be available as tools for the AI model to invoke.
       *
       * @param toolInstancesOrClasses one or more objects or classes exposing tool methods
       * @return this builder for method chaining
       */
      Builder tools(List<Object> toolInstancesOrClasses);

      /**
       * Adds tools from one or more remote MCP servers.
       *
       * <p>Construct instances using {@link RemoteMcpTools#fromServer(String)}
       */
      Builder mcpTools(RemoteMcpTools tools, RemoteMcpTools... moreTools);

      /**
       * Adds tools from one or more remote MCP servers.
       *
       * <p>Construct instances using {@link RemoteMcpTools#fromServer(String)}
       */
      Builder mcpTools(List<RemoteMcpTools> tools);

      /**
       * Create a message reply without calling the model.
       *
       * @param message The payload of the reply.
       * @return A message reply.
       */
      Agent.StreamEffect reply(String message);

      /**
       * Create a message reply without calling the model.
       *
       * @param message The payload of the reply.
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.StreamEffect reply(String message, Metadata metadata);

      /**
       * Create an error reply without calling the model. A short version of {{@code
       * streamEffects().error(new CommandException(message))}}.
       *
       * @param message The error message.
       * @return An error reply.
       */
      Agent.StreamEffect error(String message);

      /**
       * Create an error reply. {@link CommandException} will be serialized and sent to the client.
       * It's possible to catch it with try-catch statement.
       *
       * @param commandException The command exception to be returned.
       * @return An error reply.
       */
      Agent.StreamEffect error(CommandException commandException);

      Builder memory(MemoryProvider provider);
    }

    interface OnSuccessBuilder {

      /**
       * Reply with the response from the model.
       *
       * @return A message reply.
       */
      Agent.StreamEffect thenReply();

      /**
       * Reply with the response from the model.
       *
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.StreamEffect thenReply(Metadata metadata);
    }
  }
}
