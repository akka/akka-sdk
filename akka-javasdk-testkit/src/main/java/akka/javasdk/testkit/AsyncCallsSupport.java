/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static java.time.temporal.ChronoUnit.SECONDS;

import akka.javasdk.impl.ErrorHandling;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AsyncCallsSupport {

  private final Duration defaultTimeout = Duration.of(10, SECONDS);

  public <I, O> O await(CompletionStage<O> stage) {
    return await(stage, defaultTimeout);
  }

  public <I, O> O await(CompletionStage<O> stage, Duration timeout) {
    try {
      return stage.toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      throw ErrorHandling.unwrapExecutionException(e);
    } catch (InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * If completed with an exception, returns the exception. If completed successfully, fail with
   * runtime exception.
   */
  public <O> Exception failed(CompletionStage<O> stage) {
    try {
      stage.toCompletableFuture().get(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
      throw new RuntimeException("Expected call to fail but it succeeded");
    } catch (ExecutionException e) {
      return ErrorHandling.unwrapExecutionException(e);
    } catch (InterruptedException | TimeoutException e) {
      return e;
    }
  }
}
