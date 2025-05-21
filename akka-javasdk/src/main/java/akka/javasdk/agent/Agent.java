/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.annotation.InternalApi;
import akka.javasdk.Metadata;
import akka.javasdk.impl.agent.AgentEffectImpl;

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
    return new AgentEffectImpl<>();
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
       * Provides system-level instructions to the AI model that define its behavior and context.
       * The system message acts as a foundational prompt that establishes the AI's role, constraints,
       * and operational parameters. It is processed before user messages and helps maintain consistent
       * behavior throughout the interaction.
       */
      Builder systemMessage(String message);

      Builder systemMessageFromTemplate(String templateId);

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
    }

    interface MappingResponseBuilder<Input> {

      Agent.Effect<Input> thenReply();

      Agent.Effect<Input> thenReply(Metadata metadata);

      <T> MappingFailureBuilder<T> map(Function<Input, T> mapper);
    }

    interface MappingFailureBuilder<Input> {

      Agent.Effect<Input> thenReply();

      Agent.Effect<Input> thenReply(Metadata metadata);

      Agent.Effect<Input> onFailure(Function<Throwable, Input> exceptionHandler);
    }

  }

}
