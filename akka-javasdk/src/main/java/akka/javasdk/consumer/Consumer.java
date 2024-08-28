/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.consumer;

import akka.Done;
import akka.javasdk.Metadata;
import akka.javasdk.impl.consumer.ConsumerEffectImpl;
import akka.javasdk.impl.consumer.MessageContextImpl;
import akka.javasdk.timer.TimerScheduler;

import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 *
 * Consumers are stateless components that can be used to implement different uses cases, such as:
 *
 * <p>
 * <ul>
 *   <li>subscribe to events from an Event Sourced Entity.
 *   <li>subscribe to state changes from a Key Value Entity.
 * </ul>
 *
 * A Consumer method should return an {@link Effect} that describes what to do next.
 */
public abstract class Consumer {

  private volatile Optional<MessageContext> messageContext = Optional.empty();

  /**
   * Additional context and metadata for a message handler.
   *
   * <p>It will throw an exception if accessed from constructor.
   */
  protected final MessageContext messageContext() {
    return messageContext("MessageContext is only available when handling a message.");
  }

  private MessageContext messageContext(String errorMessage) {
    return messageContext.orElseThrow(() -> new IllegalStateException(errorMessage));
  }

  /**
   * INTERNAL API
   */
  public void _internalSetMessageContext(Optional<MessageContext> context) {
    messageContext = context;
  }

  public final Effect.Builder effects() {
    return ConsumerEffectImpl.builder();
  }

  /**
   * Returns a {@link TimerScheduler} that can be used to schedule further in time.
   */
  public final TimerScheduler timers() {
    MessageContextImpl impl =
      (MessageContextImpl)
        messageContext("Timers can only be scheduled or cancelled when handling a message.");
    return impl.timers();
  }

  /**
   * An Effect is a description of what Kalix needs to do after the command is handled.
   * You can think of it as a set of instructions you are passing to Kalix. Kalix will process the instructions on your
   * behalf.
   * <p>
   * Each Kalix component defines its own effects, which are a set of predefined
   * operations that match the capabilities of that component.
   * <p>
   * A Consumer Effect can either:
   * <p>
   * <ul>
   *   <li>reply with a message to be published to a Topic (in case the method is a publisher)
   *   <li>reply with a Done message to indicate that the command was processed successfully
   *   <li>forward the message to another component
   *   <li>ignore the call
   * </ul>
   *
   */
  public interface Effect {

    /**
     * Construct the effect that is returned by the message handler.
     */
    interface Builder {

      /**
       * Mark message as processed.
       */
      Effect done();

      /**
       * Mark message as processed from an async operation result
       */
      Effect acyncDone(CompletionStage<Done> message);

      /**
       * Produce a message.
       *
       * @param message The payload of the message.
       * @param <S>     The type of the message.
       */
      <S> Effect produce(S message);

      /**
       * Produce a message with custom Metadata.
       *
       * @param message  The payload of the message.
       * @param metadata The metadata for the message.
       */
      <S> Effect produce(S message, Metadata metadata);

      /**
       * Produce a message from an async operation result.
       *
       * @param message The future payload of the message.
       */
      <S> Effect asyncProduce(CompletionStage<S> message);

      /**
       * Produce a message from an async operation result with custom Metadata.
       *
       * @param message The future payload of the message.
       * @param metadata The metadata for the message.
       */
      <S> Effect asyncProduce(CompletionStage<S> message, Metadata metadata);

      /**
       * Create an async operation result returning an effect.
       *
       * @param futureEffect The future effect.
       */
      Effect asyncEffect(CompletionStage<Effect> futureEffect);

      /**
       * Ignore the current message and proceed with processing the next message
       */
      Effect ignore();
    }
  }
}
