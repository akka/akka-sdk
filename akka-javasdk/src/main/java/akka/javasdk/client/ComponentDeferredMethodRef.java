/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.javasdk.DeferredCall;
import akka.javasdk.Metadata;

/**
 * Zero argument component deferred call representation, not executed until invoked by some
 * mechanism using the deferred call (like a timer executing it later for example)
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <R> The type of value returned by executing the call
 */
@DoNotInherit
public interface ComponentDeferredMethodRef<R> {
  ComponentDeferredMethodRef<R> withMetadata(Metadata metadata);

  DeferredCall<NotUsed, R> deferred();
}
