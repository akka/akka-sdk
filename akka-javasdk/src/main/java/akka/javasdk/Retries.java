/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.pattern.RetrySettings;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

public interface Retries {

  /**
   * Retry a given call with a maximum number of retries. A predefined backoff strategy will be
   * calculated based on the number of maxRetries.
   *
   * @param call The call to retry
   * @param maxRetries The number of retries to make
   */
  <T> CompletionStage<T> retryAsync(Callable<CompletionStage<T>> call, int maxRetries);

  /**
   * Retry a given call.
   *
   * @param call The call to retry
   * @param retrySettings The retry settings
   */
  <T> CompletionStage<T> retryAsync(Callable<CompletionStage<T>> call, RetrySettings retrySettings);

  /**
   * Retry a given call with a maximum number of retries. A predefined backoff strategy will be
   * calculated based on the number of maxRetries.
   *
   * @param call The call to retry
   * @param maxRetries The number of retries to make
   */
  <T> T retry(Callable<T> call, int maxRetries);

  /**
   * Retry a given call.
   *
   * @param call The call to retry
   * @param retrySettings The retry settings
   */
  <T> T retry(Callable<T> call, RetrySettings retrySettings);
}
