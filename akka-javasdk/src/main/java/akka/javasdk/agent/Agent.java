/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.InternalApi;
import akka.javasdk.Metadata;
import akka.javasdk.impl.agent.BaseAgentEffectBuilder;
import akka.javasdk.impl.agent.AgentStreamEffectImpl;

import java.util.Optional;
import java.util.function.Function;

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
   * An Effect is a description of what the runtime needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to the runtime, which will process
   * the instructions on your behalf.
   * <p>
   * Each component defines its own effects, which are a set of predefined
   * operations that match the capabilities of that component.
   * <p>
   * An Agent Effect can:
   * <p>
   * <ul>
   *   <li>Make a request to the model and return the transformed response.
   * </ul>
   *
   * @param <T> The type of the message that must be returned by this call.
   */
  public interface Effect<T> {

    /**
     * Construct the effect that is returned by the message handler.
     */
    interface Builder {

      /**
       * Define the model (LLM) to use.
       * If undefined, the model is defined by the default configuration in
       * {@code akka.javasdk.agent.model-provider}
       */
      Builder model(ModelProvider provider);

      /**
       * Provides system-level instructions to the AI model that defines its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role, constraints,
       * and operational parameters. It is processed before user messages and helps maintain consistent
       * behavior throughout the interaction.
       */
      Builder systemMessage(String message);

      /**
       * Create a system message from a template. Call @{@link PromptTemplate} before to initiate or update template value.
       * <p>
       * Provides system-level instructions to the AI model that defines its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role, constraints,
       * and operational parameters. It is processed before user messages and helps maintain consistent
       * behavior throughout the interaction.
       *
       * @param templateId the id of the template to use
       */
      Builder systemMessageFromTemplate(String templateId);

      /**
       * Create a system message from a template. Call @{@link PromptTemplate} before to initiate or update template value.
       * Provide arguments that will be applied to the template using Java {@link String#formatted} method.
       * <p>
       * Provides system-level instructions to the AI model that defines its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role, constraints,
       * and operational parameters. It is processed before user messages and helps maintain consistent
       * behavior throughout the interaction.
       *
       * @param templateId the id of the template to use
       * @param args the arguments to apply to the template
       */
      Builder systemMessageFromTemplate(String templateId, Object... args);

      Builder memory(MemoryProvider provider);

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
       * Create an error reply without calling the model
       *
       * @param description The description of the error.
       * @param <T> The type of the message that must be returned by this call.
       * @return An error reply.
       */
      <T> Agent.Effect<T> error(String description);

    }

    interface OnSuccessBuilder {

      /**
       * Reply with the response from the model.
       * @return A message reply.
       */
      Agent.Effect<String> thenReply();

      /**
       * Reply with the response from the model.
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.Effect<String> thenReply(Metadata metadata);

      /**
       * Parse the response from the model into a structured response of a given responseType.
       * @param responseType The structured response type.
       */
      <T> MappingResponseBuilder<T> responseAs(Class<T> responseType);

      /**
       * Map the String response from the model into a different response type.
       */
      <T> MappingResponseBuilder<T> map(Function<String, T> mapper);

      FailureBuilder<String> onFailure(Function<Throwable, String> exceptionHandler);
    }

    interface MappingResponseBuilder<Result> {

      /**
       * Reply with the response from the model.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply();

      /**
       * Reply with the response from the model.
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply(Metadata metadata);

      /**
       * Map the response from the model into a different response type.
       */
      <T> MappingFailureBuilder<T> map(Function<Result, T> mapper);

      FailureBuilder<Result> onFailure(Function<Throwable, Result> exceptionHandler);
    }

    interface MappingFailureBuilder<Result> {

      /**
       * Reply with the response from the model.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply();

      /**
       * Reply with the response from the model.
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply(Metadata metadata);

      FailureBuilder<Result> onFailure(Function<Throwable, Result> exceptionHandler);
    }

    interface FailureBuilder<Result> {
      /**
       * Reply with the response from the model.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply();

      /**
       * Reply with the response from the model.
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.Effect<Result> thenReply(Metadata metadata);
    }

  }

  public interface StreamEffect {

    /**
     * Construct the effect for token streaming that is returned by the message handler.
     */
    interface Builder {

      /**
       * Define the model (LLM) to use.
       * If undefined, the model is defined by the default configuration in
       * {@code akka.javasdk.agent.model-provider}
       */
      Builder model(ModelProvider provider);

      /**
       * Provides system-level instructions to the AI model that defines its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role, constraints,
       * and operational parameters. It is processed before user messages and helps maintain consistent
       * behavior throughout the interaction.
       */
      Builder systemMessage(String message);

      /**
       * Create a system message from a template. Call @{@link PromptTemplate} before to initiate or update template value.
       * <p>
       * Provides system-level instructions to the AI model that defines its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role, constraints,
       * and operational parameters. It is processed before user messages and helps maintain consistent
       * behavior throughout the interaction.
       *
       * @param templateId the id of the template to use
       */
      Builder systemMessageFromTemplate(String templateId);

      /**
       * Create a system message from a template. Call @{@link PromptTemplate} before to initiate or update template value.
       * Provide arguments that will be applied to the template using Java {@link String#formatted} method.
       * <p>
       * Provides system-level instructions to the AI model that defines its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role, constraints,
       * and operational parameters. It is processed before user messages and helps maintain consistent
       * behavior throughout the interaction.
       *
       * @param templateId the id of the template to use
       * @param args the arguments to apply to the template
       */
      Builder systemMessageFromTemplate(String templateId, Object... args);

      OnSuccessBuilder userMessage(String message);

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
       * Create an error reply without calling the model
       *
       * @param description The description of the error.
       * @return An error reply.
       */
      Agent.StreamEffect error(String description);


      Builder memory(MemoryProvider provider);
    }

    interface OnSuccessBuilder {

      /**
       * Reply with the response from the model.
       * @return A message reply.
       */
      Agent.StreamEffect thenReply();

      /**
       * Reply with the response from the model.
       * @param metadata The metadata for the message.
       * @return A message reply.
       */
      Agent.StreamEffect thenReply(Metadata metadata);

    }

  }

}
