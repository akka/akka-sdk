/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk;

/**
 * Represents a call to another component, performed as a forward, a side effect, or a
 * request-reply.
 *
 * <p>Not for user extension.
 *
 * @param <I> The type of message the call accepts
 * @param <O> The type of message the call returns
 */
public interface DeferredCall<I, O> {

  /** The message to pass to the call when the call is invoked. */
  I message();

  /** @return The metadata to pass with the message when the call is invoked. */
  Metadata metadata();

  /** @return DeferredCall with updated metadata */
  DeferredCall<I, O> withMetadata(Metadata metadata);
}
