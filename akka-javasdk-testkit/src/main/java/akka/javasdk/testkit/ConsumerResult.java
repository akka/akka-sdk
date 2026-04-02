/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.javasdk.Metadata;

/**
 * Represents the result of a Consumer handling a message when run through the testkit.
 *
 * <p>Not for user extension, returned by the testkit.
 *
 * <p>Async effects are automatically resolved before results are returned.
 */
public interface ConsumerResult {

  /**
   * @return true if the effect was consumed (resulting from {@code done()} or {@code ignore()}),
   *     false otherwise
   */
  boolean isConsumed();

  /**
   * @return true if the effect produced a message, false otherwise
   */
  boolean isProduced();

  /**
   * @return The produced message payload, or throws if the effect was not a produce effect
   */
  Object getProducedMessage();

  /**
   * Returns the produced message payload, verifying its type.
   *
   * @param <T> the expected message type
   * @param messageClass the expected message class
   * @return the produced message cast to the expected type
   * @throws IllegalStateException if the effect was not a produce effect or the message type does
   *     not match
   */
  <T> T getProducedMessage(Class<T> messageClass);

  /**
   * @return true if the produce effect includes metadata, false otherwise. Throws if the effect was
   *     not a produce effect.
   */
  boolean hasMetadata();

  /**
   * @return The metadata from the produce effect, or throws if the effect was not a produce effect
   *     or has no metadata
   */
  Metadata getMetadata();
}
