/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.Metadata;

import java.util.concurrent.CompletionStage;

/**
 * Zero argument component call representation, not executed until invoked or by some mechanism
 * using the deferred call (like a timer executing it later for example)
 *
 * @param <R> The type of value returned by executing the call
 *     <p>Not for user extension or instantiation, returned by the SDK component client
 */
@DoNotInherit
public interface ComponentMethodRef<R> extends ComponentDeferredMethodRef<R> {

  ComponentMethodRef<R> withMetadata(Metadata metadata);

  CompletionStage<R> invokeAsync();

  R invoke();
}
