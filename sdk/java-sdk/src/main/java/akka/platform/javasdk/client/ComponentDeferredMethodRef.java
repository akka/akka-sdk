/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.client;

import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.platform.javasdk.DeferredCall;
import akka.platform.javasdk.Metadata;

/**
 * Zero argument component deferred call representation, not executed until invoked by some
 * mechanism using the deferred call (like a timer executing it later for example)
 *
 * @param <R> The type of value returned by executing the call
 *     <p>Not for user extension or instantiation, returned by the SDK component client
 */
@DoNotInherit
public interface ComponentDeferredMethodRef<R> {
  ComponentDeferredMethodRef<R> withMetadata(Metadata metadata);

  DeferredCall<NotUsed, R> deferred();
}
