/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.Metadata;
import java.util.concurrent.CompletionStage;

/**
 * Zero argument component call representation, not executed until invoked. Used for component
 * methods that cannot be deferred.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <R> The type of value returned by executing the call
 */
@DoNotInherit
public interface ComponentInvokeOnlyMethodRef<R> {
  ComponentMethodRef<R> withMetadata(Metadata metadata);

  CompletionStage<R> invokeAsync();

  R invoke();
}
