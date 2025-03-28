/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.pattern.RetrySettings;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface AsyncUtils {
  /**
   * Retry a given call with a maximum number of retries. A predifined backoff strategy will be calculated based on the number of maxRetries.
   * @param call The call to retry
   * @param maxRetries The number of retries to make
   */
  <T> CompletionStage<T> retry(Callable<CompletionStage<T>> call, int maxRetries);

  /**
   * Retry a given call.
   *
   * @param call The call to retry
   * @param retrySettings The retry settings
   */
  <T> CompletionStage<T> retry(Callable<CompletionStage<T>> call, RetrySettings retrySettings);

  <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futureToolResults);
}
