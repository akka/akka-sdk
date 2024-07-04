/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk;

import akka.platform.javasdk.action.Action;

import java.util.concurrent.CompletionStage;

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

  /**
   * Execute this call right away and get the async result back for composition. Can be used to
   * create an async reply in an {@link Action} with {@code effects().asyncReply} and {@code
   * effects().asyncEffect}
   */
  CompletionStage<O> invokeAsync();

  /** @return DeferredCall with updated metadata */
  DeferredCall<I, O> withMetadata(Metadata metadata);
}
