/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.consumer;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.Metadata;
import akka.javasdk.impl.consumer.ConsumerEffectImpl;
import akka.javasdk.impl.consumer.MessageContextImpl;
import akka.javasdk.timer.TimerScheduler;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Consumers are stateless components that enable stream-based interaction between Akka services and
 * other systems. They can be used to implement various use cases including:
 *
 * <ul>
 *   <li>Consume events from an Event Sourced Entity within the same service
 *   <li>Consume state changes from a Key Value Entity within the same service
 *   <li>Consume state changes from a Workflow within the same service
 *   <li>Consume events or state from an Entity or Workflow in another service using
 *       service-to-service eventing
 *   <li>Consume messages from Google Cloud Pub/Sub or Apache Kafka topics
 *   <li>Produce messages to Google Cloud Pub/Sub or Apache Kafka topics
 * </ul>
 *
 * <p>Events and messages are guaranteed to be delivered at least once, which means Consumers must
 * be able to handle duplicated messages.
 *
 * <p>A Consumer method should return an {@link Effect} that describes what to do next. The Effect
 * API defines the operations that Akka should perform when an incoming message is delivered to the
 * Consumer.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * @ComponentId("counter-events-consumer")
 * @Consume.FromEventSourcedEntity(CounterEntity.class)
 * public class CounterEventsConsumer extends Consumer {
 *
 *   public Effect onEvent(CounterEvent event) {
 *     return switch (event) {
 *       case ValueIncreased valueIncreased ->
 * //processing the event
 * effects().done();
 *       case ValueMultiplied valueMultiplied -> effects().ignore();
 *     };
 *   }
 * }
 * }</pre>
 *
 * <p>Concrete classes can accept the following types to the constructor:
 *
 * <ul>
 *   <li>{@link akka.javasdk.client.ComponentClient}
 *   <li>{@link akka.javasdk.http.HttpClientProvider}
 *   <li>{@link akka.javasdk.timer.TimerScheduler}
 *   <li>{@link akka.stream.Materializer}
 *   <li>{@link com.typesafe.config.Config}
 *   <li>Custom types provided by a {@link akka.javasdk.DependencyProvider} from the service setup
 * </ul>
 *
 * <p>Concrete classes must be annotated with {@link akka.javasdk.annotations.ComponentId} and one
 * of the {@link akka.javasdk.annotations.Consume} annotations such as:
 *
 * <ul>
 *   <li>{@code @Consume.FromEventSourcedEntity} - to consume events from an Event Sourced Entity
 *   <li>{@code @Consume.FromKeyValueEntity} - to consume state changes from a Key Value Entity
 *   <li>{@code @Consume.FromWorkflow} - to consume state changes from a Workflow
 *   <li>{@code @Consume.FromTopic} - to consume messages from a message broker topic
 *   <li>{@code @Consume.FromServiceStream} - to consume events from another Akka service
 * </ul>
 *
 * <p>For producing messages, use {@code @Produce.ToTopic} or {@code @Produce.ServiceStream}
 * annotations.
 *
 * <p>If an exception is raised during message processing, the Akka runtime will redeliver the
 * message until the application processes it without failures.
 */
public abstract class Consumer {

  private volatile Optional<MessageContext> messageContext = Optional.empty();

  /**
   * Additional context and metadata for a message handler.
   *
   * <p>The message context provides access to:
   *
   * <ul>
   *   <li>Message metadata including CloudEvent attributes like subject id via {@code
   *       messageContext().metadata().get("ce-subject")}
   *   <li>CloudEvent interface for accessing standard CloudEvent properties
   *   <li>Information about message origin and region for multi-region deployments
   * </ul>
   *
   * <p>For Event Sourced and Key Value Entity consumers, the entity id is available through the
   * metadata as the CloudEvent subject: {@code
   * messageContext().metadata().asCloudEvent().subject().get()}
   *
   * <p>This method will throw an exception if accessed from the constructor. It is only available
   * when handling a message.
   *
   * @return the message context containing metadata and additional information about the current
   *     message
   * @throws IllegalStateException if called outside of message handling (e.g., from constructor)
   */
  protected final MessageContext messageContext() {
    return messageContext("MessageContext is only available when handling a message.");
  }

  private MessageContext messageContext(String errorMessage) {
    return messageContext.orElseThrow(() -> new IllegalStateException(errorMessage));
  }

  /**
   * INTERNAL API
   *
   * @hidden
   */
  @InternalApi
  public void _internalSetMessageContext(Optional<MessageContext> context) {
    messageContext = context;
  }

