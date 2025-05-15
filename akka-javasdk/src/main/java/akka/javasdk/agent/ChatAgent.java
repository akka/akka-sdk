/*
 * Copyright (C) 2025 Lightbend Inc. <https://www.lightbend.com>
 */
package akka.javasdk.agent;

import akka.javasdk.Metadata;
import scala.NotImplementedError;

import java.util.function.Function;

public abstract class ChatAgent {

  public final Effect.Builder effects() {
    throw new NotImplementedError(); // FIXME
  }

  /**
   * An Effect is a description of what the runtime needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to the runtime, which will process
   * the instructions on your behalf.
   * <p>
   * Each component defines its own effects, which are a set of predefined
   * operations that match the capabilities of that component.
   * <p>
   * A ChatAgent Effect can:
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

      Builder systemMessage(String message);

      OnSuccessBuilder<String> userMessage(String message);

      <R> OnSuccessBuilder<R> userMessage(String message, Class<R> modelResponseType);

    }

    /**
     * @param <R> The type of the message that is returned by the model.
     */
    interface OnSuccessBuilder<R> {

      /**
       * Reply with the same response from the model.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> ChatAgent.Effect<T> thenReply();

      /**
       * Reply after for example {@code updateState}.
       *
       * @param replyMessage Function that transforms the response from the model to a reply message.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> ChatAgent.Effect<T> thenReply(Function<R, T> replyMessage);

      /**
       * Reply after for example {@code updateState}.
       *
       * @param replyMessage Function that transforms the response from the model to a reply message.
       * @param metadata The metadata for the message.
       * @param <T> The type of the message that must be returned by this call.
       * @return A message reply.
       */
      <T> ChatAgent.Effect<T> thenReply(Function<R, T> replyMessage, Metadata metadata);

    }

  }
}
