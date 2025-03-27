/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface AsyncUtils {

  <T> CompletionStage<T> retry(Callable<CompletionStage<T>> call, int attempts);

  <T> CompletionStage<T> retry(Callable<CompletionStage<T>> call, RetrySettings retrySettings);

  <T> CompletableFuture<List<T>> sequence(List<CompletableFuture<T>> futureToolResults);
}