  /**
   * Returns the Effect Builder for constructing effects that describe what the runtime should do
   * after message processing.
   *
   * <p>Use this to create effects such as:
   *
   * <ul>
   *   <li>{@code effects().done()} - to mark the message as successfully processed
   *   <li>{@code effects().produce(message)} - to produce a message to a topic or stream
   *   <li>{@code effects().ignore()} - to ignore the current message and continue processing
   * </ul>
   *
   * @return the Effect Builder for creating Consumer effects
   */
  public final Effect.Builder effects() {
    return ConsumerEffectImpl.builder();
  }

  /**
   * Returns a {@link TimerScheduler} that can be used to schedule actions to be executed at a later
   * time.
   *
   * <p>Timers can be used to schedule delayed processing, implement timeouts, or trigger periodic
   * actions. The scheduled actions will be delivered as messages to the Consumer.
   *
   * <p>This method can only be called when handling a message, not from the constructor.
   *
   * @return the TimerScheduler for scheduling delayed actions
   * @throws IllegalStateException if called outside of message handling (e.g., from constructor)
   */
  public final TimerScheduler timers() {
    MessageContextImpl impl =
        (MessageContextImpl)
            messageContext("Timers can only be scheduled or cancelled when handling a message.");
    return impl.timers();
  }

  /**
   * An Effect is a description of what the runtime needs to do after a message is handled. You can
   * think of it as a set of instructions you are passing to the runtime, which will process the
   * instructions on your behalf.
   *
   * <p>Each component defines its own effects, which are a set of predefined operations that match
   * the capabilities of that component.
   *
   * <p>A Consumer Effect can either:
   *
   * <ul>
   *   <li>return a message to be published to a Topic (when the Consumer is also a producer)
   *   <li>return Done to indicate that the message was processed successfully
   *   <li>ignore the message and continue processing the next message
   * </ul>
   *
   * <p>For more details, refer to the Declarative Effects documentation.
   */
  public interface Effect {

    /**
     * Builder for constructing Consumer effects that describe what the runtime should do after
     * message processing.
     */
    interface Builder {

      /**
       * Mark the message as successfully processed.
       *
       * <p>Use this when the message has been processed and no further action is needed.
       *
       * @return an Effect indicating successful message processing
       */
      Effect done();

      /**
       * Mark the message as processed from an async operation result.
       *
       * <p>Use this when message processing involves asynchronous operations that complete with a
       * Done result.
       *
       * @param message the future result of the async operation
       * @return an Effect that will complete when the async operation completes
       */
      Effect asyncDone(CompletionStage<Done> message);

      /**
       * Produce a message to a topic or service stream.
       *
       * <p>This is used when the Consumer is also configured as a producer with
       * {@code @Produce.ToTopic} or {@code @Produce.ServiceStream} annotations. The message will be
       * published using CloudEvents format.
       *
       * @param message the payload of the message to produce
       * @param <S> the type of the message
       * @return an Effect that will produce the message
       */
      <S> Effect produce(S message);

      /**
       * Produce a message with custom metadata to a topic or service stream.
       *
       * <p>Use this when you need to include additional metadata with the produced message, such as
       * CloudEvent attributes or custom headers. To guarantee message ordering, include the entity
       * id as the subject in the metadata.
       *
       * @param message the payload of the message to produce
       * @param metadata the metadata to include with the message
       * @param <S> the type of the message
       * @return an Effect that will produce the message with the specified metadata
       */
      <S> Effect produce(S message, Metadata metadata);

      /**
       * Produce a message from an async operation result.
       *
       * <p>Use this when the message to be produced is the result of an asynchronous operation.
       *
       * @param message the future payload of the message to produce
       * @param <S> the type of the message
       * @return an Effect that will produce the message when the async operation completes
       */
      <S> Effect asyncProduce(CompletionStage<S> message);

      /**
       * Produce a message from an async operation result with custom metadata.
       *
       * <p>Combines async message production with custom metadata.
       *
       * @param message the future payload of the message to produce
       * @param metadata the metadata to include with the message
       * @param <S> the type of the message
       * @return an Effect that will produce the message with metadata when the async operation
       *     completes
       */
      <S> Effect asyncProduce(CompletionStage<S> message, Metadata metadata);

      /**
       * Create an effect from an async operation that returns an effect.
       *
       * <p>Use this when the effect to be applied depends on the result of an asynchronous
       * operation.
       *
       * @param futureEffect the future effect to apply
       * @return an Effect that will apply the future effect when it completes
       */
      Effect asyncEffect(CompletionStage<Effect> futureEffect);

      /**
       * Ignore the current message and proceed with processing the next message.
       *
       * <p>Use this to filter out messages that should not be processed, such as events that are
       * not relevant to the Consumer's logic. The message will be acknowledged but no further
       * processing occurs.
       *
       * @return an Effect that ignores the current message
       */
      Effect ignore();
    }
  }
}
