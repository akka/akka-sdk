/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.Metadata;
import akka.pattern.RetrySettings;

import java.util.concurrent.CompletionStage;

/**
 * Zero or one argument component call representation, not executed until invoked or by some
 * mechanism using the deferred call (like a timer executing it later for example)
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <A1> the argument type of the call
 * @param <R> The type of value returned by executing the call
 */
@DoNotInherit
public interface ComponentMethodRefAnyArity<A1, R> {

  ComponentMethodRefAnyArity<A1, R> withMetadata(Metadata metadata);

  /**
   * Set the retry settings for this call.
   *
   * @param retrySettings The retry settings
   * @return A new call with the retry settings set
   */
  ComponentMethodRefAnyArity<A1, R> withRetry(RetrySettings retrySettings);

  /**
   * Set the retry settings for this call. A predefined backoff strategy will be calculated based on
   * the number of maxRetries.
   *
   * @param maxRetries The number of retries to make
   * @return A new call with the retry settings set
   */
  ComponentMethodRefAnyArity<A1, R> withRetry(int maxRetries);

  CompletionStage<R> invokeAsync(A1 arg);

  R invoke(A1 arg);

  CompletionStage<R> invokeAsync();

  R invoke();
}
