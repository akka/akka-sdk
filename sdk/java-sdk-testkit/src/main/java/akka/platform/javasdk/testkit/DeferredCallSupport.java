/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

 package akka.platform.javasdk.testkit;
 
import com.google.protobuf.any.Any;
import akka.platform.javasdk.DeferredCall;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.time.temporal.ChronoUnit.SECONDS;

public class DeferredCallSupport {


  static private final Duration defaultTimeout = Duration.of(10, SECONDS);

  static public  <I, O> O invokeAndAwait(DeferredCall<I, O> deferredCall) {
    return invokeAndAwait(deferredCall, defaultTimeout);
  }

  static public  <I, O> O invokeAndAwait(DeferredCall<I, O> deferredCall, Duration timeout) {
    try {
      return deferredCall.invokeAsync().toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Invoke the deferred call expecting it to fail.
   * If completed with an exception, returns the exception. If completed successfully, fail with runtime exception.
   */
  static public <O>  Exception failed(DeferredCall<?, O> deferredCall) {
    try {
      deferredCall.invokeAsync().toCompletableFuture().get(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
      throw new RuntimeException("Expected call to fail but it succeeded");
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      return e;
    }
  }

}