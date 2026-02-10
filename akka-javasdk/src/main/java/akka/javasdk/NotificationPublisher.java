/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.Done;
import akka.NotUsed;
import akka.annotation.ApiMayChange;
import akka.japi.Pair;
import akka.japi.function.Function;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * A publisher for sending notifications to external subscribers. Notifications can be used to
 * stream progress updates, status changes, or any other messages to clients. Currently, only
 * supported in the Workflow component.
 *
 * <p>To use notifications in a workflow:
 *
 * <ol>
 *   <li>Inject {@code NotificationPublisher<T>} in the workflow constructor
 *   <li>Call {@link #publish(Object)} to send notifications during workflow steps
 *   <li>Expose a method returning {@link #stream()} for clients to subscribe
 * </ol>
 *
 * <p>Example of subscribing to notifications from a client:
 *
 * <pre>{@code
 * componentClient.forWorkflow(workflowId)
 *     .notificationStream(MyWorkflow::updates)
 *     .source()
 *     .runForeach(notification -> System.out.println("Received: " + notification), materializer);
 * }</pre>
 *
 * @param <T> the type of notification messages
 */
@ApiMayChange
public interface NotificationPublisher<T> {

  /**
   * A helper interface allowing a type safe subscription to a notification stream. Not intended for
   * the actual implementation.
   */
  interface NotificationStream<T> {}

  /**
   * Publishes a single notification message to all subscribers.
   *
   * @param msg the notification message to publish
   */
  void publish(T msg);

  /**
   * Returns an Akka Streams {@link Sink} that publishes each element it receives as a notification.
   * Useful for integrating with stream-based source of notification.
   *
   * @return a sink that publishes notifications
   */
  default Sink<T, CompletionStage<Done>> publishSink() {
    return Sink.foreach(this::publish);
  }

  /**
   * Publishes a stream of string tokens (typically from an LLM response) as batched notifications.
   * Tokens are grouped by count and time window, then published as notifications using the provided
   * mapping function. This is useful for streaming AI/LLM responses to clients in real-time.
   *
   * <p>Example usage for streaming LLM tokens:
   *
   * <pre>{@code
   * Source<String, NotUsed> tokenSource = componentClient.forAgent()
   *     .tokenStream(MyAgent::generate)
   *     .source(request);
   *
   * notificationPublisher.publish(new MyNotification.ResponseStart());
   * String fullResponse = notificationPublisher.publishTokenStream(
   *     tokenSource,
   *     10,                                    // batch up to 10 tokens
   *     Duration.ofMillis(200),                // or every 200ms
   *     MyNotification.ResponseDelta::new,     // wrap batched text in notification
   *     materializer);
   * notificationPublisher.publish(new MyNotification.ResponseEnd());
   * }</pre>
   *
   * @param tokenSource the source of string tokens to publish
   * @param groupingMaxNumber maximum number of tokens to batch together
   * @param groupingDuration maximum time to wait before publishing a batch
   * @param partialMapping function to convert batched token strings into notification messages
   * @param materializer the Akka Streams materializer
   * @return the complete concatenated string of all tokens
   */
  default String publishTokenStream(
      Source<String, NotUsed> tokenSource,
      int groupingMaxNumber,
      Duration groupingDuration,
      Function<String, T> partialMapping,
      Materializer materializer) {
    return tokenSource
        .groupedWithin(groupingMaxNumber, groupingDuration)
        .map(grouped -> String.join("", grouped))
        .map(partial -> new Pair<>(partial, partialMapping.apply(partial)))
        .map(
            pair -> {
              publish(pair.second()); // publish grouped tokens
              return pair.first();
            })
        .runReduce((s1, s2) -> s1 + s2, materializer) // join token to get final result
        .toCompletableFuture()
        .join();
  }

  /**
   * Returns a {@link NotificationStream} handle that can be exposed via a Workflow method for
   * external clients to subscribe to notifications.
   *
   * <p>Example:
   *
   * <pre>{@code
   * public NotificationStream<MyNotification> updates() {
   *   return notificationPublisher.stream();
   * }
   * }</pre>
   *
   * @return a notification stream handle for client subscription
   */
  default NotificationStream<T> stream() {
    return null;
  }
}
