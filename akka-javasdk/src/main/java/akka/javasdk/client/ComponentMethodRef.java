/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.Metadata;
import akka.pattern.RetrySettings;

import java.util.concurrent.CompletionStage;

/**
 * Zero argument component call representation, not executed until invoked or by some mechanism
 * using the deferred call (like a timer executing it later for example)
 *
 * @param <R> The type of value returned by executing the call
 *            <p>Not for user extension or instantiation, returned by the SDK component client
 */
@DoNotInherit
public interface ComponentMethodRef<R> extends ComponentDeferredMethodRef<R> {

  ComponentMethodRef<R> withMetadata(Metadata metadata);

  /**
   * Set the retry settings for this call.
   *
   * @param retrySettings The retry settings
   * @return A new call with the retry settings set
   */
  ComponentInvokeOnlyMethodRef<R> withRetry(RetrySettings retrySettings);

  /**
   * Set the retry settings for this call. A predefined backoff strategy will be calculated based on the number of maxRetries.
   *
   * @param maxRetries The number of retries to make
   * @return A new call with the retry settings set
   */
  ComponentInvokeOnlyMethodRef<R> withRetry(int maxRetries);

  CompletionStage<R> invokeAsync();
}
